#!/usr/bin/env bash
set -euo pipefail

# Downloads the small models that are not committed. The ASR model is added
# here when M0-T6 lands; Silero VAD is committed because it is 629 KB and MIT.

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
VAD_DEST="$ROOT/app/src/main/assets/models/silero_vad.onnx"
VAD_URL="https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/silero_vad.onnx"
VAD_SHA256="9e2449e1087496d8d4caba907f23e0bd3f78d91fa552479bb9c23ac09cbb1fd6"

verify_or_download() {
  local dest="$1" url="$2" sha="$3"
  if [[ -f "$dest" ]] && echo "$sha  $dest" | shasum -a 256 -c - >/dev/null 2>&1; then
    echo "present: $dest"
    return 0
  fi
  mkdir -p "$(dirname "$dest")"
  echo "Downloading $(basename "$dest")..."
  curl -L --fail --retry 3 -o "$dest" "$url"
  echo "$sha  $dest" | shasum -a 256 -c -
}

verify_or_download "$VAD_DEST" "$VAD_URL" "$VAD_SHA256"

# Streaming Zipformer small bilingual zh-en, int8 (M0 default).
# The tarball contains fp32 and int8; only int8 is copied into assets.
ASR_DIR="$ROOT/app/src/main/assets/models/asr-small"
ASR_URL="https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-streaming-zipformer-small-bilingual-zh-en-2023-02-16.tar.bz2"
ASR_SHA256="2b7c63322b32e5e0f2526043a1103366119ca58dd615cd7105a37c01db9553d7"

if [[ -f "$ASR_DIR/encoder.int8.onnx" ]]; then
  echo "present: $ASR_DIR/encoder.int8.onnx"
else
  tmp_dir="$(mktemp -d)"
  trap 'rm -rf "$tmp_dir"' EXIT
  echo "Downloading streaming Zipformer small model (437 MB)..."
  curl -L --fail --retry 3 -o "$tmp_dir/asr.tar.bz2" "$ASR_URL"
  echo "$ASR_SHA256  $tmp_dir/asr.tar.bz2" | shasum -a 256 -c -
  mkdir -p "$tmp_dir/extracted"
  tar -xjf "$tmp_dir/asr.tar.bz2" -C "$tmp_dir/extracted" --strip-components=1
  mkdir -p "$ASR_DIR"
  cp "$tmp_dir/extracted/encoder-epoch-99-avg-1.int8.onnx" "$ASR_DIR/encoder.int8.onnx"
  cp "$tmp_dir/extracted/decoder-epoch-99-avg-1.int8.onnx" "$ASR_DIR/decoder.int8.onnx"
  cp "$tmp_dir/extracted/joiner-epoch-99-avg-1.int8.onnx" "$ASR_DIR/joiner.int8.onnx"
  cp "$tmp_dir/extracted/tokens.txt" "$ASR_DIR/tokens.txt"
  cp "$tmp_dir/extracted/bpe.model" "$ASR_DIR/bpe.model"
  echo "installed ASR model into $ASR_DIR"
fi
