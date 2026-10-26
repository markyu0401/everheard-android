# Everheard — Development Plan

> **Working title:** Everheard. Repository: `everheard-android`.
> **Status:** approved for execution, 2026-10-26. Owner: markyu0401.
> **Scope:** Android-only, open source (Apache-2.0), self-use first. No hardware, no account system, no Play Store release in v0.x.
> **Companion documents:** [DESIGN.md](DESIGN.md) (architecture), [plans/2026-10-26-m0-spikes.md](plans/2026-10-26-m0-spikes.md) (the executable plan for the next two weeks).

Dates are estimates. Each milestone has an **exit gate**; a missed gate stops the next phase until it is resolved or explicitly waived.

---

## 0. One sentence

Record the conversations you are part of, turn them into speaker-attributed text, commitments, and a project timeline — on the phone, with your own models.

## 1. Why this shape

The reference product (OPPO AI 心力球 + 白鲸 AI) validated the loop but left five gaps that users reported:

| Gap | Everheard's answer |
|---|---|
| Bystanders' talk filed as the user's progress | Relevance scoring + a visible "background" bucket + a "not mine" teaching action |
| Small time-bound promises missed | Explicit capture (mark / push-to-talk) + commitment extraction with evidence |
| Cloud account, metered by recording hours | Local-first text, no backend, no subscription |
| One phone brand only | Plain Android app, tested across Chinese OEM ROMs |
| No way to choose the model | BYO OpenAI-compatible endpoint for ASR, LLM, embeddings; MCP server for external tools |

## 2. Success criteria

**User-visible, after two weeks of real use:**

- ≥ 90 % of explicit commitments captured (manual audit of 20 sessions).
- ≤ 5 minutes/day to review Today + commitments.
- No meeting missed because the service was dead or the battery was flat.
- Zero raw audio retained by default.
- The user can correct a wrong speaker or a wrong "this is mine" in ≤ 2 taps.

**Technical budgets (measured, not assumed):**

| Metric | Target | Measured on |
|---|---|---|
| ASR real-time factor (RTF) | ≤ 0.30 | Primary phone, streaming model |
| Live subtitle latency | ≤ 1.5 s | Primary phone |
| Battery, armed + VAD-gated | ≤ 6 %/h | 30 min × 3, screen off, median |
| Battery, armed + paused | ≤ 1 %/h | 8 h soak |
| Service survival | 8 h screen-off, both ROMs | Soak test |
| "Me" attribution precision | ≥ 90 % | 3 real meetings with reference labels |
| Speaker-count error | ≤ ±1 | Same 3 meetings |
| Session summary latency | ≤ 60 s for a 30 min session | Local network / cloud provider |
| Search latency | ≤ 200 ms over 10k utterances | Primary phone |

## 3. Hard constraints

These are not preferences; they shape the product.

1. **compileSdk 37, targetSdk 36 (Android 16), minSdk 26.** AGP 9.4 supports API 37; current AndroidX (core 1.19, lifecycle 2.11, Compose 1.12) requires compileSdk 37 and AGP 9.1+. targetSdk stays 36 until the Android 17 behaviour changes are audited.
2. **A microphone foreground service cannot be created while the app is in the background.** Capture starts from a visible screen or a notification tap. No silent autostart.
3. **`BOOT_COMPLETED` cannot launch a microphone foreground service.** After reboot the app posts a "tap to resume" notification.
4. **`microphone` foreground services have no 6-hour timeout** (only `dataSync` and `mediaProcessing` do). A started service may run indefinitely, subject to OEM kills and battery.
5. **24 h continuous microphone + ASR is not battery-feasible.** The product promise is **24 h armed**, not 24 h recording: the service stays alive, schedule/battery/silence rules gate `AudioRecord`.
6. **A single phone microphone cannot measure distance.** Attribution is a score with visible uncertainty, never a guarantee.
7. **No covert capture.** Persistent notification, live screen, start tone. This is a legal and ethical constraint, not a feature toggle.

## 4. Milestones

| ID | Milestone | Dates | Deliverable | Exit gate |
|---|---|---|---|---|
| M0 | Measurement spikes | 10-27 → 11-09 | Numbers + go/no-go + scaffold | RTF ≤ 0.3; battery ≤ 6 %/h; "me" precision ≥ 90 %; 4 h FGS survival on both ROMs |
| M1 | Capture core | 11-10 → 11-23 | Armed service, schedule, guards, notification | 8 h armed soak; mic released on pause; no crash |
| M2 | ASR + transcript | 11-24 → 12-07 | Local ASR, cloud re-decode, live + detail UI | RTF/latency budgets; search ≤ 200 ms |
| M3 | Speaker attribution | 12-08 → 12-14 | Enrollment, clustering, relevance, correction | 3-meeting accuracy gate |
| M4 | Memory + commitments + Today (v0.1 MVP) | 12-15 → 12-28 | Encrypted DB, summaries, calendar, export | End-to-end 30 min demo; every item has evidence |
| M5 | Projects + weekly insight (v0.2) | 12-29 → 01-11 | Project clustering, timeline, insight, review | 20 sessions cluster sanely; insight has evidence |
| M6 | BYO API + MCP (v0.3) | 01-12 → 01-25 | Provider settings, fallback, MCP server | Swap to LiteLLM gateway; query from an MCP client |
| M7 | Hardening + v1.0 | 01-26 → 02-01 | ROM matrix, security/license review, release | 3 devices; 24 h armed soak; no P0 bugs |

**MVP is v0.1 at the end of M4 (2026-12-28).** v0.2 and v0.3 are deliberately separate: do not build project clustering before the transcript pipeline is trustworthy.

## 5. Phase detail

### M0 — Measurement spikes (2 weeks)

**Goal:** replace assumptions with numbers before writing product code.

| Task | Work | Output |
|---|---|---|
| M0-T1 | Scaffold Gradle project, version catalog, CI, empty Compose app | `./gradlew assembleDebug` green on CI |
| M0-T2 | Permission flow (mic, notifications), first-run screen | Fresh install reaches "Ready" |
| M0-T3 | `CaptureService` skeleton: FGS type microphone, notification, start/stop | Service survives 30 min screen-off |
| M0-T4 | `AudioRecord` + 60 s ring buffer + WAV writer | Valid 16 kHz mono file, no dropped frames |
| M0-T5 | Silero VAD wrapper + speech-ratio logger | Speech ratio within ±10 % of manual labels |
| M0-T6 | sherpa-onnx streaming ASR spike + model fetch script | RTF, latency, transcript |
| M0-T7 | Diarization spike: enrollment + online clustering | "Me" precision, speaker-count error |
| M0-T8 | Battery/thermal harness + ROM survival test | %/h, thermals, kill behaviour |
| M0-T9 | Spike report + decision log | Go/no-go, model choice, default mode |

**Exit gate:** all technical budgets in §2 measured on the primary phone and one Chinese ROM. If on-device ASR misses RTF or battery, switch the default to cloud ASR and keep local as an option; record the decision in DESIGN.md.

### M1 — Capture core (2 weeks)

**Goal:** a service that can stay armed all day without lying to the user.

| Task | Work |
|---|---|
| M1-T1 | `CaptureStateMachine` (STOPPED / ARMED / RECORDING / PAUSED_*) with unit tests |
| M1-T2 | `ScheduleRule` model + evaluator + weekly-grid UI (allow/block windows) |
| M1-T3 | Battery guard: pause < 15 %, resume > 30 % + charging; thermal downgrade |
| M1-T4 | Silence guard: 15 min no speech + stationary → pause; motion/unlock → resume |
| M1-T5 | Boot receiver → notification; sticky restart with `ForegroundServiceStartNotAllowedException` fallback |
| M1-T6 | Notification actions: pause 15 min / 1 h / until tomorrow, mark, stop; quick-settings tile |
| M1-T7 | 8 h armed soak on both ROMs + report |

**Exit gate:** 8 h screen-off; mic indicator off during schedule pause; battery ≤ 1 %/h paused, ≤ 6 %/h VAD-gated; no crash; documented OEM workaround.

### M2 — ASR + transcript (2 weeks)

| Task | Work |
|---|---|
| M2-T1 | `AsrEngine` interface + model manager (pinned URL, SHA-256, license screen, disk check) |
| M2-T2 | Local streaming provider (sherpa-onnx Zipformer bilingual int8) with partial results |
| M2-T3 | Cloud provider: OpenAI-compatible `/v1/audio/transcriptions`; per-session consent |
| M2-T4 | Two-pass merge: live draft + cloud re-decode by timestamp |
| M2-T5 | Live capture screen + session detail (summary/transcript/todos tabs) + FTS search |
| M2-T6 | Hotword list (people, projects), punctuation, number/date normalization |

**Exit gate:** RTF ≤ 0.3; live latency ≤ 1.5 s; search ≤ 200 ms; transcript survives process death; model download failure is recoverable.

### M3 — Speaker attribution (1 week)

| Task | Work |
|---|---|
| M3-T1 | 30 s enrollment flow; embedding stored per person |
| M3-T2 | Online clustering (1.5 s windows, cosine ≈ 0.7) + merge/split |
| M3-T3 | Relevance score: self voiceprint, attendee count, second-person semantics, loudness/reverb |
| M3-T4 | Rename cluster → person; person page (last talked, open commitments) |
| M3-T5 | "Not mine" swipe → noise classifier; downrank future similar segments |

**Exit gate:** 3 meetings, "me" precision ≥ 90 %, speaker count ±1, correction in ≤ 2 taps. If precision < 90 %, ship with "background" folding and no automatic person attribution.

### M4 — Memory + commitments + Today (v0.1 MVP, 2 weeks)

| Task | Work |
|---|---|
| M4-T1 | Room + SQLCipher schema; Keystore-wrapped key; migration policy |
| M4-T2 | Bitemporal commitment/entity model (`valid_from`, `valid_to`, `recorded_at`) |
| M4-T3 | Summary JSON schema + validator (drop items without evidence) |
| M4-T4 | LLM provider abstraction (OpenAI-compatible), retry/repair, cost guard |
| M4-T5 | Commitment → system calendar/reminder; dedupe on re-extraction |
| M4-T6 | Today timeline UI (cards, chips, marks, empty states) |
| M4-T7 | Markdown/JSON export + delete flows |
| M4-T8 | End-to-end demo: 30 min session → summary → 5 commitments → calendar → export |

**Exit gate:** summary ≤ 60 s; every extracted item has a quote + timestamp + speaker; calendar insert works; export/delete verified; no plaintext content in logs.

### M5 — Projects + weekly insight (v0.2, 2 weeks)

| Task | Work |
|---|---|
| M5-T1 | Project clustering (summary embedding + ≥ 2 shared entities or cosine > 0.82); user merge/split as constraints |
| M5-T2 | Project timeline UI (events, decisions, open commitments, insight card) |
| M5-T3 | Insight generation with evidence chain; reject insights without ≥ 2 citations |
| M5-T4 | Weekly review: commitment completion, open loops, talk ratio, interruptions |
| M5-T5 | Person page aggregation |

**Exit gate:** 20 sessions produce a sane project set (manual review); every insight cites ≥ 2 sessions; weekly review renders in < 2 s.

### M6 — BYO API + MCP (v0.3, 2 weeks)

| Task | Work |
|---|---|
| M6-T1 | Provider settings for ASR/LLM/embedding: base URL, key, model, "test connection" |
| M6-T2 | Per-task routing, fallback chain, timeout, token/cost guard |
| M6-T3 | MCP server over HTTP with bearer token: `search_memory`, `list_commitments`, `get_project`, `get_session` |
| M6-T4 | Optional MCP client to call external tools |
| M6-T5 | JSON export compatible with the MCP schema |

**Exit gate:** point the app at a LiteLLM gateway and swap models without rebuilding; an MCP client (Claude/Cursor) answers "what did I promise this week?" with citations.

### M7 — Hardening + v1.0 (1 week)

| Task | Work |
|---|---|
| M7-T1 | ROM matrix: Samsung + 2 Chinese ROMs; in-app reliability checklist |
| M7-T2 | 24 h armed soak (sleep window charges) |
| M7-T3 | Security review: exported components, token storage, FLAG_SECURE, log audit |
| M7-T4 | License audit: models, AAR, SQLCipher; update THIRD_PARTY_NOTICES |
| M7-T5 | Docs, build script, signed release APK (key held offline) |
| M7-T6 | Tag v1.0.0; release notes |

**Exit gate:** 3 devices pass; 24 h armed run with schedule; no P0/P1 bug; a fresh contributor can build from README.

## 6. Cross-cutting engineering

### Version policy

- Pin every dependency in `gradle/libs.versions.toml`; no dynamic versions.
- Starting set: **AGP 9.4.1, Gradle 9.8.2, built-in Kotlin (no `org.jetbrains.kotlin.android` plugin), Compose BOM 2026.09.00, Room 2.8.5, minSdk 26, compileSdk 37, targetSdk 36, JVM 17.**
- AGP 9.4.1 is required by current AndroidX artifacts (core 1.19.1, lifecycle 2.11.0, Compose 1.12.1) and supports API 37. Built-in Kotlin replaces the Kotlin Android plugin; kapt is not used.
- sherpa-onnx: `sherpa-onnx-2.0.0.aar` from the official release, SHA-256 pinned; fallback `1.13.8` if the 2.0 API breaks.

### Repository workflow

- Trunk-based: `main` always green; short-lived branches `feat/…`, `fix/…`, `docs/…`.
- Conventional commits; squash merge; PR title = commit message.
- Every PR: `./gradlew lint testDebugUnitTest assembleDebug`.
- Issues carry a milestone and one `area/*` label. Task IDs in this plan match issue titles.

### Testing

| Layer | What | Tooling |
|---|---|---|
| Unit | State machine, schedule evaluator, schema validator, clustering math, bitemporal updates | JUnit4 + Truth |
| Robolectric | Permission gate, notification builder, settings | Robolectric |
| Instrumented | Service start/stop, AudioRecord, DB encryption, calendar insert | AndroidX Test |
| Golden audio | 3 meetings with reference transcripts; WER + diarization report | `scripts/eval_audio.sh` |
| Soak | 8 h / 24 h armed runs; battery and thermals | `scripts/battery_probe.sh` |
| Manual | ROM matrix, bystander handling, legal screens | Checklist in ROM-MATRIX.md |

### Measurement protocol

- 30 min session, screen off, 50 % brightness, normal network; read `BatteryManager` before/after; repeat 3×, take the median.
- RTF: decoded audio seconds ÷ wall seconds; log per segment.
- Diarization: 3 real meetings with known speaker counts; report precision/recall, not impressions.
- Thermal: `dumpsys thermalservice` before/after; record throttle status.

### Definition of done (every task)

1. Code + tests committed; CI green.
2. The user-visible outcome works on the primary phone.
3. Measurement recorded if the task changes battery, latency, or accuracy.
4. Docs updated (DESIGN.md decision, ROM-MATRIX, THIRD_PARTY_NOTICES if dependencies changed).
5. No new P0/P1 bug; no secret or personal data in the diff.

## 7. Risks

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| Chinese ASR accuracy below usable on real meetings | Medium | High | Two-pass decode; hotwords; user correction; cloud fallback |
| Single-mic diarization unusable | Medium | High | Enrollment + "background" folding; ship without auto person labels if gate fails |
| Battery drain makes 24 h armed impractical | High | High | VAD gating, schedule, silence guard; measure in M0; charge during sleep window |
| OEM kills the service | High | Medium | ROM matrix; notification recovery; in-app guidance; no boot autostart promise |
| Model licensing blocks redistribution | Medium | Medium | Runtime download; license screen; audit at M7 |
| Scope creep into projects/insight before transcripts are solid | High | Medium | Phase gates; M4 MVP freeze |
| Legal exposure from bystander recording | Medium | High | Visible capture; meeting announcement; public-place degradation; README |
| Android API change (17) during development | Low | Medium | Pin target 36; revisit after v1.0 |

## 8. Decisions

| ID | Decision | Rationale |
|---|---|---|
| D1 | Single Gradle module with packages, not 4 modules | Solo project; build time and refactor cost outweigh boundaries until v1.0 |
| D2 | AGP 9.4.1 + Gradle 9.8.2 + built-in Kotlin, compileSdk 37 | Current AndroidX requires AGP 9.1+ and compileSdk 37; built-in Kotlin removes the Kotlin Android plugin and its version-coupling risk |
| D3 | targetSdk 36, minSdk 26 | Current rules + broad Chinese ROM coverage |
| D4 | On-device ASR is the default; cloud is opt-in | Privacy and offline use; cloud is the accuracy escape hatch |
| D5 | One long-lived armed FGS; schedule gates `AudioRecord` | Background FGS creation is forbidden; stop/start cannot be scheduled |
| D6 | Promise "24 h armed", never "24 h recording" | Battery physics; honest product claim |
| D7 | SQLCipher + brute-force cosine over float16 embeddings | Avoids a native vector extension; personal corpus is small (< 50k utterances) |
| D8 | Apache-2.0 | Patent grant; compatible with sherpa-onnx and AndroidX |
| D9 | No backend, no account, no sync in v1 | Removes the largest privacy and operational surface |
| D10 | BYO API is a first-class feature, not an advanced setting | The reference product's closed model is its main limitation |
| D11 | "Everheard" is a working name | Trademark check before v1.0; rename is a repo setting + string change |

## 9. Out of scope for v1

- Hardware, BLE, wearable firmware.
- iOS, desktop, web.
- Account system, cloud sync, team sharing.
- Play Store distribution, subscription billing.
- TTS, translation, meeting bots, calendar write-back beyond commitments.
- Real-time coaching during a meeting (candidate for v2).

## 10. Change control

- A milestone gate is not waived silently: write the waiver and the reason in DESIGN.md § Decisions, and update this table.
- New features go to `docs/BACKLOG.md` (created when the first one appears), not into the current milestone.
- If a spike invalidates a hard constraint (e.g. on-device ASR is unusable), stop and rewrite the affected phase before proceeding.

## 11. Implementation progress (2026-10-26)

- **M0-T1 → T6 done:** Gradle scaffold, onboarding/legal/permission gates, capture service skeleton, `AudioRecord` + ring buffer + WAV writer, Silero VAD, streaming sherpa-onnx ASR, and speaker embedding. The ASR model is the 2023-02-16 bilingual small Zipformer; `modelType` is left empty so sherpa detects it (forcing `zipformer2` aborts on the missing `query_head_dims` metadata).
- **M0-T7 harness done:** speaker spike screen and embedding path; real-meeting precision numbers are still pending.
- **M0-T8/T9 pending:** battery/thermal and ROM survival need a phone; the report template is in `docs/spikes/2026-11-09-spike-report.md`.
- **M1-T1 → T6 done:** 8-state machine with 15 unit tests; schedule model + DataStore + weekly editor (day chips, time pickers, add/edit/delete); battery/thermal guards; boot “tap to resume” notification; sticky-restart fallback; pause 15 min / 1 h / until tomorrow, mark, stop, and a quick-settings tile; pause window persists across process death.
- **M1-T4 caveat:** the silence guard currently uses screen-off as the motion proxy; sensor fusion and the 8 h false-pause measurement are still open.
- **M1-T7 pending:** 8 h soak protocol is in `docs/spikes/2026-11-23-m1-soak.md`.
- **Verification:** `./gradlew lint testDebugUnitTest assembleDebug` is green; 49 unit tests and 6/6 instrumented tests pass on an AOSP ATD API 36 emulator. The schedule→service→notification path was exercised end to end (block end moved to 08:00 → “Paused — outside the schedule”).
- **Next:** run the M1 soak on the Samsung and one Chinese ROM, then start M2 (ASR pipeline, transcript store, live/detail UI).
