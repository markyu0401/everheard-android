# Security Policy

## Reporting

Use GitHub Security Advisories on this repository ("Report a vulnerability"). Private vulnerability reporting is enabled, so the report stays private until a fix is ready. Please do not open a public issue for a vulnerability.

## Threat model (v1)

Everheard holds the most sensitive data a phone can hold: what you and the people around you said. The design assumes:

- **Lost or stolen device.** Transcripts and memories are stored as AES-256-GCM encrypted documents; the key is non-exportable in Android Keystore. Audio is deleted after transcription by default.
- **Malicious app on the same device.** Only the launcher activity and the quick-settings tile service are exported; the tile is protected by the system `BIND_QUICK_SETTINGS_TILE` permission. The capture service and boot receiver are not exported. The MCP server binds to `127.0.0.1` and requires a bearer token.
- **Compromised cloud provider.** Cloud ASR/LLM calls are opt-in per session; the app sends only the audio or text needed for that request. Provider keys are stored in an encrypted `providers.bin` under a Keystore alias.
- **Shoulder surfing / screenshots.** Transcript and memory screens set `FLAG_SECURE`. Export is user-initiated (share as text, Markdown or JSON); there is no biometric gate in v1.
- **Model supply chain.** Models are fetched at build time from pinned URLs, verified with SHA-256, and bundled in the APK. The app has no runtime model downloader in v1.
- **Log leakage.** Content is not written to logcat. The app has no analytics or crash-reporting SDK.
- **Voiceprint.** The speaker profile is biometric data. It is used only for attribution, never for authentication, and can be deleted in Settings.

## Out of scope for v1

- Rooted devices with a compromised Keystore.
- Physical coercion.
- Multi-device sync (not implemented).

## Known limitations

- A single phone microphone cannot measure distance; attribution is a best-effort score, not a guarantee.
- Always-on capture is visible to bystanders by design. The app cannot make it invisible, and will not try.
- The NAS backup is manual and encrypted with a user-supplied passphrase; losing the passphrase makes the backup unrecoverable.
