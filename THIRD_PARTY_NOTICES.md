# Third-party notices

Everheard's own code is Apache-2.0. The following components keep their own licenses. This file is a starting point; every dependency and model must be verified before it is shipped or downloaded.

## Libraries

| Component | Version | License | Use |
|---|---|---|---|
| sherpa-onnx (AAR) | 2.0.0 (pin; fallback 1.13.8) | Apache-2.0 | ASR, VAD, speaker embedding/diarization | Downloaded by `scripts/fetch_sherpa.sh` (47 MB, all ABIs) |
| AndroidX / Jetpack Compose | per Compose BOM 2026.09.00 | Apache-2.0 | UI, lifecycle, Room |
| Room | 2.8.5 | Apache-2.0 | Database |
| SQLCipher for Android (`sqlcipher-android`) | verify at scaffold | BSD-style (verify) | Encrypted database |
| Kotlin / kotlinx.coroutines / serialization | 2.4.21 | Apache-2.0 | Language, async, JSON |
| Ktor client or OkHttp | verify at scaffold | Apache-2.0 | HTTP providers |
| Hilt / Dagger | verify at scaffold | Apache-2.0 | DI |
| Material Icons | — | Apache-2.0 | Icons |

## Models (downloaded at runtime, never committed)

| Model | Size (verified) | License | Use | Notes |
|---|---|---|---|---|
| streaming Zipformer bilingual zh-en int8 (2023-02-20) | encoder 174 MB + decoder 13 MB + joiner 3.1 MB | Verify model card (k2-fsa) | Live draft | ~190 MB total |
| streaming Zipformer **small** bilingual zh-en int8 (2023-02-16) | encoder 41 MB + decoder 3.3 MB + joiner 3.1 MB | Verify model card (k2-fsa) | M0 default, live draft | Downloaded by `scripts/fetch_models.sh`; not committed |
| SenseVoice int8 | 239 MB | Verify (FunAudioLLM) | Batch re-decode option | 600–700 MB RAM peak on Android; not resident |
| Silero VAD v5 | 629 KB | MIT (verify) | Speech gating | Committed at `app/src/main/assets/models/silero_vad.onnx` |
| 3D-Speaker CAM++ zh-en 16k | 27 MB | Verify model card (3D-Speaker) | Speaker embedding | Downloaded by `scripts/fetch_models.sh`; not committed |
| WeSpeaker / 3D-Speaker embedding | ~20–40 MB | Apache-2.0 (verify) | Speaker embedding | |
| faster-whisper large-v3 (server-side) | ~3 GB | MIT (verify) | Optional cloud re-decode | Runs on the user's own GPU host |

## Rules

1. Do not commit model weights or third-party binaries to the repository.
2. Download from a pinned URL and verify SHA-256 before use.
3. Show the model license and source in the app before download.
4. Add a row here for every new dependency or model in the same PR that introduces it.
5. If a license is unclear, do not ship it; find an alternative.

*Last updated: 2026-10-26.*
