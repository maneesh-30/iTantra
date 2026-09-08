# 02 — Architecture (the big picture)

This file shows how all the parts fit together. Read [00-what-is-itantra.md](00-what-is-itantra.md) first if you haven't.

## The whole loop, end to end

```
        PHONE A (talking)                                   PHONE B (listening)
   ┌───────────────────────┐                          ┌───────────────────────┐
   │  🎤 Microphone         │                          │  🔊 Speaker            │
   │        ↓               │                          │        ↑               │
   │  VAD (is this speech?) │                          │  TTS (text → speech)   │
   │        ↓               │                          │        ↑               │
   │  STT (speech → text)   │                          │  received text         │
   │        ↓               │                          │        ↑               │
   │  Transport (send text) │──── WiFi / BT / LoRa ────│  Transport (recv text) │
   └───────────────────────┘   only TEXT crosses here  └───────────────────────┘

   Both phones have ALL of these parts. When B talks back, the arrows reverse.
```

Key point: **every phone runs the full app** (STT + TTS + transport). Roles flip constantly — whoever is holding the push-to-talk button is the talker.

## The parts (modules) and what each does

1. **Audio capture** — reads sound from the microphone.
2. **VAD** — decides which chunks of that sound are real speech, filters out noise and silence, and detects the pause that means "sentence finished."
3. **STT engine** — converts the speech chunks into a text sentence (using sherpa-onnx + a language model).
4. **Transport (send)** — takes the text sentence and sends it to the other device.
5. **Transport (receive)** — receives text sentences from the other device.
6. **TTS engine** — converts received text into audio (using sherpa-onnx + a voice model).
7. **Audio playback** — plays that audio through the speaker (louder + non-interruptible for alerts).
8. **UI** — the screen: push-to-talk button, mode switch, message list, language picker.

## The most important design decision: swappable transport

The transport is designed as a **plug** with a simple shape:

```
interface Transport {
    fun sendText(message: String)          // send a sentence to the other side
    fun onTextReceived(handler: (String) -> Unit)  // get sentences from the other side
}
```

The STT and TTS code only know about `sendText()` and `onTextReceived()`. They do **not** know or care what carries the message. This lets us swap the pipe without touching the rest of the app:

| Version of the pipe | Range | When we use it |
|---------------------|-------|----------------|
| `WifiTransport` (TCP socket) | same room / building | the demo, this week |
| `BluetoothTransport` (RFCOMM) | ~10–30 m | alternative demo |
| `LoRaTransport` (Meshtastic) | 2–15 km | real long-range deployment |

Because the payload is just text, the same tiny message works on all three. This is the answer to "how do we reach kilometers?" — we change only the transport plug.

## Why text-only is the whole point

A spoken sentence:
- as **text** ≈ 80 bytes
- as **audio** ≈ 200,000 bytes (200 KB)

That is roughly **2,500× smaller**. A LoRa radio can send 80 bytes over kilometers but can never send 200 KB of audio. This size difference is the core reason iTantra works on weak links — and it is the number we show the judges.

## The two modes

- **Push-to-talk (PTT):** microphone is OFF until you hold the button. Hold → talk → release → the sentence is sent. Only one person talks at a time. This also blocks background noise (mic is off when you are not talking).
- **Phone mode:** microphone stays on for both sides; both can talk at once, like a normal call.

## Alert messages

Some messages are marked "alert." On the receiving phone they:
- play at **maximum volume**,
- **cannot be interrupted or skipped**,
until they finish. For emergencies.
