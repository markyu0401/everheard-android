# M0 spike report — 2026-11-09

**Status:** pending device measurements. The harnesses and the in-app spike screens are implemented; the numbers below must be filled on real hardware.

**Emulator smoke (AOSP ATD API 36, arm64):** 10/10 instrumented tests pass — service start + foreground notification, `AudioRecord` frame size, Silero VAD initialization, streaming ASR model load + silence, streaming partial sessions, speaker embedding, the onboarding permission gate, the encrypted transcript store (AES-GCM round-trip + search), and the capture transcriber (empty-session suppression + fake-ASR utterance append). UI flows for onboarding, schedule editing (day chips, time pickers), live schedule gating, live transcript, transcript list/detail, and search were exercised with `uiautomator`; changing the block end time to 08:00 flipped the notification to “Paused — outside the schedule” without restarting the service. These are correctness checks, not battery/accuracy numbers.

**Real-device smoke (Samsung SM-S948U1, Galaxy S26 Ultra, Android 17 / API 37, arm64):** 10/10 instrumentation tests pass via `adb shell am instrument` (`OK (10 tests)`), including the native sherpa ASR and speaker models. The service starts as a `microphone` foreground service; with the default 23:30–07:00 block it correctly reports **Paused — outside the schedule**; deleting the block switches it to **Recording — transcript only** with the microphone active. Battery was 94–100 % while charging, so this run does **not** yield a %/h number. Note: Gradle's `connectedDebugAndroidTest` task exits 1 on this Samsung build even though the test XML says 10 tests / 0 failures and `am instrument` prints `OK (10 tests)` / `INSTRUMENTATION_CODE: -1`; treat the manual instrumentation output as authoritative until the Samsung/Gradle quirk is isolated.

**Device / ROM:** <model, Android version, build>
**Build:** <git sha, debug/release>
**Conditions:** screen off, 50 % brightness, unplugged, <network>

## What was measured

| Metric | Run 1 | Run 2 | Run 3 | Median | Budget | Pass? |
|---|---|---|---|---|---|---|
| RTF (streaming ASR) | | | | | ≤ 0.30 | |
| Live latency (s) | | | | | ≤ 1.5 | |
| Battery armed+VAD (%/h) | | | | | ≤ 6 | |
| Battery armed+paused (%/h) | | | | | ≤ 1 | |
| "Me" precision | | | | | ≥ 0.90 | |
| Speaker-count error | | | | | ≤ ±1 | |
| FGS survival (h) | | | | | ≥ 4 | |

## Method

- Battery: `scripts/battery_probe.sh <serial> 30`, three times, median. The script refuses to run while charging or with the screen on.
- Thermal: `scripts/thermal_probe.sh <serial> 30`.
- ASR: in-app **ASR spike** (10 s recording), record RTF and latency; save the result file path.
- VAD: in-app **VAD spike** (10 s recording), record speech ratio and CSV path.
- Diarization: in-app **Speaker spike** (10 s recording), record cluster count and CSV path; for the real gate, run three 10–15 min meetings with known speakers.
- ROM survival: start the service from the home screen, screen off, 4 h; check notification, heartbeat, mic-off during the schedule block.

## Raw results

### Battery

| Run | Start % | End % | Duration (h) | %/h | Thermal max |
|---|---|---|---|---|---|
| 1 | | | | | |
| 2 | | | | | |
| 3 | | | | | |

### ASR

| Run | Audio (s) | Wall (ms) | RTF | Latency (ms) | Transcript file |
|---|---|---|---|---|---|
| 1 | 10 | | | | |
| 2 | 10 | | | | |
| 3 | 10 | | | | |

### VAD

| Run | Frames | Speech frames | Ratio | Manual label | Error |
|---|---|---|---|---|---|
| 1 | | | | | |
| 2 | | | | | |

### Diarization

| Meeting | Known speakers | Detected | "Me" precision | Notes |
|---|---|---|---|---|
| 1 | | | | |
| 2 | | | | |
| 3 | | | | |

## Failures observed

- <crash, ANR, kill, thermal throttle, model error — with timestamps>

## Decision

- [ ] On-device ASR remains the default (RTF ≤ 0.30, battery ≤ 6 %/h).
- [ ] Switch default ASR to cloud (RTF or battery fails).
- [ ] Ship M3 without automatic person attribution ("Me" precision < 0.90); keep the background bucket.
- [ ] Document OEM workaround in `docs/ROM-MATRIX.md`.

## Next actions

1. Fill the tables on the primary phone (Samsung) and one Chinese ROM.
2. Update `docs/PLAN.md` M0 gate and `docs/DESIGN.md` §14 decisions.
3. Tag `m0-complete` only when every budget has a measured value.
