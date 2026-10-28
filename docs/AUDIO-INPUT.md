# External audio input for Everheard — research and decision

**Status:** research complete; implementation plan in
`docs/plans/2026-10-27-external-audio-input.md`.
**Date:** 2026-10-27
**Scope:** USB-C and Bluetooth microphones on Android; capture quality, routing,
and Android's concurrent-capture policy.

## TL;DR

- Everheard records with `AudioRecord(VOICE_RECOGNITION, 16 kHz, mono, PCM16)`
  and pauses when another app is recording or when its own capture is silenced
  (`PAUSED_MIC_BUSY`).
- An external microphone **does not** fix "another app is using the mic, so
  Everheard stops". Android 10+ allows only one ordinary app to capture at a
  time; the policy is per app/client, not per audio device. A USB or Bluetooth
  mic changes which device Everheard uses, not whether it is allowed to capture
  alongside another ordinary app.
- **USB-C (UAC) is the reliable path.** Android has supported USB audio class 1
  since Android 5.0; a USB mic appears as a normal input device, needs no
  Bluetooth permission, and can be pinned with
  `AudioRecord.setPreferredDevice()`. Full-band 48 kHz is possible; the
  framework resamples to Everheard's 16 kHz.
- **Bluetooth classic is call-grade.** HFP/SCO capture is 8 kHz (CVSD) or
  16 kHz (mSBC); media quality drops while SCO is active. DJI Mic Mini's
  transmitter can connect directly to a phone over Bluetooth and presents
  itself as an audio input device, but DJI says compatibility varies by phone
  and app. Treat it as experimental.
- **"Bypass system sounds" is mostly physical.** A close-talk lavalier on the
  collar is far from the phone speaker and close to the mouth, so speaker bleed
  drops substantially. Android has no API that subtracts speaker audio from the
  microphone signal. Acoustic echo cancellation (AEC) is the only software
  option, and it is tuned for calls, not ASR.
- **Recommendation:** ship USB-C external mic support first (0.2.0), add
  Bluetooth behind an experimental flag only if the on-device probe passes,
  and keep the honest pause behavior for concurrent capture.

## 1. Current capture path

`CaptureService` is a `microphone` foreground service. When the state machine
enters `RECORDING`, it creates `AudioRecordSource`:

```kotlin
AudioRecord(
    MediaRecorder.AudioSource.VOICE_RECOGNITION, // no AGC/NS
    16_000,
    AudioFormat.CHANNEL_IN_MONO,
    AudioFormat.ENCODING_PCM_16BIT,
    bufferSizeBytes
)
```

Frames are 20 ms (320 samples). They feed `RingBuffer`, `SileroVad`,
`SpeechSegmenter`, and the on-device streaming ASR (`CaptureTranscriber`).
`AudioRecordingMonitor` watches `AudioManager.getActiveRecordingConfigurations()`
and closes the mic when another non-hotword client is recording, or when
Everheard's own session has `isClientSilenced() == true`.

`docs/DESIGN.md` §3.1 already records the project's position on Bluetooth:
"SCO/LE audio is 8/16 kHz and recognizably worse — prefer the phone mic when
both exist." That position is correct for Bluetooth classic. USB is a
different case because it does not use SCO and can deliver full-band audio.

## 2. Why capture pauses when another app is used

Android 10 introduced a priority scheme for audio input. The relevant rules
([Sharing audio input](https://developer.android.com/media/platform/sharing-audio-input)):

- Privileged apps (the Assistant, accessibility services) have higher priority
  than ordinary apps.
- Apps with a visible foreground UI have higher priority than background apps.
- Apps capturing from a privacy-sensitive source (`CAMCORDER`,
  `VOICE_COMMUNICATION`) have higher priority than apps that are not.
- **Two ordinary apps can never capture audio at the same time.** One receives
  audio; the other receives silence.
- If neither app is privacy-sensitive, the app with a UI on top receives audio;
  if neither has a UI, the one that started capture most recently receives it.
- A voice call (`MODE_IN_CALL` or `MODE_IN_COMMUNICATION`) always receives
  audio; an ordinary app does not.

The AOSP [Concurrent capture](https://source.android.com/docs/core/audio/concurrent)
page describes the HAL side: multiple input streams can be active, and
different streams can be routed to different devices (for example, a Bluetooth
headset and the built-in mic). But the framework still silences the
lower-priority client. The HAL's device flexibility is for privileged/regular
concurrency and for multiple streams from the same app, not a loophole for two
ordinary apps.

Consequences for Everheard:

1. **A USB or Bluetooth mic does not bypass the policy.** The framework may
   route Everheard to the USB mic and the other app to the built-in mic, but
   only one ordinary client receives non-silent audio. Everheard's own monitor
   is right to pause; otherwise it would write silence.
2. **A phone call always wins.** No external mic changes that.
3. **If the other app is only playing audio**, the pause is Everheard's own
   `Excluded apps` policy, not the capture policy. An external mic does not
   change it.

If the product goal is "keep recording while another app records", the levers
are:

- Accept the pause (current behavior).
- Mark Everheard's capture privacy-sensitive with
  `AudioRecord.setPrivacySensitive(true)` (API 30+), which raises its priority
  over non-privacy-sensitive apps. This makes the other app receive silence
  instead; it is a product decision, not a microphone feature.
- Use a second device (a dedicated recorder), which is out of scope.
- Run as a privileged/system app, which is not viable for a normal APK.

The plan deliberately does **not** promise concurrent capture.

## 3. Bluetooth microphone

### 3.1 Classic Bluetooth (HFP/SCO)

A Bluetooth headset or lavalier that exposes a microphone uses the Hands-Free
Profile. To record from it, the app must bring up a Synchronous Connection
Oriented (SCO) link:

- API 31+: `AudioManager.setCommunicationDevice(AudioDeviceInfo)`. The method
  accepts a **sink** (output) device from
  `getAvailableCommunicationDevices()`; the platform selects the matching
  source automatically. `clearCommunicationDevice()` releases it.
- API 26–30: `startBluetoothSco()` + `setBluetoothScoOn(true)`, then wait for
  `ACTION_SCO_AUDIO_STATE_UPDATED` with `SCO_AUDIO_STATE_CONNECTED`;
  `stopBluetoothSco()` releases it. `startBluetoothSco()` requires
  `MODIFY_AUDIO_SETTINGS`.

Quality:

- CVSD is 8 kHz narrowband; mSBC is 16 kHz wideband. Both are phone-call
  grade. Android's [BLE Audio overview](https://developer.android.com/develop/connectivity/bluetooth/ble-audio/overview)
  states that "Bluetooth classic lowers audio quality when using Bluetooth
  microphones" and that LE Audio input can reach 32 kHz.
- While SCO is active, media playback quality drops and/or is routed to the
  headset. The phone may behave as if a call is in progress.
- On Android 12+, `setCommunicationDevice()` on a Bluetooth device can fail
  silently if `BLUETOOTH_CONNECT` is not granted on some OEM builds. Request
  the permission before selecting a Bluetooth device; USB does not need it.

### 3.2 LE Audio

Android 13+ supports LE Audio. A LE Audio hearable can expose
`AudioDeviceInfo.TYPE_BLE_HEADSET` and be recorded with
`AudioRecord.setPreferredDevice()`; input can reach 32 kHz and media quality
is preserved. This is the better Bluetooth path, but it requires a LE
Audio-capable microphone. DJI does not advertise LE Audio for the Mic Mini;
its direct-phone mode behaves like a classic Bluetooth audio input, so this
path does not apply to it.

### 3.3 DJI Mic Mini direct Bluetooth

DJI's manual says the transmitter "will then function as an audio input device
and can be used with third-party recording or live streaming applications."
DJI's support page is more cautious: when the transmitter is connected to a
phone over Bluetooth, "the phone's system camera, voice recorder, and some apps
may not be able to record audio"; compatibility depends on the phone and app.
On iOS the system camera does not support Bluetooth audio input; some Samsung
and OPPO camera apps let the user select an external input.

Practical conclusion: direct Bluetooth is **best-effort**. It may enumerate as
a standard HFP device and work with Everheard on some phones, but it is not a
guaranteed path and the audio is call-grade. The receiver (RX) over USB-C is
the deterministic path.

## 4. USB microphone

Android has supported USB audio class 1 (UAC1) in host mode since Android 5.0
([USB digital audio](https://source.android.com/docs/core/audio/usb)):

- The device must act as USB host (phones with USB-C/USB OTG do).
- PCM, 16/24/32-bit; sample rates 48, 44.1, 32, 24, 22.05, 16, 12, 11.025,
  or 8 kHz; mono or stereo.
- A USB microphone appears as `AudioDeviceInfo.TYPE_USB_DEVICE` or
  `TYPE_USB_HEADSET` in `AudioManager.getDevices(GET_DEVICES_INPUTS)`.
- No Bluetooth runtime permission is needed. `AudioRecord.setPreferredDevice()`
  pins the input to the USB device; the framework resamples the hardware rate
  to the client's requested 16 kHz.
- USB hotplug is visible through `AudioManager.registerAudioDeviceCallback()`.
  If the device is removed mid-recording, `AudioRecord.read()` can return
  `ERROR_DEAD_OBJECT`; the service must close and re-open with a fallback.

### 4.1 DJI Mic Mini receiver

The DJI Mic Mini receiver has a USB-C data port and a phone adapter. DJI's
manual documents "Using with a Computer" by connecting the receiver and
selecting it as the audio input; the phone path uses the same USB-C adapter.
DJI's support guidance for a receiver-connected phone is to test with the
phone's built-in voice recorder and confirm the waveform follows the
transmitter. The receiver supports pass-through charging, so the phone can
charge while the receiver is attached.

Caveats to test on the actual phone:

- Whether the receiver enumerates as `TYPE_USB_DEVICE` or `TYPE_USB_HEADSET`.
- Whether it exposes both input and output. If it exposes an output, Android
  may route media to it; without headphones in the receiver's monitoring jack,
  media can become inaudible. This is a UX trade-off, not a bug in Everheard.
- Whether 16 kHz mono `AudioRecord` initializes directly, or whether the app
  must record at 48 kHz and resample. The framework normally resamples, but
  the probe must confirm on the target Samsung build.
- Power draw and battery impact over a one-hour session.

### 4.2 Input-only USB microphones

A plain USB-C lavalier with no output endpoint avoids the media-rerouting
problem. It is the simplest hardware choice if the user does not need the DJI
receiver's monitoring or dual-transmitter features. The trade-off is a cable.

## 5. System sounds and echo

The built-in mic sits next to the phone speaker. When the phone plays media,
notifications, or the app's 180 ms start tone, the mic picks up that audio
acoustically. A lavalier on the collar is close to the mouth and far from the
speaker; the speech-to-bleed ratio improves substantially. That is the real
"bypass".

Android does not provide an API that subtracts the speaker signal from the
microphone input. The available options are:

- **A close-talk external mic** — the primary fix.
- **Acoustic echo cancellation (AEC)** via `AcousticEchoCanceler` or the
  `VOICE_COMMUNICATION` source. AEC is tuned for calls; it can suppress
  background speech and change the ASR characteristics. Everheard deliberately
  uses `VOICE_RECOGNITION` to avoid AGC/NS, so AEC should be an opt-in
  experiment, not a default.
- **Pause while other apps play audio** — already implemented through
  `Excluded apps`. This is the deterministic way to avoid transcribing a
  specific app's audio.
- **Route media to the USB device** — if the USB device exposes an output and
  Android routes media to it, the phone speaker is silent. The user may not
  hear the media unless headphones are attached to the device.

The app's own start tone is a small, fixable case: `playStartTone()` runs
immediately before `evaluate()` opens the microphone, so the first frames can
contain the 180 ms beep. Delaying the mic open by ~250 ms after the tone
removes it without affecting the capture UX.

## 6. Hardware notes

| Option | Quality | Routing | Permission | Notes |
|---|---|---|---|---|
| Built-in mic | Good in quiet rooms; picks up speaker bleed | Always available | `RECORD_AUDIO` | Current default |
| USB-C lav (input-only) | Full-band 48 kHz | `setPreferredDevice()`; no SCO | `RECORD_AUDIO` | Simplest; no media rerouting |
| DJI Mic Mini RX + TX (USB-C) | Full-band 48 kHz, close-talk | Same as USB | `RECORD_AUDIO` | RX may expose output; test media routing; pass-through charging |
| DJI Mic Mini TX direct BT | Call-grade 8/16 kHz | HFP/SCO; `setCommunicationDevice()` | `BLUETOOTH_CONNECT`, `MODIFY_AUDIO_SETTINGS` | Best-effort; compatibility varies |
| Bluetooth headset mic | Call-grade 8/16 kHz | HFP/SCO | Same | Convenient; worse for ASR |
| LE Audio mic | Up to 32 kHz | `TYPE_BLE_HEADSET` + `setPreferredDevice()` | `BLUETOOTH_CONNECT` | Android 13+; not DJI Mic Mini |

## 7. Decision

1. **0.2.0-alpha: USB-C external microphone support.**
   - Automatic mode: USB first, then LE Audio, then built-in, then Bluetooth
     classic.
   - Manual modes: phone microphone / external microphone.
   - Hotplug: switch when a device appears; fall back or pause when it
     disappears.
   - Diagnostics: show the routed device, sample rate, channel count, and
     level; include a 5-second test.
   - No new runtime permission for USB.
2. **0.2.x: Bluetooth classic, experimental.**
   - Behind a setting, default off.
   - Only if the on-device probe shows that `AudioRecord` receives non-silent
     audio from the DJI transmitter and that ASR quality is acceptable.
   - Otherwise document "use the receiver over USB-C" and do not ship the
     Bluetooth path.
3. **No concurrency promise.** Keep `PAUSED_MIC_BUSY`; the copy now distinguishes an interrupted recording (“Microphone taken”, high-importance heads-up) from waiting for a busy mic while armed, and the guard reads our own `AudioRecord` silenced state instead of guessing from session ids.

## 8. Open questions

- Which phone(s) are the primary targets? The repo's real device is a Samsung
  SM-S948U1 (Galaxy S26 Ultra, Android 17/API 37); Chinese OEM ROMs are still
  untested.
- Does the user already own a DJI Mic Mini (TX + RX) or another USB/BT mic?
  The probe needs the actual hardware.
- Is the priority clearer audio, fewer pauses, or not transcribing system
  audio? The plan optimizes for clearer audio and physical isolation; fewer
  pauses is not solvable with a microphone.
- Is Bluetooth a must-have, or is USB-C acceptable? The recommendation is USB
  first, Bluetooth experimental.

## References

- [Sharing audio input](https://developer.android.com/media/platform/sharing-audio-input)
- [Concurrent capture (AOSP)](https://source.android.com/docs/core/audio/concurrent)
- [Bluetooth LE Audio overview](https://developer.android.com/develop/connectivity/bluetooth/ble-audio/overview)
- [Audio recording over BLE Audio](https://developer.android.com/develop/connectivity/bluetooth/ble-audio/audio-recording)
- [USB digital audio (AOSP)](https://source.android.com/docs/core/audio/usb)
- [AudioRecord reference](https://developer.android.com/reference/android/media/AudioRecord)
- [AudioManager reference](https://developer.android.com/reference/android/media/AudioManager)
- [DJI Mic Mini FAQ](https://www.dji.com/mic-mini/faq)
- [DJI Mic Mini detection/troubleshooting](https://support.dji.com/help/content?customId=01700011794&spaceId=17&re=US&lang=en)
- [DJI Mic Mini user manual (PDF)](https://dl.djicdn.com/downloads/DJI_Mic_Mini/20241120/UM/DJI_Mic_Mini_User_Manual_v1.0_EN.pdf)
