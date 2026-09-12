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
HI_STT_URL='https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-whisper-small.tar.bz2'
HI_TTS_URL='https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-hi_IN-rohan-medium-int8.tar.bz2'
echo 'Downloading Hindi STT (Whisper small, ~610 MB)...'
curl -L "$HI_STT_URL" -o "$TMP/hi-stt.tar.bz2"
echo 'Downloading Hindi TTS (Piper, ~20 MB)...'
curl -L "$HI_TTS_URL" -o "$TMP/hi-tts.tar.bz2"
tar -xjf "$TMP/hi-stt.tar.bz2" -C "$TMP"
tar -xjf "$TMP/hi-tts.tar.bz2" -C "$TMP"

mkdir -p "$ASSETS/hi-stt"
DS="$TMP/sherpa-onnx-whisper-small"
cp "$DS/small-encoder.int8.onnx" "$ASSETS/hi-stt/"
cp "$DS/small-decoder.onnx"      "$ASSETS/hi-stt/"   # float decoder (int8 degenerates)
cp "$DS/small-tokens.txt"        "$ASSETS/hi-stt/"

mkdir -p "$ASSETS/hi-tts"
HV="$TMP/vits-piper-hi_IN-rohan-medium-int8"
cp "$HV/hi_IN-rohan-medium.onnx" "$ASSETS/hi-tts/"
cp "$HV/tokens.txt"              "$ASSETS/hi-tts/"

rm -rf "$TMP"
echo 'Done. Models and native libraries are in place.'
