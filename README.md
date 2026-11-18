# iTantra — Indian Multilingual TTS & STT Aided Neural Transceiver Radio

iTantra lets one person **speak** into a phone and another person **hear** that speech on
a different phone — but across the link, only **text** travels, never audio. Text is tiny,
so the message gets through even on very weak radio links where sending audio would fail.
It works in Indian languages, **fully offline** (no internet), on low and mid-range phones.

This is a submission for the Smart India Hackathon (SIH) problem statement of the same name.

## The idea

```
Person A speaks -> [Speech-to-Text] -> text -> sent over WiFi / Bluetooth / LoRa
                                                   -> [Text-to-Speech] -> Person B hears it
```

A spoken sentence is ~80 bytes as text but ~200 KB as audio — roughly 2500x smaller.
That size difference is why iTantra survives links that cannot carry voice.

## Languages

Target: Hindi, Gujarati, Marathi, Kannada, Malayalam, Tamil, Telugu, Odia, Bengali, English.
Working today: **English** and **Hindi**.

## Key constraints

- Fully offline — AI models run on the phone, no cloud APIs.
- Open-source models only.
- Runs on low and mid-range Android phones.

## Documentation

Plain-English project notes live in [`iTantra/docs/learning/`](iTantra/docs/learning/README.md).
Start there to understand the project module by module.

## Build

See [`iTantra/docs/SETUP.md`](iTantra/docs/SETUP.md). After cloning, run the model-download
script, then open `iTantra/` in Android Studio and Run on an arm64 phone (Android 7.0+).

## Status

- [x] Offline speech-to-text with push-to-talk, on-device (English: Moonshine, Hindi: AI4Bharat IndicConformer)
- [x] Offline text-to-speech playback (Piper voices, English + Hindi)
- [x] Alert messages (maximum volume)
- [x] Two-phone transport over WiFi (channel-based UDP broadcast, live roster/radar)
- [x] Benchmark screen (word error rate + real-time factor)
- [ ] Bluetooth transport
- [ ] Phone mode (automatic pause detection with VAD)
- [ ] The other 8 Indian languages
- [ ] Long-range LoRa transport
