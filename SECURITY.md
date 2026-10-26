# Security Policy

## Reporting

Use GitHub Security Advisories on this repository ("Report a vulnerability"). Please do not open a public issue for a vulnerability.

## Threat model (v1)

Everheard holds the most sensitive data a phone can hold: what you and the people around you said. The design assumes:

- **Lost or stolen device.** The database is encrypted with SQLCipher; the key is wrapped by Android Keystore (StrongBox where available). Audio is deleted after transcription by default.
- **Malicious app on the same device.** No exported services or receivers except the launcher activity; MCP server binds to localhost by default and requires a bearer token.
- **Compromised cloud provider.** Cloud ASR/LLM calls are opt-in per session; the app sends only the audio or text needed for that request; keys are stored with Keystore-wrapped encryption.
- **Shoulder surfing / screenshots.** Transcript screens set `FLAG_SECURE`; export requires biometric confirmation.
- **Model supply chain.** Models are downloaded from pinned URLs with SHA-256 verification; the license is shown before download.
- **Log leakage.** Content is never written to logcat; crash reports are off by default.

## Out of scope for v1

- Rooted devices with a compromised Keystore.
- Physical coercion.
- Multi-device sync (not implemented).

## Known limitations

- A single phone microphone cannot measure distance; attribution is a best-effort score, not a guarantee.
- Always-on capture is visible to bystanders by design. The app cannot make it invisible, and will not try.
