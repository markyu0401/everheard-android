# Everheard — Feature Backlog

Updated: 2026-10-27. This is the working backlog for feature parity with the
reference-product loop, plus the engineering gaps behind it. A feature is
only marked **Shipped** when it works on a real screen and has tests.

## Status

| Status | Meaning |
|---|---|
| **Shipped** | In `main`, tested, visible in the app |
| **Partial** | The useful slice exists; the parity version is not complete |
| **Planned** | Scoped here; not built yet |
| **Blocked** | Needs a model, key, device, or measurement |

## Parity matrix

| Reference capability | Everheard status | What exists now | Next |
|---|---|---|---|
| **Today** timeline | **Partial** | Home shows today's sessions (or recent), plus up to three semantic highlights extracted locally; session cards carry time, duration, title, next-line preview, mark count; tap → detail | Add todos, full event cards and chips; add a mark gesture from Today |
| **Semantic daily timeline** | **Partial (M4)** | Local rule analyzer extracts progress / decision / commitment / risk / inspiration from transcript lines; Today shows the top three; session detail shows the full timeline | LLM schema extraction for better recall; every event still needs quote + timestamp + speaker; no event without evidence |
| **Inspiration / idea notes** | **Partial (M4)** | Marked lines and keyword ideas become “Ideas” cards; no manual “save this thought” action yet | Add a capture-time / Live “save idea” action; keep source quote |
| **Manual highlight** | **Shipped** | Tap any transcript line in Session detail to star/unstar it; marked lines get an amber tint; Today shows the mark count; mark is persisted in the encrypted store | Add a capture-time mark action (notification/Live) and a "Marked" filter |
| **Session share / delete** | **Shipped** | Share a session as text, Markdown or JSON; delete one session with confirmation; Settings → Data → clear all | Add bulk export and export redaction |
| **Structured summary** | **Partial (M4)** | Local extractive summary plus optional OpenAI-compatible LLM analyzer (JSON schema/evidence validator); rule-based fallback; every item cites a transcript line | Hierarchical summary; retry/repair/cost guard; bitemporal extraction |
| **Todos / commitments** | **Partial (M4)** | Local rule extractor finds commitments and due hints; encrypted memory store persists them; session detail lists open commitments | Bitemporal commitment model, dedupe, calendar/reminder bridge, completion state |
| **Memory store** | **Shipped (M4 slice)** | Encrypted `memory.bin` (AES-256-GCM, separate Keystore alias), versioned JSON; save/get/delete/clear; session delete and clear-all remove memory | Room + SQLCipher migration; FTS/vector retrieval; project/person relations |
| **Projects** | **Partial (M5)** | Keyword-based project clustering from transcripts + memory events; Projects tab lists sessions, last activity, open commitments; project detail shows a cross-session timeline, commitments and source sessions | Embedding clustering, merge/split, manual constraints |
| **Insight / 觉察** | **Partial (M5)** | Deterministic weekly report: sessions, minutes, decisions, commitments, risks, ideas, projects; cited observations with a next action | O-Mind-style LLM observations; talk ratio/interruptions; weekly review UI polish |
| **Conversation profile / long-term memory** | **Partial (M5)** | People tab from explicit speaker labels; person detail shows sessions, open commitments and quotes; no invented attendees | Online clustering, rename/merge/split, long-term summaries |
| **Connect / MCP** | **Shipped (M6 slice)** | Local read-only MCP HTTP server bound to 127.0.0.1 with a bearer token; tools: search_memory, get_daily_timeline, list_commitments, get_project, get_session, list_inspirations, get_person, get_insight; resources for summaries/transcripts/ideas/projects/people; Settings UI shows endpoint/token | Per-tool scopes UI; IM bridge; streamable HTTP/SSE compatibility polish |
| **MCP resources** | **Shipped (M6 slice)** | Resources and tools return evidence quotes, session ids, timestamps; no write tools | Scope consent audit; resource pagination |
| **IM bridge** | **Blocked** | None | Read-only WeChat/Feishu memory Q&A; requires platform APIs/credentials and a server-side component |
| **Export / redaction** | **Partial** | Session share as text, Markdown and JSON; JSON includes memory/evidence | Redaction rules, bulk export, delete by project/person |
| **Today / Library / Projects / Insights / Settings tabs** | **Shipped (M5 slice)** | Four-tab taskbar (Today / Library / Memory / Settings) with a centered capture orb; Projects and Insights are folded into Memory; detail screens keep a back affordance | Keep the four destinations + orb; no dead destinations |
| **Voice profile enrollment** | **Shipped** | Settings → Voice profile uses a language dropdown (中文 / Chinese, English / 英文), records one or both languages separately, quality-gates each, and stores encrypted per-language centroids. Re-record and delete supported | Add more language options when prompt sets exist; per-language threshold calibration |
| **Speaker attribution** | **Partial** | Capture compares a segment against all enrolled centroids, labels `me` at best cosine ≥ 0.72, and logs the score; UI shows “Me”; other speakers are not clustered yet | Real-device precision/recall; online clustering; “not mine” correction; threshold calibration; person graph |
| **Manual mark at capture time** | **Planned** | Mark action exists only after transcription | Notification/Live mark that attaches to the current segment |
| **Provider settings (BYO)** | **Shipped (M6 slice)** | Settings → Model provider stores LLM/ASR/embedding base URL, model and API key in an encrypted `providers.bin`; LLM connection test; remote LLM analyzer wired to session detail with rule-based fallback | ASR/embedding providers are config-only; per-task routing/fallback/cost guard |
| **Calendar bridge** | **Partial (M4)** | Commitments with a recognizable due hint can open a calendar-insert intent from session detail; the user confirms | Bitemporal due dates, dedupe, reminders |
| **Model manager** | **Blocked** | Models bundled in the debug APK (372 MB) | Runtime download requires a sherpa-onnx file-path API; SHA-256/license screen |
| **Cloud ASR re-decode** | **Blocked** | Local SenseVoice final decode; audio is deleted by design | Needs an opt-in audio-retention window and consent; not in the current privacy model |
| **Hotwords / normalization** | **Planned (M2-T6)** | None | People/project names, punctuation, number/date normalization |
| **FTS search** | **Partial** | Substring search over utterances | SQLite FTS5 + ranking, date/language filters |
| **Session detail tabs** | **Partial** | Stacked Summary / Timeline / Commitments / Ideas above the transcript; no segmented control | Summary / Transcript / Actions segmented control; tap an event to jump to its quote |
| **ROM matrix / survival** | **Partial** | S26 Ultra + AOSP emulator; Tailscale install | 8 h/24 h soak, Chinese OEM background guidance, boot recovery |
| **Excluded-app playback** | **Partial (M1 slice)** | Settings → Capture → Excluded apps: user picks launchable apps; the service pauses when one is in the foreground and playing audible media/communication audio. Needs the user-granted Usage access special permission. | Detect PiP/background playback without foreground package; per-app allow/deny for meetings |
| **Mic busy / concurrent capture** | **Shipped (M1 slice)** | `AudioRecordingMonitor` reads our own `AudioRecord` silenced state (authoritative) plus other active clients; `PAUSED_MIC_BUSY` closes the mic instead of writing silence. An interrupted recording posts a separate high-importance heads-up ("Microphone taken"), while merely waiting for a busy mic shows "Waiting" with no alert. Resumes automatically when the mic frees; a 2 s open grace stops the guard from mistaking our own session for another app. Voice input, calls and meeting apps can take the mic; an ordinary app cannot capture concurrently. | MediaProjection internal-audio path for meeting recording (separate consent + foreground type) |
| **24 h armed** | **Partial** | ARMED + schedule/battery/thermal/silence gates; battery resumes only at 30 % (or while charging), and a silence pause resumes on motion or unlock | Soak numbers; charge-during-sleep guidance |
| **Privacy** | **Shipped** | Persistent notification, visible capture, FLAG_SECURE, AES-256-GCM + Keystore, no raw audio retained, `allowBackup=false` | Log audit; export redaction; MCP scope audit |

## Priority order

1. **P0 — trustworthy input**: real-device ASR benchmark (CER/WER), voice-profile
   threshold calibration, speaker clustering/correction, segment merge tuning,
   mark-at-capture, and visible mic-busy/app-audio states. Everything downstream
   is worthless if the transcript is wrong.
2. **P1 — memory layer**: BYO LLM provider, structured summary + commitments,
   semantic daily timeline (what happened / decision / commitment / risk),
   inspiration notes, Today chips, session detail tabs. This is the actual reference-product
   parity gap. The local baseline, encrypted memory store, optional LLM analyzer,
   calendar intent and Markdown/JSON export are in place.
3. **P2 — projects and insight**: keyword project clustering, cross-session
   project timeline, conversation profile / long-term memory, deterministic
   weekly review with citations. Embedding clustering, merge/split and O-Mind
   style observations remain.
4. **P3 — connection and distribution**: local MCP server + resources is in
   place; IM bridge, export redaction, ROM matrix, model manager and release
   hardening remain.

## Design backlog

- **Today**: vertical day timeline; session cards plus semantic event cards
  (“what happened”, decision, commitment, risk, inspiration) with time,
  duration, title, one-line preview, mark chip; semantic chips only when
  extraction exists. Every event links to its quote.
- **Session detail**: segmented `Summary / Transcript / Actions`; every summary
  item links to its quote; marks are visible in both views.
- **Projects**: project cards with last activity + open commitments; project
  detail with a cross-session timeline (progress, decisions, commitments,
  risks) and an insight card; jump to source session.
- **Insight**: weekly metrics + evidence cards; never an insight without at
  least two citations.
- **Conversation profile**: recurring people, topics, open commitments and
  long-term summaries; rename/merge/split; every claim links to a session.
- **Connect**: chat-like memory query; MCP is the first integration, IM later.
  MCP exposes read-only tools and resources (summaries, inspirations, profile,
  long-term memory) with per-tool consent and a revocable token.
- **Navigation**: persistent bottom taskbar with Today, Library, Projects,
  Insights, and Settings; detail screens keep a back button and hide the bar.
  Schedule is folded into Settings, Live into Today.
- **Scrolling**: every main surface scrolls vertically above the fixed taskbar;
  voice enrollment and settings must never clip below the fold.
- **Settings**: Appearance and Language are tap-to-expand dropdown rows; Voice
  profile row (language dropdown, add/select Chinese or English,
  re-record/delete); People row; Model provider row; MCP server row; Schedule
  row; Data row for clearing transcripts.
- **Projects**: keyword clusters from transcripts + memory events; project
  detail shows a cross-session timeline, open commitments and source sessions.
  Merge/split and embeddings remain.
- **Insight**: deterministic weekly metrics and cited observations; an insight
  never appears without at least two citations.
- **Connect**: local read-only MCP server bound to 127.0.0.1 with a bearer token;
  tools and resources expose summaries, transcripts, commitments, ideas,
  projects and people. IM is blocked on platform APIs.
- **Provider**: BYO OpenAI-compatible LLM/ASR/embedding configuration; API keys
  encrypted with a dedicated Keystore alias; LLM extraction falls back to the
  local rule analyzer on failure.
- **Privacy**: voiceprint is a biometric; it is used for attribution, never for
  authentication, and can be deleted.
- **Motion**: restrained; no gradients, no dashboard chrome, no developer
  labels on the main surface.
- **Empty states**: explain what will appear and offer one primary action.

## Guardrails

- No dead links or placeholder tabs in a release build.
- No fabricated summary, todo, project, or insight. If the provider is not
  configured, say so.
- No raw audio retained by default; no covert capture.
- No secrets, keys, transcripts, device serials, or LAN addresses in the repo.
- Every feature needs a test and a real-screen check before it is marked
  **Shipped**.
