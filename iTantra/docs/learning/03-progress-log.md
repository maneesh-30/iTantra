# 03 — Progress Log

A dated diary of everything we have done, in plain language. Newest entries at the bottom. Each entry says *what* we did and *why it mattered*.

---

## 2026-09-07 — Understanding the problem

- Read and broke down the SIH problem statement in plain English (see [00-what-is-itantra.md](00-what-is-itantra.md)).
- Confirmed the core idea: send **text**, not audio, across the weak link.
- Confirmed the rules: fully offline, open-source only, must run on cheap phones, 10 Indian languages.
- Decided the paid subscription (featherless.ai) is useless here — it is a cloud API, which the rules ban.

## 2026-09-07 — Choosing the tech stack

- **App:** native Android in Kotlin, built with Android Studio.
- **Speech engine:** sherpa-onnx — one open-source library that does both STT and TTS, offline, and has ready-made Android examples.
- **Language models:** AI4Bharat (Indian-language STT/TTS) plus built-in English.
- **Transport:** a swappable interface — WiFi socket for the demo, LoRa/Meshtastic for real long range.
- Set the realistic target: a working walkie-talkie loop with a few languages solid, honest metrics, and an architecture that clearly extends to all 10 languages and to LoRa.

## 2026-09-08 — Setting up the machine

- Set up Git and pushed the repo to GitHub (`maneesh-30/iTantra`).
- Freed about 37 GB of disk space (the laptop was almost full) by clearing safe caches and unused files.
- Confirmed Android Studio, the Android SDK, the NDK, and cmake were already installed. The NDK matters because sherpa-onnx uses C++ code.
- Connected the test phone (Redmi Note 11, a mid-range device — a good real-world target) to the laptop with `adb`.

## 2026-09-08 — Proving the engines work on the phone

Before building anything custom, we tested that the speech engines actually run on the real phone.

- **STT test:** installed sherpa-onnx's prebuilt English speech-to-text sample app. Spoke into the phone → it transcribed the words live, **fully offline** (worked in airplane mode). ✅
  - Observation: the sample transcribes background noise too, and invents sentences from noise. This is expected (the mic is always on and the model hallucinates on noise). We will fix it in our app using push-to-talk (mic off unless the button is held) plus VAD tuning and noise suppression.
- **TTS test:** installed sherpa-onnx's prebuilt Hindi text-to-speech sample. Typed Hindi text → the phone spoke it aloud, offline. ✅

**Why this mattered:** it removed our two biggest technical risks. We now know both halves of the loop run on the actual hardware, offline, at no cost.

## 2026-09-08 — Starting our own app (Milestone M1)

- Created a fresh Android Studio project: name `iTantra`, package `com.itantra.app`, language Kotlin, minimum Android version API 24 (so it runs on old, cheap phones).
- Gradle build succeeded.
- Ran the empty "Hello World" app on the Redmi — toolchain confirmed: we can build and install our own app on the phone.

## 2026-09-08 — Speech-to-text working in our app (Milestone M2)

- Integrated the sherpa-onnx engine into our app: native libraries (`jniLibs/`), the Kotlin
  API bindings, and a bundled English model in `assets/`.
- First attempt used a **streaming** recognizer. Two problems showed up:
  1. Garbled output ("0", numbers) — caused by using the int8 decoder; fixed by using the float decoder.
  2. Low accuracy, and it transcribed background noise.
- Root cause of the accuracy problem: a small **streaming** model sees little context. The problem
  statement actually asks for sentence-level transcription after a pause, not live streaming.
- **Corrected approach:** switched to a full-clip **offline** recognizer (Moonshine base-en) triggered
  by **push-to-talk** — hold the button, speak a sentence, release, and the whole clip is transcribed.
  This is more accurate (full context) and matches the walkie-talkie mode in the problem statement.
  It also removes the noise problem, because the microphone is only on while the button is held.
- Result: accurate English speech-to-text, fully offline, on the Redmi. M2 done.
- Note on model delivery: model and native-library files are large, so they are not stored in git;
  `scripts/download-models.*` fetches them. See [../SETUP.md](../SETUP.md).

---

## The milestone plan

| Milestone | Goal |
|-----------|------|
| M1 | Empty app builds and runs on the phone. |
| M2 | sherpa-onnx integrated; STT works inside our app. |
| M3 | TTS works inside our app (one phone: speak → text → speak). |
| M4 | Push-to-talk button + VAD tuning (fixes the noise problem). |
| M5 | WiFi transport: two phones, full walkie-talkie loop. |
| M6 | More languages, alert mode, and measuring our metrics. |
| M7 | LoRa transport, polish, and demo preparation. |
