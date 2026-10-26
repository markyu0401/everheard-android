# Everheard

**English** | [简体中文](README.zh-CN.md)

Always-on conversation memory for Android. Everheard records the conversations you are part of, transcribes them on-device, attributes speakers, and turns what was said into commitments, project timelines, and a weekly review — without keeping the audio.

> **Status: pre-alpha.** Debug builds work (Compose shell, a restrained grouped light/dark interface with in-app language and theme settings, armed capture core, on-device VAD/ASR/speaker spikes, encrypted transcript store with live partials and search); no release APK yet. The development plan is in [docs/PLAN.md](docs/PLAN.md); the architecture is in [docs/DESIGN.md](docs/DESIGN.md).

## Why another recorder

The reference product (OPPO AI 心力球) proved the shape: an always-on capture device + an app that turns talk into memory. Its own users found the weak points:

- Bystanders' conversations get filed as *your* progress.
- Small, time-bound promises are missed unless you press a button.
- The data lives in a cloud account and is metered by recording hours.
- It only works with one phone brand.

Everheard is the phone-only, open-source answer: local-first text, correctable attribution, a model you choose, and no subscription.

## What v1 targets

- **24 h armed capture** with allow/block windows (sleep, classes, meetings you don't want recorded).
- **On-device ASR** (sherpa-onnx): streaming live draft plus a SenseVoice final pass, with an optional cloud re-decode.
- **Speaker attribution**: enroll yourself once; cluster others; swipe "not mine" to teach the filter.
- **Commitments** extracted with evidence quotes and pushed to the system calendar.
- **Project timelines** built from repeated topics, plus a weekly insight with evidence.
- **Bring your own model**: any OpenAI-compatible endpoint (LiteLLM, DeepSeek, OpenAI, Ollama, local llama.cpp).
- **MCP server**: expose your memory to Claude, Cursor, or any MCP client.
- **Text only by default**: audio is deleted after transcription; the database is encrypted.

## Non-goals for v1

- No hardware, no BLE, no wearable.
- No account system, no multi-device sync, no telemetry.
- No iOS, no Play Store release, no subscription.
- No covert mode: recording is visible in the notification shade and on the live screen.

## Architecture at a glance

```
AudioRecord (16 kHz mono PCM16)
  → ring buffer (60 s)
  → Silero VAD → segments (5–30 s, 200 ms overlap)
  → on-device ASR (streaming draft + SenseVoice final) → optional cloud re-decode
  → speaker embedding + online clustering → "me" / A / B / background
  → SQLCipher (Room): session, segment, utterance, commitment, project, insight
  → LLM (JSON schema): summary, commitments, weekly review
  → Compose UI: Today, live capture, session detail, projects, insight, person, settings
  → MCP server (HTTP + token): search_memory, list_commitments, get_project
```

The capture service is a single `microphone` foreground service that stays armed; schedule rules gate `AudioRecord` instead of stopping the service, because Android refuses to create a microphone foreground service from the background.

## Build

Requirements: JDK 21, Android SDK with platform 37, and the Gradle wrapper (AGP 9.4.1, Gradle 9.8.2, built-in Kotlin).

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21   # macOS Homebrew
./gradlew lint testDebugUnitTest assembleDebug
# debug APK: app/build/outputs/apk/debug/app-debug.apk
```

Models are not committed. Fetch them with SHA-256 verification:

```bash
scripts/fetch_models.sh   # streaming ASR (~437 MB tarball) + SenseVoice (228 MB) + speaker embedding (27 MB)
```

Silero VAD (629 KB, MIT) is committed so the app can boot without a download. The streaming ASR, SenseVoice, and speaker models live under `app/src/main/assets/models/` and are gitignored.

Instrumented tests (service, AudioRecord, VAD, ASR, speaker) run on a device or emulator:

```bash
export ANDROID_HOME="$HOME/Library/Android/sdk"
sdkmanager "emulator" "system-images;android-36;aosp_atd;arm64-v8a"
avdmanager create avd -n everheard_atd -k "system-images;android-36;aosp_atd;arm64-v8a" --device pixel_7
emulator -avd everheard_atd -no-window -no-audio -no-boot-anim -gpu swiftshader_indirect &
./gradlew connectedDebugAndroidTest
```

## Documentation

- [Development plan](docs/PLAN.md)
- [Architecture and design](docs/DESIGN.md)
- [M0 spike plan](docs/plans/2026-10-26-m0-spikes.md)
- [ROM compatibility matrix](docs/ROM-MATRIX.md)
- [Legal notes](docs/LEGAL.md)
- [Privacy](docs/PRIVACY.md)
- [Third-party notices](THIRD_PARTY_NOTICES.md)

## Legal

Recording law depends on where you are and who is in the room. Everheard defaults to explicit, visible capture and a meeting-mode announcement; it is not a covert recorder. Read [docs/LEGAL.md](docs/LEGAL.md) before using it around other people.

## License

Code: Apache-2.0. Models and third-party binaries keep their own licenses; see [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md). "Everheard" is a working name and may change before v1.0.
