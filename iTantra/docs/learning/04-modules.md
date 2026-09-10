# 04 — Modules (the code, explained)

This file documents each part of the code as we build it: **what** it does, **why** it exists, and **how** it works. It grows over time. Right now the app is an empty starter, so this file lists the planned modules and fills in as we write them.

> How to use this file: whenever we add a new code file or feature, we add a section here in plain language, so you can explain any part of the app to anyone.

---

## Planned modules

These map to the parts in [02-architecture.md](02-architecture.md).

### AudioCapture (planned — M2)
- **What:** reads raw sound from the microphone.
- **Why:** every other speech step needs microphone audio to work on.
- **How (planned):** uses Android's `AudioRecord` to read 16 kHz mono audio into small buffers.

### Vad (planned — M4)
- **What:** decides which audio is speech, and detects the pause that ends a sentence.
- **Why:** stops noise and silence from reaching the STT model, and tells us when a sentence is complete.
- **How (planned):** a small VAD model (Silero) via sherpa-onnx, with tunable thresholds for sensitivity and pause length.

### SttEngine (planned — M2)
- **What:** converts speech audio into text.
- **Why:** this is the "speech-to-text" half of the product.
- **How (planned):** sherpa-onnx loads a language model (ONNX format) and returns the recognized text.

### Transport (planned — M5)
- **What:** sends text to, and receives text from, the other device.
- **Why:** this is the link between two phones; it is deliberately swappable (WiFi now, LoRa later).
- **How (planned):** a Kotlin `interface` with `sendText()` and `onTextReceived()`. First implementation is `WifiTransport` using a TCP socket.

### TtsEngine (planned — M3)
- **What:** converts received text into audio.
- **Why:** the "text-to-speech" half — lets the other person hear the message.
- **How (planned):** sherpa-onnx loads a voice model (VITS/Piper) and produces audio samples.

### AudioPlayback (planned — M3)
- **What:** plays audio through the speaker, with a special loud, non-interruptible mode for alerts.
- **Why:** the receiver must hear the message; alerts must not be missed.
- **How (planned):** Android's `AudioTrack`, with the volume forced to maximum for alert messages.

### UI / MainActivity (exists — M1)
- **What:** the app screen.
- **Why:** where the user sees messages and controls the app.
- **How (now):** currently the default "Hello World" screen from the project template. Will grow to include the push-to-talk button, a mode switch (PTT vs phone), a message list, and a language picker.

---

## Actual modules (filled in as we build)

### SttEngine (built — M2)
- **File:** `app/src/main/java/com/itantra/app/SttEngine.kt`
- **What:** loads the offline Moonshine English model and transcribes a whole recorded clip.
- **Why offline/full-clip:** it sees the entire sentence at once, so it is far more accurate than a
  streaming model, and it matches the problem statement ("after detecting pauses... form the sentences").
- **How:** wraps sherpa-onnx's `OfflineRecognizer`. `transcribe(samples)` creates a stream, feeds the
  audio, decodes, and returns the text. Model files are read from `assets/`, so it runs with no internet.

### MainActivity (built — M2)
- **File:** `app/src/main/java/com/itantra/app/MainActivity.kt`
- **What:** the screen and the push-to-talk recording logic.
- **Why:** gives the user a simple way to record and see the transcript; PTT also keeps the mic off
  except while the button is held, which removes background-noise pickup.
- **How:** loads the model on a background thread; while the button is held, `AudioRecord` captures
  16 kHz mono audio into memory; on release, the clip is sent to `SttEngine.transcribe()` and the
  result is appended to the on-screen transcript.

### sherpa-onnx bindings (added — M2)
- **Files:** `app/src/main/java/com/k2fsa/sherpa/onnx/*.kt`
- **What:** the official sherpa-onnx Kotlin API. We call into these; we did not modify them.
- **Why:** they bridge our Kotlin code to the sherpa-onnx C++ engine (the `.so` native libraries).

### TtsEngine (built — M3)
- **File:** `app/src/main/java/com/itantra/app/TtsEngine.kt`
- **What:** turns text into speech audio offline, using a Piper VITS English voice via sherpa-onnx.
- **How:** on first run it copies the `espeak-ng-data` folder from assets to app storage (Piper needs
  it on the real filesystem), then `synthesize(text)` returns audio samples.

### AudioPlayback (built — M3)
- **File:** `app/src/main/java/com/itantra/app/AudioPlayback.kt`
- **What:** plays audio samples through the speaker with `AudioTrack`.
- **Why:** the listener must hear the message; alerts must be unmissable.
- **How:** waits for the audio to finish before stopping. For alerts it forces maximum volume and
  peak-normalizes the samples so they are clearly louder.

### Transport + WifiTransport (built — M5)
- **Files:** `Transport.kt` (interface), `WifiTransport.kt` (implementation)
- **What:** carries text between phones. `Transport` is a small swappable interface
  (`send` / `onMessage`); `WifiTransport` implements it over the local WiFi network.
- **Why swappable:** WiFi is only for the demo. A LoRa implementation (long range) will plug into the
  same interface later without changing the rest of the app.
- **How:** channel-based like a walkie-talkie — same channel name = same group. Uses UDP broadcast so
  one phone reaches everyone on the channel, and sends to each interface's directed broadcast so it
  works over a phone hotspot too. Holds a WiFi lock so it keeps receiving while idle.

*(Vad for phone mode, and a LoRa transport, arrive in later milestones.)*
