# Privacy

## Design rule

The default is **local-only, text-only**. Audio exists only long enough to become text. Nothing leaves the phone unless the user turns on a cloud provider for that session or explicitly runs a NAS backup.

## Data inventory

| Data | Where it lives | Retention | Leaves the device? |
|---|---|---|---|
| Raw audio | App-private storage, encrypted | Deleted after transcription (default); optional 24 h for corrections | Only if a cloud ASR provider is enabled for that session |
| Transcript | Encrypted store (AES-256-GCM, Keystore key) | Until the user deletes it | Only to a cloud LLM if enabled for that session |
| Speaker embeddings | Encrypted store (AES-256-GCM, Keystore key) | Until the user deletes the person | No (computed on-device) |
| Commitments / projects / insights | Encrypted store (AES-256-GCM, Keystore key) | Until deleted | Included in a cloud summary request if enabled |
| API keys | Keystore-wrapped encrypted preferences | Until removed | No |
| Settings | DataStore | Until app data is cleared | No |
| NAS backup file | User-configured WebDAV folder | Until the user deletes it on the NAS | Only when the user runs “Back up now”; encrypted with the user passphrase before upload |
| Crash logs / analytics | Not collected | — | No |

## Cloud calls

- Cloud ASR and cloud LLM are **per-provider, per-session opt-in**. The first time a provider is used, the app shows exactly what will be sent (audio or text), to which host, and under which model.
- The app sends the minimum: one segment for ASR, or the transcript of one session for summarisation. It does not send the whole database.
- A provider's own retention policy applies once data leaves the device; the app links to it in settings.
- There is no first-party Everheard server. The project has no backend.
- NAS backup is a user-initiated upload to the user's own WebDAV server. The `.ehb` file is AES-256-GCM encrypted with a user passphrase (PBKDF2-HMAC-SHA256) before it leaves the phone; it contains transcripts, memory, schedule and excluded apps, not provider API keys or the voiceprint. The NAS password and the backup passphrase are stored in an encrypted `nas.bin` under a dedicated Android Keystore alias.

## Permissions

| Permission | Why | Optional? |
|---|---|---|
| `RECORD_AUDIO` | Capture | Required for the core function |
| `POST_NOTIFICATIONS` | Visible recording indicator | Required on Android 13+ |
| `FOREGROUND_SERVICE_MICROPHONE` | Keep capture alive with the screen off | Required |
| `READ_CALENDAR` / `WRITE_CALENDAR` | Push commitments and auto-trigger around meetings | Optional |
| `BLUETOOTH_CONNECT` | Route to a headset mic | Optional |
| Battery-optimisation exemption | Survive OEM battery managers | Optional, explained in-app |
| `MediaProjection` consent | Capture internal audio of online meetings | Per-session, optional |

## User controls

- Delete one session, one person, one project, or everything.
- Export Markdown/JSON, then delete.
- Pause capture for 15 min / 1 h / until tomorrow from the notification.
- Turn off cloud providers entirely; the app remains functional with on-device ASR and a local LLM (if configured).
- Back up to a NAS, restore from a backup, or delete the remote files with the NAS tools.

## What the app will never do

- Upload audio or transcripts without an explicit, visible opt-in.
- Keep audio after transcription by default.
- Collect analytics, advertising IDs, or crash content.
- Record while the user believes capture is off: the notification and the mic indicator are the ground truth.
