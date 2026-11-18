# Setup

The app needs two sets of files that are **not** stored in git because they are too
large for GitHub (several are over GitHub's 100 MB per-file limit):

1. The speech **models** (go into `app/src/main/assets/`): English STT (Moonshine) and TTS
   (Piper), Hindi STT (AI4Bharat IndicConformer, in `hi-stt-ic/`) and Hindi TTS (Piper).
2. The sherpa-onnx **native libraries** (go into `app/src/main/jniLibs/arm64-v8a/`).

The download is about 565 MB in total. Everything comes from the official sherpa-onnx GitHub
releases, except the Hindi STT model: sherpa-onnx does not publish IndicConformer, so the
script fetches a community sherpa-onnx export of it from Hugging Face
(`parismitaglobalsolutions/indicconformer-sherpa-onnx`), pinned to a fixed commit and
checked by SHA-256.

If a model file is missing, the app shows "Failed to load … models" for that language
instead of crashing.

## After cloning, download them

**Windows (PowerShell):**
```powershell
powershell -ExecutionPolicy Bypass -File scripts\download-models.ps1
```

**Linux / macOS:**
```bash
bash scripts/download-models.sh
```

The script places every file in the correct folder. It needs internet only for this
one-time step — the built app itself runs fully offline.

## Then build

Open the project in Android Studio, let Gradle sync, plug in an arm64 Android phone
(Android 7.0 / API 24 or newer) with USB debugging on, and press Run.
