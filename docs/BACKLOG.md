# Everheard — Feature Backlog

Updated: 2026-10-26. This is the working backlog for feature parity with the
OPPO AI 心力球 / 白鲸 AI loop, plus the engineering gaps behind it. A feature is
only marked **Shipped** when it works on a real screen and has tests.

## Status

| Status | Meaning |
|---|---|
| **Shipped** | In `main`, tested, visible in the app |
| **Partial** | The useful slice exists; the parity version is not complete |
| **Planned** | Scoped here; not built yet |
| **Blocked** | Needs a model, key, device, or measurement |

## Parity matrix

| OPPO / 白鲸 AI capability | Everheard status | What exists now | Next |
|---|---|---|---|
| **Today** timeline | **Partial** | Home shows today's sessions (or recent), with time, duration, title, next-line preview, mark count; tap → detail | Add summary, todos, semantic chips, manual mark gesture from Today |
| **Manual highlight** | **Shipped** | Tap any transcript line in Session detail to star/unstar it; marked lines get an amber tint; Today shows the mark count; mark is persisted in the encrypted store | Add a capture-time mark action (notification/Live) and a "Marked" filter |
| **Session share / delete** | **Shipped** | Share a session as plain text; delete one session with confirmation; Settings → Data → clear all | Add JSON/Markdown export and bulk export |
| **Structured summary** | **Planned (M4)** | None; do not fake one | Summary JSON schema + validator; every item must cite a quote + timestamp |
| **Todos / commitments** | **Planned (M4)** | None | Bitemporal commitment model, dedupe, calendar/reminder bridge |
| **Projects** | **Planned (M5)** | None | Embedding clustering + entity overlap; project timeline; merge/split |
| **Insight / 觉察** | **Planned (M5)** | None | Weekly metrics + O-Mind-style behavior observations with ≥ 2 citations and one action suggestion |
| **Connect / MCP** | **Planned (M6)** | Share/export only | HTTP MCP server with bearer token: `search_memory`, `list_commitments`, `get_project`, `get_session`; JSON schema |
| **Voice profile enrollment** | **Shipped** | Settings → Voice profile records a Chinese sample and an English sample separately; each is quality-gated, embedded with CAM++, normalised, and stored encrypted as multiple centroids. Re-record and delete supported | Calibrate thresholds per language/acoustic condition; add more conditions later |
| **Speaker attribution** | **Partial** | Capture compares a segment against all enrolled centroids, labels `me` at best cosine ≥ 0.72, and logs the score; UI shows “Me”; other speakers are not clustered yet | Real-device precision/recall; online clustering; “not mine” correction; threshold calibration |
| **Manual mark at capture time** | **Planned** | Mark action exists only after transcription | Notification/Live mark that attaches to the current segment |
| **Provider settings (BYO)** | **Planned (M6)** | None | ASR/LLM/embedding base URL, key, model, test connection, fallback chain |
| **Model manager** | **Planned (M2-T1)** | Models bundled in the debug APK (357 MB) | Runtime download, SHA-256, license screen, disk check |
| **Cloud ASR re-decode** | **Planned (M2-T3)** | Local SenseVoice final decode | OpenAI-compatible `/v1/audio/transcriptions`, per-session consent |
| **Hotwords / normalization** | **Planned (M2-T6)** | None | People/project names, punctuation, number/date normalization |
| **FTS search** | **Partial** | Substring search over utterances | SQLite FTS5 + ranking, date/language filters |
| **Today / Library / Insights / Settings tabs** | **Planned** | Home + separate activities | Add bottom tabs only when each destination exists; no dead tabs |
| **Session detail tabs** | **Planned** | One transcript list with marks | Summary / Transcript / Actions segmented control after M4 |
| **ROM matrix / survival** | **Partial** | S26 Ultra + AOSP emulator; Tailscale install | 8 h/24 h soak, Chinese OEM background guidance, boot recovery |
| **24 h armed** | **Partial** | ARMED + schedule/battery/thermal/silence gates | Soak numbers; charge-during-sleep guidance |
| **Privacy** | **Shipped** | Persistent notification, visible capture, FLAG_SECURE, AES-256-GCM + Keystore, no raw audio retained, `allowBackup=false` | Log audit; export redaction |

## Priority order

1. **P0 — trustworthy input**: real-device ASR benchmark (CER/WER), voice-profile
   threshold calibration, speaker clustering/correction, segment merge tuning,
   mark-at-capture. Everything downstream is worthless if the transcript is wrong.
2. **P1 — memory layer**: BYO LLM provider, summary + commitments, Today chips,
   session detail tabs. This is the actual OPPO parity gap.
3. **P2 — projects and insight**: clustering, project timeline, weekly review
   with citations.
4. **P3 — connection and distribution**: MCP server, export schema, ROM matrix,
   model manager, release hardening.

## Design backlog

- **Today**: vertical day timeline; session cards with time, duration, title,
  one-line preview, mark chip; semantic chips for decision/commitment/risk once
  extraction exists.
- **Session detail**: segmented `Summary / Transcript / Actions`; every summary
  item links to its quote; marks are visible in both views.
- **Projects**: project cards with last activity + open commitments; project
  detail with a timeline and an insight card.
- **Insight**: weekly metrics + evidence cards; never an insight without at
  least two citations.
- **Connect**: chat-like memory query; MCP is the first integration, IM later.
- **Settings**: Voice profile row (enrolled/not enrolled), re-record and delete;
  Data row for clearing transcripts.
- **Voice enrollment**: Chinese and English samples recorded separately, quality feedback, encrypted vectors only; no audio retained. The profile keeps one centroid per language so code-switching does not collapse attribution.
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
