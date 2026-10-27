# Everheard — Architecture and Design

> This document is the spec the plan argues from. When a decision changes, update §14 and the corresponding section here; the plan's task list follows this document, not the other way round.

## 1. Principles

1. **Local-first.** The phone is the system of record. No backend, no account.
2. **Text-only by default.** Audio is a temporary artifact; it is deleted after transcription.
3. **Model-agnostic.** ASR, LLM, and embeddings sit behind interfaces; the user can point them at any OpenAI-compatible endpoint or keep them on-device.
4. **Visible capture.** Persistent notification, live screen, start tone. No covert mode.
5. **Correctable attribution.** Speaker labels and "this is mine" are visible, editable, and teachable. Never silently delete a segment because confidence is low.
6. **Bitemporal memory.** When a fact changes (a deadline moves), close the old edge and open a new one; do not overwrite history.
7. **Evidence or nothing.** Every decision, commitment, risk, and insight carries a quote, timestamp, and speaker. No evidence → drop it.

## 2. Package layout

Single Gradle module for v0.1 (decision D1). Packages, not modules:

```
app/src/main/java/io/github/markyu0401/everheard/
  EverheardApp.kt
  MainActivity.kt
  di/                      Hilt modules
  data/
    db/                    Room entities, DAOs, migrations, SQLCipher factory
    crypto/                Keystore-wrapped key, encrypted prefs
    settings/              DataStore: schedule, providers, retention
    repo/                  SessionRepository, PersonRepository, ProjectRepository
  capture/
    CaptureService.kt      foreground service, notification, lifecycle
    CaptureStateMachine.kt states + events
    ScheduleEvaluator.kt   allow/block windows
    guards/                BatteryGuard, ThermalGuard, SilenceGuard
    audio/                 AudioRecordSource, RingBuffer, SegmentWriter, OpusEncoder
    vad/                   SileroVad
  asr/
    AsrEngine.kt           interface
    local/                 SherpaAsr, ModelManager
    cloud/                 OpenAiCompatibleAsr
    merge/                 TwoPassMerger
    text/                  Punctuation, Hotwords, Normalizer
  speaker/
    Enrollment.kt
    SpeakerEmbedder.kt
    OnlineCluster.kt
    Relevance.kt
    NoiseFeedback.kt
  memory/
    model/                 Session, Segment, Utterance, Person, Commitment, Project, Insight
    graph/                 EntityGraph, BitemporalStore
    project/               ProjectClusterer
    search/                FtsSearch, VectorIndex
  llm/
    LlmEngine.kt           interface
    OpenAiCompatibleLlm.kt
    schema/                SummarySchema, InsightSchema, Validator
    prompt/                SummaryPrompt, InsightPrompt, WeeklyPrompt
  mcp/
    McpServer.kt           HTTP + bearer token
    Tools.kt               search_memory, list_commitments, get_project, get_session
  ui/
    today/ live/ detail/ projects/ insight/ person/ settings/ onboarding/
    theme/                 colors, type, shapes
  util/
```

Rules: a file owns one responsibility; UI never touches Room directly; providers never see UI models.

## 3. Capture pipeline

### 3.1 Audio source

```kotlin
AudioRecord(
    MediaRecorder.AudioSource.VOICE_RECOGNITION, // no AGC/NS; cleaner for ASR
    16_000,
    AudioFormat.CHANNEL_IN_MONO,
    AudioFormat.ENCODING_PCM_16BIT,
    minBufferSize * 4
)
```

- Frame = 20 ms = 320 samples = 640 bytes.
- Ring buffer = 60 s = 1,920,000 bytes (~1.9 MB) in RAM.
- Segment = 5–30 s, split on VAD endpoint; 200 ms overlap to avoid cutting words.
- Optional retained audio: Opus 16 kbps, encrypted, deleted after transcription by default; a 24 h retention setting exists for corrections.
- Bluetooth: route with `setCommunicationDevice()` (API 31+); SCO/LE audio is 8/16 kHz and recognizably worse — prefer the phone mic when both exist.
- Online meetings: `MediaProjection` internal audio capture, per-session consent, Android 14+ single-app option.

### 3.2 VAD and segmentation

- Silero VAD v5 via sherpa-onnx; threshold 0.5; min speech 250 ms; hangover 500 ms.
- Keep a **500 ms pre-roll** of audio before the VAD fires; the segment starts there, so a late VAD decision does not clip the first syllable.
- Endpoint on ~600 ms silence; hard cap 30 s; emit `SegmentPcm(startMs, endMs, samples)`.
- The streaming recognizer has endpointing disabled; VAD owns segmentation, so ASR cannot cut a segment a second time.
- Adjacent utterances with a gap ≤ 1.2 s are merged into one transcript row; this keeps poems and breath-paused sentences coherent.
- Speech ratio is logged per hour (not content) to drive the battery report.

### 3.3 Storage during capture

- In M2, VAD segments are held in memory only; the final text is written to the AES-256-GCM transcript document. No audio temp file is created.
- The M4 design keeps encrypted segment files for crash recovery; that path is not implemented yet.

### 3.4 Memory layer (M4 slice)

- `memory.bin` is a second AES-256-GCM document with its own Keystore alias; its versioned JSON holds one `SessionMemory` per session.
- The current analyzer is deterministic (`rule-v1`): progress / decision / commitment / risk / inspiration patterns plus marked lines. Every item carries the exact quote, utterance id, timestamp, and speaker; an item without evidence is dropped.
- The summary is extractive (first two utterances), not a generated claim. This is a baseline, not the LLM path; LLM JSON schema + validator, Room/SQLCipher, bitemporal commitments, calendar bridge, and project clustering remain planned.
- Home shows up to three semantic highlights for today; session detail shows the summary, full timeline, commitments, and ideas above the transcript. Session delete and clear-all delete the corresponding memory.

## 4. Service and state machine

### 4.1 States

| State | Mic | Notification | Entered by |
|---|---|---|---|
| `STOPPED` | closed | none | user stop; fatal error |
| `ARMED` | closed | "Armed — tap to start now" | service start; schedule block; battery pause |
| `RECORDING` | open | "Recording — transcript only" | schedule allow; user start |
| `PAUSED_SCHEDULE` | closed | "Paused until 07:00" | block window |
| `PAUSED_BATTERY` | closed | "Paused — battery 12 %" | battery < 15 % |
| `PAUSED_SILENCE` | closed | "Paused — no speech for 15 min" | silence + stationary |
| `PAUSED_USER` | closed | "Paused for 1 h" | notification action |
| `ERROR` | closed | "Capture stopped — tap to fix" | permission/FGS failure |

Events: `USER_START`, `USER_STOP`, `USER_PAUSE(duration)`, `SCHEDULE_ALLOW`, `SCHEDULE_BLOCK`, `BATTERY_LOW`, `POWER_CONNECTED`, `SILENCE_TIMEOUT`, `MOTION`, `SCREEN_UNLOCK`, `BOOT`, `SERVICE_KILLED`.

Transitions are pure functions, unit-tested without Android. Side effects (open/close `AudioRecord`, update notification) are applied by `CaptureService` after a transition.

### 4.2 Schedule model

```kotlin
data class ScheduleRule(
    val id: Long,
    val days: Set<DayOfWeek>,     // empty = every day
    val startMinute: Int,         // 0..1439, local time
    val endMinute: Int,           // exclusive; may wrap past midnight
    val mode: Mode                // ALLOW or BLOCK
)
```

Evaluation order:

1. If any enabled `BLOCK` rule matches now → `PAUSED_SCHEDULE`.
2. Else if any enabled `ALLOW` rule matches now → `RECORDING`.
3. Else if at least one `ALLOW` rule exists → `PAUSED_SCHEDULE`.
4. Else → `RECORDING` (no allow rules means "always allowed").

Default for a new install: one `BLOCK` rule 23:30–07:00, every day. The UI is a weekly grid; edits take effect on the next tick (30 s), no service restart.

### 4.3 Guards

- **Battery:** pause at < 15 %, resume at > 30 % while charging. Optional "record only while charging".
- **Thermal:** `PowerManager.getCurrentThermalStatus() >= THERMAL_STATUS_MODERATE` → close mic, keep service armed; resume at `LIGHT`.
- **Silence:** 15 min without speech and no significant motion → `PAUSED_SILENCE`; resume on motion (Activity Recognition transition) or screen unlock.
- **Storage:** < 500 MB free → pause and notify; delete completed temp audio first.

### 4.4 Start, reboot, recovery

- Start only from a visible activity or a notification `PendingIntent`. Background `startForegroundService` for `microphone` is refused.
- `BOOT_COMPLETED` receiver **posts a notification** ("Tap to resume armed capture"); it does not start the service.
- `START_STICKY`; if a restart throws `ForegroundServiceStartNotAllowedException`, catch it, post the same notification, and stop.
- After a process kill, restore state from DataStore: last state, last session id, pending segments. Do not claim capture continued while the process was dead.

### 4.5 Notification

- Channel: `capture`, importance low, no sound except the start tone played by the app.
- Content: state, elapsed time, current session title, battery, schedule next change.
- Actions: `Pause 15 min`, `Pause 1 h`, `Pause until tomorrow`, `Mark`, `Stop`.
- Quick-settings tile mirrors start/stop/pause.
- The notification is the legal "visible capture" indicator; it cannot be hidden.

## 5. ASR

### 5.1 Interface

```kotlin
interface AsrEngine {
    val id: String
    suspend fun transcribe(request: AsrRequest): AsrResult
    fun stream(request: StreamRequest): Flow<AsrPartial>
}

data class AsrRequest(
    val pcm16: ShortArray,
    val sampleRate: Int = 16_000,
    val language: String = "zh",
    val hotwords: List<String> = emptyList()
)

data class AsrResult(
    val text: String,
    val segments: List<AsrSegment>,
    val model: String,
    val latencyMs: Long
)
```

### 5.2 Local provider

- sherpa-onnx 2.0.0 AAR (SHA-256 pinned; fallback 1.13.8).
- **Live draft:** streaming Zipformer **small** bilingual zh-en int8 (encoder 41 MB + decoder 3.3 MB + joiner 3.1 MB) for immediate partials. It is not accurate enough to be the durable transcript.
- **Final transcript:** **SenseVoice small int8** (zh/en/ja/ko/yue, 228 MB) decoded per VAD segment. It is offline/non-autoregressive, much more accurate on Mandarin, and supports inverse text normalization. Load lazily on the first speech segment and keep it for the capture session; peak RAM is ~600–700 MB.
- The streaming recognizer has `enableEndpoint=false`; segmentation is owned by Silero VAD + `SpeechSegmenter`, so a VAD segment is never cut again by a second endpoint detector.
- `SpeechSegmenter` keeps a **500 ms pre-roll** so the first syllable is not clipped when the VAD fires late; segment timestamps start at the pre-roll.
- On devices with < 6 GB RAM, fall back to the streaming-only path or cloud ASR; do not load SenseVoice while another large model is resident.
- Model manager: download on first run, verify SHA-256, show license + size, refuse to start if free space < 1 GB.

### 5.3 Cloud provider

- OpenAI-compatible `POST /v1/audio/transcriptions` (multipart WAV/Opus), model name configurable.
- Per-session consent: show host, model, and what will be sent; remember the choice per provider, not per session forever.
- Timeout 60 s; retry once on 5xx; on 401/429 fall back to local and surface the error.

### 5.4 Two-pass merge

1. Live draft from the local streaming model (fast, lower accuracy).
2. Capture path: each VAD segment is decoded with SenseVoice for the durable transcript.
3. Merge by timestamp: final text wins inside its segment; keep the draft's word timings for the UI.
4. If the final decode fails, keep the draft and mark `refined = false`.

### 5.5 Text post-processing

- SenseVoice already emits punctuation and inverse-normalized numbers; keep a light normalizer for dates/times used by extraction.
- Hotword list: people, projects, product names; applied as context bias where supported, and as a post-hoc re-rank (edit distance) otherwise.
- Language: auto by default; SenseVoice covers zh/en/ja/ko/yue. Per-session override.

## 6. Speaker attribution

### 6.1 Enrollment

- **Implemented:** Settings → Voice profile records a **Chinese sample** and an **English sample** separately, each 5–20 s; the UI gates duration, RMS, and speech ratio. The CAM++ zh-en model produces one L2-normalised centroid per language; both are stored in `voice_profile.bin` with AES-256-GCM under a separate Keystore alias. No enrollment audio is written. Capture compares a segment against all centroids and uses the best cosine.
- Re-enrollment replaces the profile; delete is available in Settings. Voiceprint is biometric data used only for attribution, never for authentication.
- The current cosine threshold is **0.72 (provisional)**; the transcriber logs the best score per segment so it can be calibrated on real voice before claiming accuracy.

### 6.2 Online clustering

- **Implemented now:** each VAD segment ≥ 1.5 s is embedded; a segment is labelled `me` when the best cosine across the enrolled language centroids is ≥ 0.72, otherwise it stays unlabelled (unknown). The detail UI maps `me` to the localized “Me”.
- **Planned:** 1.5 s windows with 0.75 s hop for other speakers; cosine clustering at ≥ 0.70; merge at ≥ 0.80; labels `我`, `说话人 A`, `说话人 B`, `背景`; rename maps a cluster to a person.
- Do not claim speaker names without enrollment or calibration.

### 6.3 Relevance scoring

A single phone mic cannot measure distance. Score four weak signals:

| Signal | Weight | How |
|---|---|---|
| Self voiceprint match | 0.45 | cosine to the enrolled embedding |
| Attendee fit | 0.20 | speaker count vs calendar attendees; unknown → neutral |
| Semantic address | 0.20 | second-person pronouns, imperatives, name mentions near the segment |
| Acoustic | 0.15 | loudness + reverb proxy; close talk is louder and drier |

Buckets: `MINE` ≥ 0.70, `OTHER` 0.40–0.69, `BACKGROUND` < 0.40. The UI shows the bucket and a confidence dot; it never hides a segment silently.

### 6.4 Teaching

- Swipe "不是我的事" on a segment → store `(embedding, text features, label=background)`.
- A small logistic regression / centroid classifier downranks similar future segments; retrain nightly.
- Swipe "这是我" on a mislabeled segment → add to the self cluster; update the centroid.

### 6.5 Honest limits

- Do not promise speaker names without enrollment.
- Do not auto-attribute a segment to a person at confidence < 0.8; show the cluster label instead.
- Bystander speech in public places is expected to land in `BACKGROUND`; the user can delete the bucket in one action.

## 7. Memory

### 7.1 Tables

```sql
session(id, started_at, ended_at, title, project_id, state, summary_json, refined)
segment(id, session_id, start_ms, end_ms, speaker_id, text, confidence, is_mark, audio_path, audio_deleted)
utterance(id, segment_id, speaker_id, text, start_ms, end_ms)
person(id, name, is_self, embedding BLOB, created_at)
speaker(id, person_id, label, embedding BLOB, centroid BLOB)
commitment(id, text, owner_person_id, counterpart_person_id, due_at, status,
           evidence_segment_id, confidence, valid_from, valid_to, recorded_at, superseded_by)
project(id, name, embedding BLOB, created_at, merged_into)
project_event(id, project_id, session_id, kind, text, evidence_segment_id, occurred_at)
insight(id, kind, text, evidence_json, period_start, period_end, created_at)
mark(id, session_id, at_ms, note)
setting(key, value)
```

Indexes: `session(started_at)`, `segment(session_id, start_ms)`, `commitment(status, due_at)`, `project_event(project_id, occurred_at)`. FTS5 virtual table on `utterance(text)` with `content=utterance`.

### 7.2 Bitemporal updates

When a deadline or owner changes:

1. Close the old row: `valid_to = now`, `superseded_by = new_id`.
2. Insert a new row with `valid_from = now`, `recorded_at = now`.
3. Never update `text` in place for a changed fact; the history is the point.

Queries default to `valid_to IS NULL` for "current truth"; a history view shows the chain.

### 7.3 Project clustering

- Feature: session summary embedding + entity set (people, orgs, project names, dates).
- Link a session to a project if it shares ≥ 2 entities with the project centroid or cosine > 0.82.
- Otherwise create a candidate project; promote after 2 sessions.
- User merge/split writes a constraint row (`project_merge`, `project_split`) that overrides future clustering.
- Run nightly via WorkManager; never block the UI.

### 7.4 Retrieval

- **Keyword:** FTS5 for names, numbers, dates.
- **Semantic:** embeddings stored as float16 BLOB; brute-force cosine in Kotlin (corpus < 50k utterances; ~50 MB at 512-d float16).
- **Graph:** "who promised what", "what changed on project X", "open commitments for person Y".
- Hybrid: route by query shape; always return `session_id` + `start_ms` for citation.
- Answer generation uses only retrieved rows; if retrieval is empty, say so.

## 8. Generation

### 8.1 Session summary schema

```json
{
  "title": "string",
  "participants": ["我", "李维"],
  "decisions": [{"text": "...", "evidence": "...", "ts": "09:18", "speaker": "我"}],
  "commitments": [{"text": "...", "owner": "李维", "due": "2026-10-30", "evidence": "...", "ts": "09:26", "confidence": 0.9}],
  "risks": [{"text": "...", "evidence": "...", "ts": "09:41", "speaker": "王敏"}],
  "ideas": [],
  "open_questions": []
}
```

Validation rules:

- Drop any item without a non-empty `evidence` that appears in the transcript.
- `due` must parse as ISO date/time or be `null`; never invent a date.
- `confidence < 0.5` → `needs_review = true`, shown with a warning chip.
- `participants` must be a subset of speakers present in the session.

### 8.2 Prompts

- System: "You extract only what the user needs. Attribute every claim to a speaker. Quote the exact sentence. If a date is not stated, use null. Do not infer facts from general knowledge."
- User: transcript with speaker labels and timestamps; the user's name and known aliases; project context if any.
- Output: JSON only; no prose. Retry once with the validation error appended; then fall back to a rule-based extractor (dates, "I will", "you should", "by Friday").

### 8.3 Insight and weekly review

- Insight requires ≥ 2 cited sessions and a concrete next action; otherwise it is dropped.
- Weekly review aggregates: commitments opened/closed, overdue, talk ratio, interruptions (if measurable), projects touched, unanswered questions.
- All numbers are computed from the DB, not generated by the LLM; the LLM writes the narrative around them.

## 9. Providers (BYO API)

### 9.1 Config

```json
{
  "asr": {"provider": "local-sherpa", "model": "streaming-zipformer-bilingual-zh-en-int8"},
  "llm": {
    "provider": "openai-compatible",
    "baseUrl": "http://127.0.0.1:4000/v1",
    "model": "your-model",
    "apiKeyRef": "llm.default",
    "timeoutMs": 60000
  },
  "embedding": {"provider": "local-onnx", "model": "bge-small-zh"},
  "routing": {"summary": "llm", "insight": "llm", "embedding": "embedding"}
}
```

- `apiKeyRef` points to a Keystore-wrapped entry; the key never appears in the JSON, logs, or exports.
- Supported providers: `local-sherpa`, `openai-compatible` (ASR + LLM + embeddings), `ollama`, `litellm` (same protocol), `local-onnx` (embeddings).
- "Test connection" calls `GET /v1/models` (or a provider-specific ping) and reports latency/model list.
- Fallback chain per task: e.g. `llm: [primary, backup, local]`; on 401/429/timeout, try the next and show a toast.
- Cost guard: max tokens per request, max requests/day, warn before a cloud call on cellular.

### 9.2 MCP server

- Binds `127.0.0.1:8765` by default; LAN binding requires an explicit toggle + token.
- Bearer token generated on first enable, stored hashed; shown once.
- Tools:
  - `search_memory(query, limit=10)` → utterances with citations.
  - `get_daily_timeline(date)` → semantic events for that day: what happened, decisions, commitments, risks, inspirations; each with a quote + timestamp + speaker.
  - `list_commitments(status="open", due_before=null)` → commitments with owner/due/evidence.
  - `get_project(id)` → project timeline + open loops.
  - `get_session(id)` → summary + transcript.
  - `list_inspirations()` → saved/extracted ideas with source quotes.
  - `get_person(id)` → conversation profile, recurring topics, open commitments.
  - `get_insight(id)` → insight + evidence citations.
- Resources: `everheard://session/{id}/summary`, `everheard://session/{id}/transcript`, `everheard://inspiration/{id}`, `everheard://person/{id}`, `everheard://project/{id}/timeline`, `everheard://memory/{id}`. Every resource carries citations; no raw audio.
- Per-tool consent and a revocable token; the client sees only the scopes the user granted.
- Read-only in v1; no write tools.
- Transport: streamable HTTP (MCP spec); no stdio on Android.

## 10. UI

### 10.1 Screens

| Screen | Content | Key interaction |
|---|---|---|
| Today | Vertical timeline of session cards plus semantic event cards (“what happened”, decision, commitment, risk, inspiration) with time, duration, title, next-line preview, and mark count | Tap → detail; bottom taskbar persists; every event links to its quote |
| Live | Timer, waveform, live transcript with speaker colors, privacy strip | Mark; pause; stop |
| Session detail | Transcript with star marks, share, and delete | Tap a line to mark; summary/todos tabs arrive with M4 |
| Projects | Project cards with last activity and open commitments | Merge/split; open timeline |
| Project detail | Cross-session event timeline (progress, decisions, commitments, risks) + insight card | Jump to source session |
| Insight | Weekly metrics + insight cards with evidence | Drill into citations |
| Person | Last talked, open commitments, projects, conversation profile | Rename; merge clusters |
| Settings | Appearance and Language dropdowns (tap to expand), Voice profile (language dropdown, add/select Chinese or English, re-record/delete), Schedule, and Data > Clear all transcripts | Changes apply immediately; language restarts the task; clearing is confirmed; content scrolls above the taskbar |
| Onboarding | Legal notice, mic permission, enrollment, schedule default | 3 steps, skippable enrollment |

Implemented in the M2 UI slice: Home (Today/Recent timeline), Live, Transcripts, Session detail (marks/share/delete), Schedule, Settings (voice profile, schedule, data), Onboarding, Permission gate, and a persistent bottom taskbar (Today / Library / Settings). Main surfaces scroll above the bar. Voice enrollment uses a language dropdown and stores one centroid per recorded language; summary, todos, chips, Insights, Projects, Person, and provider settings remain planned. Do not ship dead links for them.

### 10.2 Tokens

- Follow the system light/dark setting. Light: grouped background `#F2F2F7`, white cards, black primary text. Dark: background `#000000`, cards `#1C1C1E`.
- One interaction blue (`#0A84FF`); semantic colors only where they carry meaning: decision `#0A84FF` / `#64D2FF`, commitment `#FF9F0A` / `#FFD60A`, risk `#FF3B30` / `#FF6961`, insight `#BF5AF2`, mark `#1C1C1E` / white.
- Type: platform system font; large title 34/38 with negative tracking, body 17/24, secondary 15/21, label 13/18. Tighten tracking as size grows; tabular numerals for timestamps.
- Spacing: 20 dp screen gutter, 16 dp card inset, 8 dp grid; grouped cards 16 dp radius, hero capture card 20 dp; 50 dp primary buttons with 14 dp radius; 60 dp list rows.
- Motion: system ripples and a restrained recording pulse; no decorative motion during capture.

### 10.3 Rules

- No developer instrumentation, model names, spike links, or milestone labels on the main surface. Debug tools remain reachable only from `adb` or a debug build.
- The capture card answers three questions at a glance: is it on, what is it doing, and what will happen next. A dot plus one sentence, not a status dashboard.
- Empty states explain what will appear and offer one primary action.
- Every screen has one large title, one primary action, and grouped rows; destructive actions are red text, never a red filled button.
- Recording state is always one glance away: persistent notification plus a red dot and "Listening" on Home.

## 11. Security and privacy

- SQLCipher database (M4+); the M2 transcript document is AES-256-GCM with a Keystore key, written atomically per update.
- Audio temp files encrypted with AES-GCM using a derived key; deleted after transcription.
- API keys in Keystore-wrapped encrypted preferences; never logged; redacted in exports.
- `FLAG_SECURE` on transcript/detail screens; biometric prompt before export.
- All components `exported=false` except the launcher activity and the notification receiver.
- MCP server localhost-only by default; bearer token; no write tools in v1.
- No analytics, no crash reporting, no network calls except configured providers.
- Logs: metadata only (state transitions, durations, error codes); no transcript text.

## 12. Performance budgets

| Budget | Target | Enforcement |
|---|---|---|
| Cold start | ≤ 1.5 s to Today | Macrobenchmark |
| Timeline scroll | 60 fps | Macrobenchmark |
| Live subtitle latency | ≤ 1.5 s | Logged per segment |
| RTF (streaming) | ≤ 0.30 | Logged; CI gate on golden audio |
| Search | ≤ 200 ms / 10k utterances | Instrumented benchmark |
| Summary | ≤ 60 s / 30 min session | Logged |
| Battery armed+paused | ≤ 1 %/h | Soak |
| Battery armed+VAD | ≤ 6 %/h | 30 min × 3, median |
| APK size | ≤ 60 MB without models | CI |

## 13. Failure modes

| Failure | Behaviour |
|---|---|
| Mic permission denied | Onboarding blocks capture; explains why; no silent fallback |
| FGS start denied (background) | Catch, post notification "Tap to start" |
| OEM kills service | `START_STICKY`; on refusal, notification; state restored from DataStore |
| Reboot | Notification only; no autostart claim |
| Model download fails / checksum mismatch | Retry with backoff; delete partial; show error + manual URL |
| Disk < 500 MB | Pause capture; delete completed temp audio; notify |
| Cloud 401/429/timeout | Fall back to local; surface provider error; queue summary for later |
| Bluetooth route fails | Fall back to phone mic; notify once |
| Media playback bleeding into mic | Detect `isMusicActive`; show a one-time warning; suggest MediaProjection for online meetings |
| Process death mid-session | Close session at last segment end; re-queue pending segments |
| Clock change / DST | Store UTC + original offset; schedule evaluates local time; recompute on `TIME_SET` |
| Database corruption | SQLCipher integrity check on open; offer export of last good backup |

## 14. Decisions and open questions

Decisions D1–D11 are in PLAN.md §8. Design-level decisions:

- **Single module** until build time > 2 min or the capture package exceeds ~15 files.
- **No native vector extension.** Brute-force cosine is enough for a personal corpus; revisit at 100k utterances.
- **No sync.** Export/import JSON is the escape hatch.
- **No TTS in v1.** The phone has a screen; reading summaries aloud is a v2 candidate.
- **Chinese-first UI**, strings externalized so an English translation can land without code changes.
- **M2 transcript storage is an encrypted JSON document**, not Room yet: AES-256-GCM with a non-exportable Keystore key, one file in `filesDir`. M4 replaces it with Room + SQLCipher when commitments and projects need indexed queries. This keeps KSP/SQLCipher out of M2 and avoids claiming a database that does not exist.

Open questions (resolve before the milestone that needs them):

1. Which Chinese ROM device is available for M0-T8? (Needed for the second half of the gate.)
2. Default schedule: 23:30–07:00 block, or ask during onboarding? (Current: ask, with that as the pre-filled default.)
3. Should the app record while charging overnight, outside the sleep block? (Current: no, block wins.)
4. MCP transport: streamable HTTP only, or also a local socket for desktop clients? (Current: HTTP.)
