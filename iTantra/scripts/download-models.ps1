# Downloads the STT model + sherpa-onnx native libraries for iTantra (Windows / PowerShell).
# These files are NOT stored in git (too large for GitHub). Run this once after cloning.
#
#   powershell -ExecutionPolicy Bypass -File scripts\download-models.ps1
#
$ErrorActionPreference = 'Stop'

$root     = Split-Path -Parent $PSScriptRoot
$assets   = Join-Path $root 'app\src\main\assets'
$jniLibs  = Join-Path $root 'app\src\main\jniLibs\arm64-v8a'
$tmp      = Join-Path $env:TEMP 'itantra-models'

New-Item -ItemType Directory -Force -Path $assets, $jniLibs, $tmp | Out-Null

$libsUrl  = 'https://github.com/k2-fsa/sherpa-onnx/releases/download/v1.13.7/sherpa-onnx-v1.13.7-android.tar.bz2'
$sttUrl   = 'https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-moonshine-base-en-int8.tar.bz2'
$ttsUrl   = 'https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-en_US-lessac-medium.tar.bz2'

Write-Host 'Downloading sherpa-onnx native libraries (~43 MB)...'
Invoke-WebRequest -Uri $libsUrl -OutFile "$tmp\libs.tar.bz2" -UseBasicParsing
Write-Host 'Downloading Moonshine STT model (~239 MB)...'
Invoke-WebRequest -Uri $sttUrl -OutFile "$tmp\stt.tar.bz2" -UseBasicParsing
Write-Host 'Downloading English TTS voice (~64 MB)...'
Invoke-WebRequest -Uri $ttsUrl -OutFile "$tmp\tts.tar.bz2" -UseBasicParsing

Write-Host 'Extracting...'
tar -xjf "$tmp\libs.tar.bz2" -C $tmp
tar -xjf "$tmp\stt.tar.bz2"  -C $tmp
tar -xjf "$tmp\tts.tar.bz2"  -C $tmp

Copy-Item "$tmp\jniLibs\arm64-v8a\libonnxruntime.so"       $jniLibs -Force
Copy-Item "$tmp\jniLibs\arm64-v8a\libsherpa-onnx-jni.so"   $jniLibs -Force

# STT model (flat, in assets/)
$md = "$tmp\sherpa-onnx-moonshine-base-en-int8"
Copy-Item "$md\preprocess.onnx"          $assets -Force
Copy-Item "$md\encode.int8.onnx"         $assets -Force
Copy-Item "$md\uncached_decode.int8.onnx" $assets -Force
Copy-Item "$md\cached_decode.int8.onnx"  $assets -Force
Copy-Item "$md\tokens.txt"               $assets -Force

# TTS voice (in assets/tts/)
$ttsDir = Join-Path $assets 'tts'
New-Item -ItemType Directory -Force -Path $ttsDir | Out-Null
$vd = "$tmp\vits-piper-en_US-lessac-medium"
Copy-Item "$vd\en_US-lessac-medium.onnx" $ttsDir -Force
Copy-Item "$vd\tokens.txt"               $ttsDir -Force
Copy-Item "$vd\espeak-ng-data"           $ttsDir -Recurse -Force

# --- Hindi models ---
$hiSttUrl = 'https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-whisper-base.tar.bz2'
$hiTtsUrl = 'https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-hi_IN-rohan-medium-int8.tar.bz2'
Write-Host 'Downloading Hindi STT (Whisper base, ~198 MB)...'
Invoke-WebRequest -Uri $hiSttUrl -OutFile "$tmp\hi-stt.tar.bz2" -UseBasicParsing
Write-Host 'Downloading Hindi TTS (Piper, ~20 MB)...'
Invoke-WebRequest -Uri $hiTtsUrl -OutFile "$tmp\hi-tts.tar.bz2" -UseBasicParsing
tar -xjf "$tmp\hi-stt.tar.bz2" -C $tmp
tar -xjf "$tmp\hi-tts.tar.bz2" -C $tmp

$hiSttDir = Join-Path $assets 'hi-stt'
New-Item -ItemType Directory -Force -Path $hiSttDir | Out-Null
$ds = "$tmp\sherpa-onnx-whisper-base"
Copy-Item "$ds\base-encoder.int8.onnx" $hiSttDir -Force
Copy-Item "$ds\base-decoder.int8.onnx" $hiSttDir -Force
Copy-Item "$ds\base-tokens.txt"        $hiSttDir -Force

$hiTtsDir = Join-Path $assets 'hi-tts'
New-Item -ItemType Directory -Force -Path $hiTtsDir | Out-Null
$hv = "$tmp\vits-piper-hi_IN-rohan-medium-int8"
Copy-Item "$hv\hi_IN-rohan-medium.onnx" $hiTtsDir -Force
Copy-Item "$hv\tokens.txt"              $hiTtsDir -Force

Write-Host 'Done. Models and native libraries are in place.'
