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
MODEL_URL='https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-moonshine-base-en-int8.tar.bz2'

echo 'Downloading sherpa-onnx native libraries (~43 MB)...'
curl -L "$LIBS_URL"  -o "$TMP/libs.tar.bz2"
echo 'Downloading Moonshine base-en model (~239 MB)...'
curl -L "$MODEL_URL" -o "$TMP/model.tar.bz2"

echo 'Extracting...'
tar -xjf "$TMP/libs.tar.bz2"  -C "$TMP"
tar -xjf "$TMP/model.tar.bz2" -C "$TMP"

cp "$TMP/jniLibs/arm64-v8a/libonnxruntime.so"     "$JNILIBS/"
cp "$TMP/jniLibs/arm64-v8a/libsherpa-onnx-jni.so" "$JNILIBS/"

MD="$TMP/sherpa-onnx-moonshine-base-en-int8"
cp "$MD/preprocess.onnx"           "$ASSETS/"
cp "$MD/encode.int8.onnx"          "$ASSETS/"
cp "$MD/uncached_decode.int8.onnx" "$ASSETS/"
cp "$MD/cached_decode.int8.onnx"   "$ASSETS/"
cp "$MD/tokens.txt"                "$ASSETS/"

rm -rf "$TMP"
echo 'Done. Models and native libraries are in place.'
