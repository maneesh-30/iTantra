#!/usr/bin/env bash
# Downloads the STT model + sherpa-onnx native libraries for iTantra (Linux / macOS).
# These files are NOT stored in git (too large for GitHub). Run this once after cloning.
#
#   bash scripts/download-models.sh
#
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
ASSETS="$ROOT/app/src/main/assets"
JNILIBS="$ROOT/app/src/main/jniLibs/arm64-v8a"
TMP="$(mktemp -d)"

mkdir -p "$ASSETS" "$JNILIBS"

LIBS_URL='https://github.com/k2-fsa/sherpa-onnx/releases/download/v1.13.7/sherpa-onnx-v1.13.7-android.tar.bz2'
STT_URL='https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-moonshine-base-en-int8.tar.bz2'
TTS_URL='https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-en_US-lessac-medium.tar.bz2'

echo 'Downloading sherpa-onnx native libraries (~43 MB)...'
curl -L "$LIBS_URL" -o "$TMP/libs.tar.bz2"
echo 'Downloading Moonshine STT model (~239 MB)...'
curl -L "$STT_URL"  -o "$TMP/stt.tar.bz2"
echo 'Downloading English TTS voice (~64 MB)...'
curl -L "$TTS_URL"  -o "$TMP/tts.tar.bz2"

echo 'Extracting...'
tar -xjf "$TMP/libs.tar.bz2" -C "$TMP"
tar -xjf "$TMP/stt.tar.bz2"  -C "$TMP"
tar -xjf "$TMP/tts.tar.bz2"  -C "$TMP"

cp "$TMP/jniLibs/arm64-v8a/libonnxruntime.so"     "$JNILIBS/"
cp "$TMP/jniLibs/arm64-v8a/libsherpa-onnx-jni.so" "$JNILIBS/"

# STT model (flat, in assets/)
MD="$TMP/sherpa-onnx-moonshine-base-en-int8"
cp "$MD/preprocess.onnx"           "$ASSETS/"
cp "$MD/encode.int8.onnx"          "$ASSETS/"
cp "$MD/uncached_decode.int8.onnx" "$ASSETS/"
cp "$MD/cached_decode.int8.onnx"   "$ASSETS/"
cp "$MD/tokens.txt"                "$ASSETS/"

# TTS voice (in assets/tts/)
mkdir -p "$ASSETS/tts"
VD="$TMP/vits-piper-en_US-lessac-medium"
cp "$VD/en_US-lessac-medium.onnx" "$ASSETS/tts/"
cp "$VD/tokens.txt"               "$ASSETS/tts/"
cp -r "$VD/espeak-ng-data"        "$ASSETS/tts/"

# --- Hindi models ---
# Hindi STT is AI4Bharat IndicConformer (NeMo CTC, int8), loaded by SttEngine from assets/hi-stt-ic/.
# sherpa-onnx does not publish it, so we use a community sherpa-onnx export, pinned to one
# commit and checked by SHA-256 (tokens.txt is the vocabulary shared by all Indian languages).
IC_BASE='https://huggingface.co/parismitaglobalsolutions/indicconformer-sherpa-onnx/resolve/9721eb71eea141fae0982cfcdb9dd2e3d4953c4a'
HI_TTS_URL='https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-hi_IN-rohan-medium-int8.tar.bz2'

sha256() { if command -v sha256sum >/dev/null; then sha256sum "$1"; else shasum -a 256 "$1"; fi | cut -d' ' -f1; }
fetch_checked() {  # url, destination, expected sha256
  curl -fL "$1" -o "$2"
  [ "$(sha256 "$2")" = "$3" ] || { echo "Checksum mismatch for $1" >&2; rm -f "$2"; exit 1; }
}

mkdir -p "$ASSETS/hi-stt-ic"
echo 'Downloading Hindi STT (IndicConformer int8, ~198 MB)...'
fetch_checked "$IC_BASE/hi/model.int8.onnx" "$ASSETS/hi-stt-ic/model.int8.onnx" 915c71e04dd7e5378a4057fdebb252b3a587188e4e99db6d7ce0909ad5ad05fa
fetch_checked "$IC_BASE/tokens.txt"         "$ASSETS/hi-stt-ic/tokens.txt"      ee60967630213f31951817ac8b402b92ec18cce80718a24a49b388e56672dfb2

echo 'Downloading Hindi TTS (Piper, ~20 MB)...'
curl -L "$HI_TTS_URL" -o "$TMP/hi-tts.tar.bz2"
tar -xjf "$TMP/hi-tts.tar.bz2" -C "$TMP"

mkdir -p "$ASSETS/hi-tts"
HV="$TMP/vits-piper-hi_IN-rohan-medium-int8"
cp "$HV/hi_IN-rohan-medium.onnx" "$ASSETS/hi-tts/"
cp "$HV/tokens.txt"              "$ASSETS/hi-tts/"

rm -rf "$TMP"
echo 'Done. Models and native libraries are in place.'
