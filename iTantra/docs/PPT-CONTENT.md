# iTantra — SIH Idea PPT Content (jury-ready)

Follow the official SIH 2026 template. **Maximum 6 slides including the title.** Use points,
diagrams and infographics — not paragraphs. Save as PDF before uploading.

Each slide below gives: **what to put on the slide** (short bullets), a **visual to add**, and
**speaker notes** (what you say to the jury — do not print these on the slide).

---

## SLIDE 1 — TITLE PAGE

On the slide (use the template fields):
- Problem Statement ID: *(fill from portal)*
- Problem Statement Title: **iTantra — Indian Multilingual TTS & STT Aided Neural Transceiver Radio Access for Low-Bitrate Links**
- Theme: *(as listed for the PS, e.g. Disaster Management / MedTech-BioTech-HealthTech / Miscellaneous — use the portal's theme)*
- PS Category: **Software** (with an optional hardware add-on)
- Team ID: *(fill)*
- Team Name: *(your registered team name)*

Visual: one clean line-art of the core idea — Phone A 🎤 → "text" → Phone B 🔊 — with a tagline.

Tagline (one line under the title):
> **"Speak here, heard there — only text crosses the link, so it works where a voice call can't."**

Speaker notes: "Our solution lets people communicate by voice in 10 Indian languages, fully offline, even on the weakest radio links — by sending text across the link instead of audio."

---

## SLIDE 2 — IDEA / PROPOSED SOLUTION

**The problem (1 line):** In disasters and remote areas, networks are weak or dead. Audio is too heavy to send; typing excludes people who can't read/write.

**Our idea (the core insight):**
- Speak → convert to **text** on the phone (STT) → send the tiny text over the link → convert back to **speech** on the other phone (TTS).
- **Only text crosses the link.** A spoken sentence ≈ **80 bytes** as text vs **~200 KB** as audio → **~2500× smaller** → survives links that cannot carry voice.

**How it addresses the problem:**
- Humans still speak and hear voice on both ends — inclusive for **non-literate** users.
- Works **fully offline** — AI models run on the phone, no internet, no cloud.
- **Walkie-talkie (push-to-talk)** and **phone (hands-free)** modes; **alert messages** play at max volume, non-interruptible.

**Innovation / uniqueness:**
- Voice-in, voice-out — but a **text-sized payload** — enabling voice comms over **km-range, low-bitrate radio** where audio is impossible.
- **On-device, open-source, multilingual** (10 Indian languages) on low/mid-range phones.
- **Swappable transport:** same app runs over WiFi/Bluetooth today and **LoRa radio (2–15 km)** for real deployment — no app changes.

Visual (the money slide): the end-to-end diagram —
`🎤 Speak → [VAD + STT] → text (~80 B) → WiFi/BT/LoRa → [TTS] → 🔊 Speech`, with the
**80 B vs 200 KB** size comparison shown as two bars.

Speaker notes: "The trick is simple and powerful: never send audio, send text. That one decision is what makes offline, long-range, multilingual voice possible on a cheap phone."

---

## SLIDE 3 — TECHNICAL APPROACH

**Technologies (all open-source, offline):**
- **App:** Native Android (Kotlin), runs on Android 7.0+ (low/mid-range phones).
- **On-device speech engine:** **sherpa-onnx** (ONNX Runtime) — runs both STT and TTS on the phone.
- **STT models:** Moonshine (English), Whisper / AI4Bharat-family (Indian languages) — int8 quantized.
- **TTS models:** VITS / Piper voices (English + Indian languages), int8.
- **VAD:** Silero (detects pauses/stoppages to auto-form sentences).
- **Transport:** channel-based UDP over WiFi/Bluetooth now; **LoRa / Meshtastic** for long range.

**Methodology / pipeline (show as a flow chart):**
1. **Capture** — microphone (push-to-talk gates noise; VAD for hands-free mode).
2. **STT** — full-clip offline recognition → text sentence on a pause.
3. **Transport** — broadcast the text on a shared **channel** (walkie-talkie, one-to-many).
4. **TTS** — receiving phone converts text → speech, plays as a voice note.
5. **Alert path** — flagged messages play at maximum volume, non-interruptible.

**Design highlight:** a single swappable `Transport` interface (`sendText` / `onReceive`) — WiFi today, LoRa tomorrow, everything else unchanged.

Visual: horizontal pipeline flow chart of the 5 steps, with a small "LoRa module" box hanging off the Transport step for the long-range deployment.

Speaker notes: "We reuse mature open-source models rather than reinventing them, and we designed the transport as a plug so the same app scales from a WiFi demo to kilometre-range LoRa radio."

---

## SLIDE 4 — FEASIBILITY AND VIABILITY

**Why it is feasible (evidence):**
- **Working prototype:** two phones talking end-to-end over WiFi — speak on one, heard on the other — **fully offline**, today.
- Models already run on real mid-range phones (tested on Redmi Note 11, Realme, iQOO — arm64, Android 11–16).
- All components are **free and open-source** — near-zero software cost; only optional LoRa hardware (~₹1,500–3,000 for two nodes).

**Challenges & risks → our strategy:**
| Challenge | Strategy |
|---|---|
| Accuracy for 10 Indian languages | Per-language models (AI4Bharat/Whisper for Indic, Moonshine for English); force language per selection; measure WER |
| App/model size on cheap phones | int8 quantization + **on-demand per-language download** (small base app) |
| Idle battery/CPU | Push-to-talk = mic and models **off when idle** → near-zero idle CPU |
| Background noise | PTT gating + VAD thresholds + noise suppression |
| Real long-range link | Abstract transport layer → drop in LoRa/Meshtastic without touching the app |

Visual: a "Prototype status" strip (✅ STT ✅ TTS ✅ two-phone loop ✅ alert mode → ⏳ 10 languages, ⏳ LoRa) + the challenge/strategy table.

Speaker notes: "This isn't only a concept — the core loop already works offline on ordinary phones. The remaining work is scaling languages and swapping in the LoRa radio, both of which our design already accommodates."

---

## SLIDE 5 — IMPACT AND BENEFITS

**Who it helps (target users):**
- **Disaster response** — floods, earthquakes, when towers are down.
- **Defense / border / forest / coast patrols** — off-grid tactical radio nets.
- **Search & rescue, trekking, expeditions** — teams spread over kilometres.
- **Remote & rural communities** — no cell coverage.

**Impact:**
- **Inclusive** — voice works for people who **cannot read or write** (a core PS goal).
- **Life-saving** — alert/distress messages get through where voice calls fail, at max volume.
- **Infrastructure-free** — no towers, no internet, no data cost.

**Benefits (social / economic / strategic):**
- **Social:** equal access to emergency communication across languages and literacy levels.
- **Economic:** near-zero running cost, cheap phones, cheap radios; no subscriptions.
- **Strategic / national:** an **indigenous, open-source, offline** comms tool — no foreign cloud dependency; data never leaves the device.

Visual: a map/scenario infographic — a disaster zone with several people on a shared channel, phones + small LoRa nodes relaying text over km; icons for the four user groups.

Speaker notes: "The impact is widest exactly where communication matters most and is hardest — emergencies, off-grid, and for people left out by text-only tools."

---

## SLIDE 6 — RESEARCH AND REFERENCES

Keep as short link/points list:
- **sherpa-onnx** (next-gen Kaldi, on-device STT/TTS/VAD): github.com/k2-fsa/sherpa-onnx
- **AI4Bharat** — open Indian-language speech models (IndicConformer, Indic-TTS): ai4bharat.org / huggingface.co/ai4bharat
- **Moonshine** (fast offline English ASR) and **OpenAI Whisper** (multilingual ASR).
- **VITS / Piper** neural TTS voices (offline).
- **Silero VAD** (voice activity detection).
- **LoRa / Meshtastic** (open-source long-range radio mesh): meshtastic.org
- **ONNX Runtime** (on-device model inference).
- Payload basis: text ≈ 80 bytes/sentence vs PCM audio ≈ 200 KB/sentence → ~2500× reduction (the core low-bitrate rationale).

Visual: logos of the open-source tools in a neat row.

Speaker notes: "Everything is built on proven, open-source, offline technology — which is what the problem statement explicitly requires."

---

## Presentation tips for the internal round
- **Lead with the demo** if allowed: two phones, speak on one, heard on the other, offline (airplane mode on) — it lands harder than any slide.
- Put the **80 B vs 200 KB** number on screen — it's the single most convincing fact.
- Keep each slide to **5–7 short bullets**; let the diagrams carry the explanation.
- Map your strengths to the scoring: **Accuracy (40%)**, **Latency (20%)**, **Efficiency (20%)** — mention you measure WER, RTF and end-to-end latency, and that idle CPU is near zero thanks to push-to-talk.
- Honest framing: "English end-to-end loop is working now; the architecture scales to all 10 languages and to LoRa" — judges respect a truthful, working core over an over-claimed one.

*(If the college internal round allows more than 6 slides, add: one "Live Demo / Screenshots" slide and one "Architecture deep-dive" slide. For the official SIH portal upload, keep strictly to 6.)*
