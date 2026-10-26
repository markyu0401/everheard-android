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
- Only speech frames go to ASR; silence frames are dropped after VAD.
- Endpoint on ~500 ms silence; hard cap 30 s; emit `SegmentPcm(startMs, endMs, samples)`.
- Speech ratio is logged per hour (not content) to drive the battery report.

### 3.3 Storage during capture

- Segments are written to app-private storage as Opus/FLAC temp files, encrypted with the same data key.
- A `segment` row is created immediately with `state = PENDING`; after ASR it becomes `TRANSCRIBED` and the audio file is deleted (default).
- Crash recovery: on service start, any `PENDING` segment with a file is re-queued; any `RECORDING` session is closed at its last known timestamp.

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
- Model: streaming Zipformer bilingual zh-en int8, encoder 174 MB + decoder 13 MB + joiner 3.1 MB (~190 MB total).
- Streaming partials for the live screen; final decode on segment end.
- Model manager: download on first run, verify SHA-256, show license + size, refuse to start if free space < 1 GB.
- Do **not** keep SenseVoice resident: 239 MB file, 600–700 MB RAM peak on Android. It is a batch option only.

### 5.3 Cloud provider

- OpenAI-compatible `POST /v1/audio/transcriptions` (multipart WAV/Opus), model name configurable.
- Per-session consent: show host, model, and what will be sent; remember the choice per provider, not per session forever.
- Timeout 60 s; retry once on 5xx; on 401/429 fall back to local and surface the error.

### 5.4 Two-pass merge

1. Live draft from the local streaming model (fast, lower accuracy).
2. After session end, send segments to the cloud or a local GPU host (faster-whisper large-v3) for a final decode.
3. Merge by timestamp: final text wins inside its segment; keep the draft's word timings for the UI.
4. If the final decode fails, keep the draft and mark `refined = false`.

### 5.5 Text post-processing

- Punctuation model (sherpa-onnx) or LLM punctuation; numbers/dates normalized to a canonical form for extraction (`2026-10-30`, `15:00`).
- Hotword list: people, projects, product names; applied as context bias where supported, and as a post-hoc re-rank (edit distance) otherwise.
- Language: zh default; code-switch zh-en handled by the bilingual model; per-session override.

## 6. Speaker attribution

### 6.1 Enrollment

- 30 s read passage; extract a 256-d embedding; store in `person` with `is_self = 1`.
- Re-enrollment allowed; keep the old embedding for fallback until the new one is validated.

### 6.2 Online clustering

- Window: 1.5 s of speech, 0.75 s hop; embedding per window.
- Cosine similarity; assign to an existing cluster if ≥ 0.70, else create a new cluster.
- Merge clusters if centroid similarity ≥ 0.80; split on user request.
- Cluster labels: `我`, `说话人 A`, `说话人 B`, `背景`.
- Person rename maps a cluster to a `person` row; future matches use the person's centroid.

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
  - `list_commitments(status="open", due_before=null)` → commitments with owner/due/evidence.
  - `get_project(id)` → project timeline + open loops.
  - `get_session(id)` → summary + transcript.
- Read-only in v1; no write tools.
- Transport: streamable HTTP (MCP spec); no stdio on Android.

## 10. UI

### 10.1 Screens

| Screen | Content | Key interaction |
|---|---|---|
| Today | Vertical timeline of sessions, chips (decisions/commitments/risks), marks | Tap → detail; long-press → delete/mark |
| Live | Timer, waveform, live transcript with speaker colors, privacy strip | Mark; pause; stop |
| Session detail | Tabs: Summary / Transcript / Todos; every item links to its quote | Add to calendar; "not mine" swipe; edit speaker |
| Projects | Project cards with last activity and open commitments | Merge/split; open timeline |
| Project detail | Event timeline + insight card | Jump to source session |
| Insight | Weekly metrics + insight cards with evidence | Drill into citations |
| Person | Last talked, open commitments, projects | Rename; merge clusters |
| Settings | Capture mode, schedule grid, providers, retention, reliability, export/delete | Test connection; ROM checklist |
| Onboarding | Legal notice, mic permission, enrollment, schedule default | 3 steps, skippable enrollment |

### 10.2 Tokens

- Dark-first; near-black background `#0A0B0D`, panel `#141619`, line `rgba(255,255,255,.08)`.
- Semantic colors only: decision `#5FD3C4`, commitment `#E8B45A`, risk `#E88A8A`, mark white, insight `#A99CF5`.
- Type: system font (PingFang SC / Noto Sans CJK), tabular numerals for timestamps.
- Spacing: 4/8/12/16; cards 18 dp radius; 48 dp touch targets.
- Motion: 150–250 ms; timeline cards spring in; no decorative animation during recording.

### 10.3 Rules

- Every AI conclusion is tappable and opens its source sentence.
- Confidence is visible: a dot (high/medium/low), not a false precision number.
- Empty states explain what will appear and how to trigger capture.
- Recording state is always one glance away: persistent notification + a red/amber dot in the app bar.

## 11. Security and privacy

- SQLCipher database; 256-bit key generated on first run, wrapped by Android Keystore (StrongBox when available).
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

Open questions (resolve before the milestone that needs them):

1. Which Chinese ROM device is available for M0-T8? (Needed for the second half of the gate.)
2. Default schedule: 23:30–07:00 block, or ask during onboarding? (Current: ask, with that as the pre-filled default.)
3. Should the app record while charging overnight, outside the sleep block? (Current: no, block wins.)
4. MCP transport: streamable HTTP only, or also a local socket for desktop clients? (Current: HTTP.)
