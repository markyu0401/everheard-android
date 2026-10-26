#!/usr/bin/env bash
set -euo pipefail

# Thermal sampler for M0.
# Usage: scripts/thermal_probe.sh <adb-serial> <minutes>
# Writes a timestamped dump of thermalservice and battery every 10 s.

SERIAL="${1:-}"
MINUTES="${2:-30}"

if [[ -z "$SERIAL" ]]; then
  echo "usage: $0 <adb-serial> <minutes>" >&2
  exit 1
fi

ADB=(adb -s "$SERIAL")
mkdir -p spike-results
OUT="spike-results/thermal-${SERIAL}-$(date +%Y%m%d-%H%M%S).log"

for ((elapsed = 0; elapsed <= MINUTES * 60; elapsed += 10)); do
  {
    echo "=== $(date -u +%Y-%m-%dT%H:%M:%SZ) ==="
    "${ADB[@]}" shell dumpsys thermalservice | grep -E 'Thermal Status|Temperature|mStatus' | head -20
    "${ADB[@]}" shell dumpsys battery | grep -E 'level:|status:|temperature:' | head -5
  } >> "$OUT"

  if ((elapsed < MINUTES * 60)); then
    sleep 10
  fi
done

echo "log: $OUT"
