# iTantra — Learning Guide

This folder is the **plain-English notebook** for the iTantra project. It explains *what* we are building, *why*, and *how* every piece works — written for someone who is new to this.

Use it to:
- Understand the project yourself, module by module.
- Explain the project to teammates, judges, or anyone else.
- Come back later and remember why we made each decision.

## How to read this folder

Read the files in order. Each one builds on the last.

| File | What it covers |
|------|----------------|
| [00-what-is-itantra.md](00-what-is-itantra.md) | The problem we are solving, in simple words. The one-line idea. |
| [01-glossary.md](01-glossary.md) | Every technical term (STT, TTS, VAD, RTF, socket, LoRa…) explained plainly. |
| [02-architecture.md](02-architecture.md) | The big picture: how the parts fit together, with diagrams. |
| [03-progress-log.md](03-progress-log.md) | A dated diary of everything we have done, step by step. |
| [04-modules.md](04-modules.md) | Each code module: what it does, why it exists, how it works. Grows as we build. |

## The 10-second summary

iTantra lets one person **speak** into a phone and another person **hear** that speech on a different phone — but across the connection, only **text** travels, never audio. Text is tiny, so the message gets through even on very weak radio links where sending audio would fail. It works in 10 Indian languages, fully offline (no internet), on cheap phones.

## Rule for keeping this useful

Every time we finish a real step, we add a short entry to `03-progress-log.md`, and when we add a new code module we document it in `04-modules.md`. Keep the language simple. If a term is new, it goes in the glossary.
