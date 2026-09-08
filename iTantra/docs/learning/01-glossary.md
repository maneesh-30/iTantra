# 01 — Glossary (plain-English)

Every technical term used in this project, explained simply.

## Speech and AI terms

**STT — Speech-to-Text** (also called ASR, Automatic Speech Recognition)
Turns spoken audio into written text. You talk, it types what you said.

**TTS — Text-to-Speech**
The reverse. Turns written text into spoken audio. You give it text, it reads it aloud.

**Model**
A trained AI "brain" saved as a file. There is one model per language for STT, and one per voice for TTS. We download these files and run them on the phone. No internet needed.

**VAD — Voice Activity Detection**
A small piece of AI that answers one question: *"Is this sound speech, or just noise/silence?"* We use it to know when you start and stop talking, so we only send real speech to the STT model and ignore background noise.

**WER — Word Error Rate**
A score for STT accuracy: what fraction of words it got wrong. Lower is better. 5% WER means 5 out of 100 words were wrong.

**RTF — Real Time Factor**
A speed score: processing time ÷ audio length. If 1 second of audio takes 0.5 seconds to process, RTF = 0.5 (faster than real-time, good). RTF above 1 means it is slower than real-time (laggy, bad).

**Quantization**
Shrinking a model by storing its numbers with less precision (for example int8 instead of float32). Makes the model smaller and faster, with a small accuracy cost. Important for running on cheap phones.

**Hallucination (in speech models)**
When an STT model is fed silence or noise, it sometimes *invents* words that were never spoken. This is why we gate the input with VAD and push-to-talk.

## Networking terms

**Offline**
Works without any internet connection. Everything happens on the device.

**WiFi Direct**
Two phones connect directly to each other over WiFi, without a router or internet.

**Hotspot**
One phone shares its connection; the other joins it. Creates a small local network between them.

**Bluetooth (RFCOMM)**
A short-range wireless link. RFCOMM is the Bluetooth mode used to send a stream of data (like text) between two paired devices.

**IP address**
A device's address on a network, like `192.168.1.5`. Lets one device find and send data to another.

**Port**
A numbered "door" on an IP address (for example 8080). Lets data reach the correct app on a device.

**Socket**
A connection endpoint a program opens to send and receive data over a network. Think of it as a data pipe between two apps.

**TCP**
The set of rules for a reliable socket connection. TCP guarantees data arrives complete and in order (nothing lost, nothing scrambled). We use TCP so text messages arrive intact.

**LoRa**
A long-range, low-power radio technology. Carries tiny amounts of data (like text) over 2–15 km. Cannot carry audio, but can carry a text sentence. This is how iTantra reaches long distances in real deployment.

**Meshtastic**
An open-source project that runs on small LoRa radio boards. Phones connect to a LoRa board over Bluetooth and send text messages that hop across boards over kilometers, fully offline. We can use it as our long-range transport layer.

**Transport layer / transport interface**
The part of our app that actually sends and receives the text. We design it as a swappable "plug": WiFi socket today, LoRa tomorrow — the rest of the app does not change.

## Android / build terms

**Android Studio**
The program we use to write, build, and run the Android app.

**Kotlin**
The programming language we write the app in (modern, official for Android).

**SDK — Software Development Kit**
The set of tools and libraries needed to build Android apps.

**NDK — Native Development Kit**
Tools for running C/C++ code inside an Android app. Needed because the sherpa-onnx speech engine is written in C++.

**Gradle**
The build system that compiles our code and dependencies into an installable app.

**adb — Android Debug Bridge**
A command-line tool that lets the laptop talk to the phone: install apps, read logs, etc.

**APK**
The installable app file for Android (like an .exe on Windows).

**minSdk**
The oldest Android version our app supports. We use API 24 (Android 7.0) so it runs on old, cheap phones.

## Tools we chose

**sherpa-onnx**
The open-source engine that runs both STT and TTS models on the phone, offline. One library does both jobs. It has ready-made Android example apps we learn from.

**ONNX**
A standard file format for AI models. sherpa-onnx runs models saved in this format.

**AI4Bharat**
An Indian research group that released free, open-source STT and TTS models built specifically for Indian languages. These are our language models.

**VITS / Piper**
Types of TTS model architecture that produce natural-sounding speech. Our Hindi test voice is a "VITS Piper" model.

**PTT — Push-to-Talk**
The walkie-talkie behavior: hold a button to record and send, release to listen. Also our main defense against background noise (mic is off unless you are holding the button).
