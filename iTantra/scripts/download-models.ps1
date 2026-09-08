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
$modelUrl = 'https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-moonshine-base-en-int8.tar.bz2'

Write-Host 'Downloading sherpa-onnx native libraries (~43 MB)...'
Invoke-WebRequest -Uri $libsUrl -OutFile "$tmp\libs.tar.bz2" -UseBasicParsing
Write-Host 'Downloading Moonshine base-en model (~239 MB)...'
Invoke-WebRequest -Uri $modelUrl -OutFile "$tmp\model.tar.bz2" -UseBasicParsing

Write-Host 'Extracting...'
tar -xjf "$tmp\libs.tar.bz2"  -C $tmp
tar -xjf "$tmp\model.tar.bz2" -C $tmp

Copy-Item "$tmp\jniLibs\arm64-v8a\libonnxruntime.so"       $jniLibs -Force
Copy-Item "$tmp\jniLibs\arm64-v8a\libsherpa-onnx-jni.so"   $jniLibs -Force

$md = "$tmp\sherpa-onnx-moonshine-base-en-int8"
Copy-Item "$md\preprocess.onnx"          $assets -Force
Copy-Item "$md\encode.int8.onnx"         $assets -Force
Copy-Item "$md\uncached_decode.int8.onnx" $assets -Force
Copy-Item "$md\cached_decode.int8.onnx"  $assets -Force
Copy-Item "$md\tokens.txt"               $assets -Force

Write-Host 'Done. Models and native libraries are in place.'
