# Setup

The app needs two sets of files that are **not** stored in git because they are too
large for GitHub (the model alone is ~270 MB, over GitHub's 100 MB per-file limit):

1. The speech-to-text **model** files (go into `app/src/main/assets/`).
2. The sherpa-onnx **native libraries** (go into `app/src/main/jniLibs/arm64-v8a/`).

## After cloning, download them

**Windows (PowerShell):**
```powershell
powershell -ExecutionPolicy Bypass -File scripts\download-models.ps1
```

**Linux / macOS:**
```bash
bash scripts/download-models.sh
```

The script downloads from the official sherpa-onnx GitHub releases and places every
file in the correct folder. It needs internet only for this one-time step — the built
app itself runs fully offline.

## Then build

Open the project in Android Studio, let Gradle sync, plug in an arm64 Android phone
(Android 7.0 / API 24 or newer) with USB debugging on, and press Run.
