# Contributing

Everheard is a solo-maintained project, but issues and pull requests are welcome.

## Before you write code

- Open an issue describing the problem and the user-visible outcome. Link the milestone it belongs to.
- For anything touching capture, privacy, or model downloads, wait for a written decision in the issue. These areas have hard Android and legal constraints.

## Workflow

1. Branch from `main`: `feat/<short-name>`, `fix/<short-name>`, `docs/<short-name>`.
2. Keep commits conventional: `feat(capture): add schedule evaluator`, `fix(asr): handle empty segment`.
3. Every behavior change needs a test. UI-only changes need a screenshot or a manual test note in the PR.
4. Run `./gradlew lint testDebugUnitTest assembleDebug` before opening the PR.
5. Squash-merge; the PR title becomes the commit message.

## Hard rules

- **No secrets, no personal data.** Do not commit API keys, keystores, real recordings, transcripts, device serials, or LAN addresses.
- **No model weights in the repo.** Download them at runtime, verify SHA-256, show the license.
- **No covert recording features.** Capture must be visible: persistent notification, live screen, start tone.
- **Privacy defaults are not configurable in the unsafe direction.** Cloud calls are opt-in per session; audio is deleted after transcription unless the user explicitly retains it.
- **Attribute, don't guess.** A low-confidence speaker label must be shown as low-confidence, never silently dropped.

## Reporting security issues

Use GitHub Security Advisories ("Report a vulnerability") on the repository. Do not open a public issue for a vulnerability.
