#!/usr/bin/env bash
set -euo pipefail

# Downloads the sherpa-onnx Android AAR used by the capture and ASR spikes.
# The AAR is not committed; run this once per checkout (CI runs it too).

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DEST="$ROOT/app/libs/sherpa-onnx-2.0.0.aar"
URL="https://github.com/k2-fsa/sherpa-onnx/releases/download/v2.0.0/sherpa-onnx-2.0.0.aar"
SHA256="f0f8716982d27cb4ce695e3934018b1342d9d3d6fdb6ef1f1658fe8bad50b4fc"

if [[ -f "$DEST" ]] && echo "$SHA256  $DEST" | shasum -a 256 -c - >/dev/null 2>&1; then
  echo "sherpa-onnx AAR already present and verified"
  exit 0
fi

mkdir -p "$(dirname "$DEST")"
echo "Downloading sherpa-onnx 2.0.0 AAR..."
curl -L --fail --retry 3 -o "$DEST" "$URL"
echo "$SHA256  $DEST" | shasum -a 256 -c -
