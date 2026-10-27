# Third-party notices

Everheard's own code is Apache-2.0. This file lists what actually ships in the app and in the release APK, with the license of each component. Last verified: 2026-10-27.

## Libraries (shipped in the APK)

| Component | Version | License | Use |
|---|---|---|---|
| sherpa-onnx (AAR) | 2.0.0 | Apache-2.0 | ASR, VAD, speaker embedding |
| AndroidX Core KTX | 1.19.1 | Apache-2.0 | Platform helpers |
| AndroidX Lifecycle (runtime-ktx, runtime-compose, viewmodel-compose) | 2.11.0 | Apache-2.0 | Lifecycle and state |
| AndroidX Activity Compose | 1.13.0 | Apache-2.0 | Activity/Compose integration |
| AndroidX DataStore Preferences | 1.3.0 | Apache-2.0 | Settings storage |
| Jetpack Compose BOM | 2026.09.00 | Apache-2.0 | UI toolkit |
| Compose UI / Graphics / Tooling Preview | per BOM | Apache-2.0 | UI |
| Compose Material 3 | per BOM | Apache-2.0 | Components |
| Material Icons Core + Extended | per BOM | Apache-2.0 | Icons |
| Kotlin standard library | 2.2.10 | Apache-2.0 | Language runtime |
| kotlinx.coroutines (android) | 1.11.0 | Apache-2.0 | Concurrency |

## Test-only (not shipped)

| Component | Version | License |
|---|---|---|
| JUnit 4 | 4.13.2 | EPL-1.0 |
| Truth | 1.4.5 | Apache-2.0 |
| AndroidX Test ext JUnit / Rules / Espresso | 1.3.0 / 1.7.0 / 3.7.0 | Apache-2.0 |
| kotlinx-coroutines-test | 1.11.0 | Apache-2.0 |
| org.json | 20240303 | Public domain |

## Models (bundled in the release APK)

| Model | Size | License | Use | Source |
|---|---|---|---|---|
| Streaming Zipformer **small** bilingual zh-en int8 | 48 MB | Apache-2.0 (upstream: k2-fsa/icefall) | Live draft transcript | `sherpa-onnx-streaming-zipformer-small-bilingual-zh-en-2023-02-16` |
| SenseVoice small int8 (zh/en/ja/ko/yue) | 228 MB | MIT (upstream: FunAudioLLM/SenseVoice) | Final-pass transcript | `csukuangfj/sherpa-onnx-sense-voice-zh-en-ja-ko-yue-2024-07-17` |
| CAM++ zh-en speaker embedding | 27 MB | Apache-2.0 (upstream: modelscope/3D-Speaker) | Speaker embedding | `3dspeaker_speech_campplus_sv_zh_en_16k-common_advanced.onnx` |
| Silero VAD v5 | 629 KB | MIT | Speech gating | `silero_vad.onnx` |

The Hugging Face model cards do not declare a separate license; the upstream repositories above do, and the weights are redistributed under those terms. If a model card changes, update this table before shipping.

## Fonts

| Font | License |
|---|---|
| Instrument Sans | SIL Open Font License 1.1 (`assets/fonts/OFL-InstrumentSans.txt`) |
| Instrument Serif | SIL Open Font License 1.1 (`assets/fonts/OFL-InstrumentSerif.txt`) |
| Noto Serif SC | SIL Open Font License 1.1 (`assets/fonts/OFL-NotoSerifSC.txt`) |

## Not shipped (do not list as dependencies)

Room, SQLCipher, Hilt/Dagger, OkHttp and Ktor are **not** dependencies of the current build. The M2 transcript and memory layers are first-party encrypted JSON documents: AES-256-GCM with a non-exportable Android Keystore key, written atomically. The Room + SQLCipher migration is planned for M4; when it lands, add the rows here in the same PR.

## Rules

1. Do not commit model weights or third-party binaries to the repository.
2. Download from a pinned URL and verify SHA-256 before use.
3. Show the model license and source in the app before download.
4. Add a row here for every new dependency or model in the same PR that introduces it.
5. If a license is unclear, do not ship it; find an alternative.
