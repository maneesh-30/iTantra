# 00 — What is iTantra?

## The problem

Sending **voice/audio** over a network uses a lot of data. In places with a weak or slow connection — a disaster zone, a remote area, a military radio link — there isn't enough bandwidth to carry audio. The voice call breaks up or fails.

But in an emergency, **voice is the best way to communicate**:
- It is faster than typing.
- It works for people who cannot read or write.
- It is natural and inclusive — everyone can speak.

So there is a conflict:
- **Voice** = best for humans, but too heavy for weak links.
- **Text** = tiny and survives weak links, but slower and needs literacy.

## The idea (how iTantra solves it)

Get the best of both. Let humans **speak and hear voice**, but send only **text** across the weak link.

```
Person A speaks
      ↓  (STT: speech turned into text, on A's phone)
   text
      ↓  (sent over WiFi / Bluetooth / radio — tiny amount of data)
   text
      ↓  (TTS: text turned back into speech, on B's phone)
Person B hears the spoken message
```

The trick: **audio never crosses the link — only text does.** A spoken sentence is about 80 bytes as text, but hundreds of kilobytes as audio. Text is thousands of times smaller, so it gets through where audio cannot.

## What the app must do

1. **Listen and detect pauses** — when you stop talking, it knows the sentence is finished.
2. **Convert speech to text** (STT) on the phone.
3. **Send the text** to another phone (or radio device) over WiFi/Bluetooth, with very little delay.
4. **Convert received text back to speech** (TTS) and play it as a voice note.
5. **Alert messages** play at maximum volume and cannot be interrupted.
6. **Two modes:**
   - *Push-to-talk (PTT)* — hold a button to talk, like a walkie-talkie. One person at a time.
   - *Phone mode* — both people can talk freely, like a normal call.

## The hard rules (from the problem statement)

- **Fully offline.** No internet. The AI models run *on the phone itself*.
- **Open-source only.** No paid/commercial voice services (Google Cloud, Amazon, etc. are banned).
- **Must run on cheap phones** — low and mid-range Android devices.
- **10 languages:** Hindi, Gujarati, Marathi, Kannada, Malayalam, Tamil, Telugu, Odia, Bengali, English.

## How we are scored

| What | Weight | Meaning |
|------|--------|---------|
| **Accuracy** | 40% | STT gets the words right (low error rate); TTS sounds clear and natural. |
| **Latency** | 20% | Small delay between speaking and hearing on the other phone. |
| **Efficiency** | 20% | Small model/app size; low CPU use while idle-listening. |

Accuracy is the biggest and hardest part. Latency is our natural strength, because text moves fast.

## The one sentence to remember

**Speak on one phone, hear it on another — but only text crosses the link, so it works where audio can't, in 10 Indian languages, fully offline, on cheap phones.**
