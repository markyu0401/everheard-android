# M0 spike report — 2026-11-09

**Status:** pending device measurements. The harnesses and the in-app spike screens are implemented; the numbers below must be filled on real hardware.

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
