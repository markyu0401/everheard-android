#!/usr/bin/env bash
set -euo pipefail

# Battery measurement harness for M0.
# Usage: scripts/battery_probe.sh <adb-serial> <minutes>
# Refuses to run while charging or with the screen on; writes a CSV and a
# summary. Run three times and use the median.

SERIAL="${1:-}"
MINUTES="${2:-30}"

if [[ -z "$SERIAL" ]]; then
  echo "usage: $0 <adb-serial> <minutes>" >&2
  exit 1
fi

if ! [[ "$MINUTES" =~ ^[0-9]+$ ]]; then
  echo "minutes must be an integer" >&2
  exit 1
fi

ADB=(adb -s "$SERIAL")

if ! "${ADB[@]}" get-state >/dev/null 2>&1; then
  echo "device $SERIAL is not available" >&2
  exit 1
fi

battery="$("${ADB[@]}" shell dumpsys battery)"

if grep -qE 'AC powered: true|USB powered: true|Wireless powered: true|Dock powered: true' <<<"$battery"; then
  echo "refusing: device is charging; unplug it first" >&2
  exit 1
fi

wakefulness="$("${ADB[@]}" shell dumpsys power | grep -m1 'mWakefulness=' || true)"
if [[ "$wakefulness" != *"Asleep"* && "$wakefulness" != *"Dozing"* ]]; then
  echo "refusing: screen is on ($wakefulness); turn it off first" >&2
  exit 1
fi

mkdir -p spike-results
OUT="spike-results/battery-${SERIAL}-$(date +%Y%m%d-%H%M%S).csv"
echo "timestamp,level,status,thermal" > "$OUT"

read_level() {
  "${ADB[@]}" shell dumpsys battery | awk '/level:/{print $2; exit}'
}

read_status() {
  "${ADB[@]}" shell dumpsys battery | awk '/status:/{print $2; exit}'
}

read_thermal() {
  "${ADB[@]}" shell dumpsys thermalservice |
    grep -m1 -E 'Thermal Status|mStatus' |
    sed -E 's/.*[:=][[:space:]]*//' |
    tr -d '\r' || echo "unknown"
}

for ((minute = 0; minute <= MINUTES; minute++)); do
  level="$(read_level)"
  status="$(read_status)"
  thermal="$(read_thermal)"
  printf '%s,%s,%s,%s\n' "$(date +%s)" "$level" "$status" "$thermal" >> "$OUT"

  if ((minute < MINUTES)); then
    sleep 60
  fi
done

awk -F, '
  NR == 2 { first_level = $2; first_ts = $1 }
  END {
    if (NR < 3) { print "not enough samples"; exit 0 }
    last_level = $2; last_ts = $1
    hours = (last_ts - first_ts) / 3600.0
    if (hours <= 0) { print "zero duration"; exit 0 }
    drain = (first_level - last_level) / hours
    printf "samples=%d duration_h=%.3f start=%d%% end=%d%% drain=%.2f%%/h\n", NR - 1, hours, first_level, last_level, drain
  }
' "$OUT"

echo "csv: $OUT"
