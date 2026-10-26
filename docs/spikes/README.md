# Spike reports

Each spike produces raw numbers, not impressions. Use this template and keep the file named `YYYY-MM-DD-<topic>.md`.

## Template

```markdown
# <Topic> spike — <date>

**Device / ROM:** <model, Android version, build>
**Build:** <git sha, debug/release>
**Conditions:** screen off, 50 % brightness, unplugged, <network>

## What was measured

| Metric | Run 1 | Run 2 | Run 3 | Median | Budget | Pass? |
|---|---|---|---|---|---|---|
| RTF | | | | | ≤ 0.30 | |
| Live latency (s) | | | | | ≤ 1.5 | |
| Battery armed+VAD (%/h) | | | | | ≤ 6 | |
| Battery armed+paused (%/h) | | | | | ≤ 1 | |
| "Me" precision | | | | | ≥ 0.90 | |
| Speaker-count error | | | | | ≤ ±1 | |

## Method

<commands, fixtures, manual labels, anything a reviewer needs to reproduce>

## Failures observed

<crash, ANR, kill, thermal throttle, model error — with timestamps>

## Decision

<go / no-go / switch default; link the DESIGN.md decision>
```

## Rules

- One probe is noise; three runs, median.
- Never report a battery number taken while charging or with the screen on.
- Keep raw CSVs in `spike-results/` (gitignored) and attach the summary table here.
- If a measurement fails the budget, that is the result; do not round it into a pass.
