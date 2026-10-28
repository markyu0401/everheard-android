# External Audio Input Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add selectable USB-C and experimental Bluetooth microphone input to Everheard's capture service, with hotplug handling, diagnostics, and honest UI about Android's concurrent-capture policy.

**Architecture:** A pure `AudioInputResolver` maps a persisted `AudioInputMode` and the current `AudioInputDevice` list to one device. `AudioDeviceCatalog` wraps `AudioManager` and hotplug callbacks; `AudioRecordSource` pins the resolved `AudioDeviceInfo` with `setPreferredDevice()` and reports route/read errors. `CaptureService` owns the policy: it opens the resolved input, restarts on route changes, and falls back or pauses when the preferred device disappears. Bluetooth classic is isolated in `BluetoothMicRoute` behind an experimental setting.

**Tech Stack:** Kotlin, Jetpack Compose, Android `AudioManager`/`AudioRecord`, minSdk 26 / targetSdk 36 / compileSdk 37, JUnit4 + Truth, the existing foreground-service and state-machine architecture.

**Spec:** [docs/AUDIO-INPUT.md](../AUDIO-INPUT.md)

## Global Constraints

- Application ID and package: `io.github.markyu0401.everheard`.
- `compileSdk = 37`, `targetSdk = 36`, `minSdk = 26`, JVM target 17.
- Do not add third-party dependencies; use the Android framework and existing Compose/JUnit/Truth stack.
- The capture pipeline stays 16 kHz mono PCM16, 320-sample frames. Resampling from a USB device's 48 kHz is the framework's job; do not change the ASR input format.
- `RECORD_AUDIO` remains the only capture permission for the USB path. `BLUETOOTH_CONNECT` and `MODIFY_AUDIO_SETTINGS` are only for the experimental Bluetooth path.
- Capture remains visible: persistent notification, start tone, live screen. No covert path.
- Do not claim concurrent capture. `PAUSED_MIC_BUSY` remains the honest response to another app holding the mic.
- Every task ends with `./gradlew lint testDebugUnitTest assembleDebug` green and a commit. Instrumentation tasks also run the named test on the Samsung device.
- Never commit model weights, audio, API keys, keystores, device serials, or personal paths.

## Review Focus

These are the conditions most likely to break a real user; each is pinned by a test or a manual check in the task that owns the code.

1. **USB mic unplugged mid-recording** → `AudioRecord.read()` can return `ERROR_DEAD_OBJECT`; the service must close and fall back to built-in (`AUTO`) or pause (`EXTERNAL`), never spin or write silence. (Tasks 4–5)
2. **External mode selected with no external mic** → `PAUSED_INPUT_MISSING`, not `ERROR`; auto-resume when a device appears. (Task 5)
3. **Bluetooth `setCommunicationDevice()` fails** (missing `BLUETOOTH_CONNECT`, OEM quirk) → fall back to built-in, show a message, no crash. (Task 7)
4. **Another app records** → an external mic does not bypass Android's policy; the app must pause with accurate copy, not claim it can record. (Tasks 5, 7)
5. **Media routed to the USB device** → the phone speaker may go silent; document it and do not assume media is audible. (Task 8)
6. **Start tone captured by the first frames** → delay mic open by 250 ms after the 180 ms tone. (Task 5)

---

### Task 1: On-device audio route probe (spike)

**Files:**
- Create: `app/src/androidTest/java/io/github/markyu0401/everheard/capture/audio/AudioRouteProbeTest.kt`
- Create: `docs/spikes/2026-10-27-audio-input-probe.md`

**Interfaces:**
- Consumes: the app's `RECORD_AUDIO` permission and the existing instrumentation runner.
- Produces: a CSV/table of every input device's type, name, address, sample rates, channel counts, RMS for `VOICE_RECOGNITION` and `VOICE_COMMUNICATION`, `routedDevice`, and `isClientSilenced`; a go/no-go for USB and Bluetooth classic.

- [ ] **Step 1: Write the probe test**

`AudioRouteProbeTest` grants `RECORD_AUDIO` (and `BLUETOOTH_CONNECT` on API 31+) through `UiAutomation`, enumerates `AudioManager.getDevices(GET_DEVICES_INPUTS)` and `getAvailableCommunicationDevices()`, and for each input records 2 s at 16 kHz mono PCM16 with `AudioRecord.Builder`, `setPreferredDevice(device)`, and each source in `[VOICE_RECOGNITION, VOICE_COMMUNICATION]`. It computes RMS, logs `requested`, `preferred`, `routedDevice`, `isClientSilenced`, and writes `audio-route-probe.csv` to `context.getExternalFilesDir(null)`.

- [ ] **Step 2: Build the instrumentation APK**

Run: `./gradlew :app:assembleDebugAndroidTest`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Install and run the built-in baseline**

The debug app and the test APK must share a signing key. The release build on the phone uses a different key, so install the debug app first; this replaces the app and clears its data. Use a test device or back up first.

Run:
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w \
  -e class io.github.markyu0401.everheard.capture.audio.AudioRouteProbeTest \
  io.github.markyu0401.everheard.test/androidx.test.runner.AndroidJUnitRunner
adb pull /sdcard/Android/data/io.github.markyu0401.everheard/files/audio-route-probe.csv
```
Expected: `OK (1 test)`; CSV shows the built-in mic with non-zero RMS.

- [ ] **Step 4: Repeat with the DJI receiver over USB-C**

Connect the DJI Mic Mini receiver with its USB-C phone adapter; run the same command; confirm the receiver appears as `TYPE_USB_DEVICE` or `TYPE_USB_HEADSET`, 16 kHz initializes, and RMS follows the transmitter (tap/blow test). Record whether media playback moves to the receiver.

- [ ] **Step 5: Repeat with the DJI transmitter over Bluetooth**

Pair the transmitter directly; run the same command; record whether it appears as `TYPE_BLUETOOTH_SCO`, whether `VOICE_RECOGNITION` or `VOICE_COMMUNICATION` produces non-silent audio, and the RMS. If it returns silence, stop the Bluetooth path here.

- [ ] **Step 6: Write the spike report and gate**

Fill `docs/spikes/2026-10-27-audio-input-probe.md` with the device table, the USB verdict, the Bluetooth verdict, and the media-routing observation. Gate: USB must pass; Bluetooth passes only if it produces non-silent audio and its transcript is not worse than the built-in mic on a fixed test phrase.

- [ ] **Step 7: Commit**

```bash
git add app/src/androidTest/java/io/github/markyu0401/everheard/capture/audio/AudioRouteProbeTest.kt \
  docs/spikes/2026-10-27-audio-input-probe.md
git commit -m "spike(capture): probe USB and Bluetooth audio input routes"
```

---

### Task 2: Audio input model and resolver

**Files:**
- Create: `app/src/main/java/io/github/markyu0401/everheard/capture/audio/AudioInputMode.kt`
- Create: `app/src/main/java/io/github/markyu0401/everheard/capture/audio/AudioInputDevice.kt`
- Create: `app/src/main/java/io/github/markyu0401/everheard/capture/audio/AudioInputResolver.kt`
- Test: `app/src/test/java/io/github/markyu0401/everheard/capture/audio/AudioInputResolverTest.kt`

**Interfaces:**
- Consumes: nothing.
- Produces:
```kotlin
enum class AudioInputMode { AUTO, BUILT_IN, EXTERNAL }

enum class AudioInputKind { BUILT_IN, USB, BLUETOOTH_LE, BLUETOOTH_CLASSIC, OTHER }

data class AudioInputDevice(
    val id: Int,
    val kind: AudioInputKind,
    val name: String,
    val address: String,
    val sampleRates: List<Int>,
    val channelCounts: List<Int>
)

object AudioInputResolver {
    fun resolve(
        mode: AudioInputMode,
        devices: List<AudioInputDevice>,
        allowClassicBluetooth: Boolean = false
    ): AudioInputDevice?
}

fun parseAudioInputMode(raw: String?): AudioInputMode // unknown/null -> AUTO
```

Resolution policy:

| Mode | Order |
|---|---|
| `AUTO` | USB → LE Audio → built-in → classic Bluetooth (only if allowed) |
| `EXTERNAL` | USB → LE Audio → classic Bluetooth (only if allowed) → null |
| `BUILT_IN` | built-in → null |

- [ ] **Step 1: Write the failing tests**

```kotlin
@Test fun auto_prefers_usb_over_builtin_and_bluetooth()
@Test fun auto_prefers_le_audio_over_builtin()
@Test fun auto_prefers_builtin_over_classic_bluetooth()
@Test fun auto_falls_back_to_classic_bluetooth_when_it_is_the_only_input()
@Test fun external_prefers_usb_then_le_then_classic()
@Test fun external_returns_null_when_no_external_device()
@Test fun built_in_ignores_external_devices()
@Test fun classic_bluetooth_is_ignored_when_disabled()
@Test fun unknown_mode_parses_as_auto()
```

Use `Truth.assertThat`. Build devices with small helpers (`usb(id)`, `builtIn(id)`, `ble(id)`, `classic(id)`).

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew testDebugUnitTest --tests '*AudioInputResolverTest*'`
Expected: FAIL — unresolved reference / assertions fail.

- [ ] **Step 3: Implement the model and resolver**

Implement the enums, data class, `parseAudioInputMode`, and the table above. Filter `kind == OTHER` out. Sort equal-priority devices by `name`, then `id`, for deterministic output.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew testDebugUnitTest --tests '*AudioInputResolverTest*'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/io/github/markyu0401/everheard/capture/audio/AudioInputMode.kt \
  app/src/main/java/io/github/markyu0401/everheard/capture/audio/AudioInputDevice.kt \
  app/src/main/java/io/github/markyu0401/everheard/capture/audio/AudioInputResolver.kt \
  app/src/test/java/io/github/markyu0401/everheard/capture/audio/AudioInputResolverTest.kt
git commit -m "feat(capture): add audio input model and resolver"
```

---

### Task 3: Device catalog and preference store

**Files:**
- Create: `app/src/main/java/io/github/markyu0401/everheard/capture/audio/AudioDeviceCatalog.kt`
- Create: `app/src/main/java/io/github/markyu0401/everheard/data/settings/AudioInputStore.kt`
- Test: `app/src/test/java/io/github/markyu0401/everheard/data/settings/AudioInputStoreTest.kt`

**Interfaces:**
- Consumes: `AudioInputMode`, `AudioInputDevice`, `AudioInputKind`, `parseAudioInputMode` from Task 2.
- Produces:
```kotlin
class AudioDeviceCatalog(context: Context) {
    fun currentInputs(): List<AudioInputDevice>
    fun inputById(id: Int): AudioDeviceInfo?
    fun register(callback: AudioManager.AudioDeviceCallback)
    fun unregister(callback: AudioManager.AudioDeviceCallback)
}

class AudioInputStore private constructor(context: Context) {
    val mode: StateFlow<AudioInputMode>
    val bluetoothExperimental: StateFlow<Boolean>
    fun setMode(mode: AudioInputMode)
    fun setBluetoothExperimental(enabled: Boolean)
}

data class AudioInputSettings(
    val mode: AudioInputMode = AudioInputMode.AUTO,
    val bluetoothExperimental: Boolean = false
)

fun decodeAudioInputSettings(
    modeRaw: String?,
    bluetoothRaw: Boolean?
): AudioInputSettings

fun Context.audioInputStore(): AudioInputStore
```

- [ ] **Step 1: Write the failing store test**

```kotlin
@Test fun unknown_persisted_mode_falls_back_to_auto()
@Test fun null_mode_falls_back_to_auto()
@Test fun missing_bluetooth_flag_defaults_to_false()
@Test fun explicit_bluetooth_flag_is_preserved()
```

Use the pure `decodeAudioInputSettings` function; do not require Robolectric.

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew testDebugUnitTest --tests '*AudioInputStoreTest*'`
Expected: FAIL — `AudioInputStore`/`decodeAudioInputSettings` unresolved.

- [ ] **Step 3: Implement `AudioDeviceCatalog`**

Map `AudioDeviceInfo.type`:
- `TYPE_BUILTIN_MIC` → `BUILT_IN`
- `TYPE_USB_DEVICE`, `TYPE_USB_HEADSET` → `USB`
- `TYPE_BLE_HEADSET` → `BLUETOOTH_LE` (guard `Build.VERSION.SDK_INT >= 31`)
- `TYPE_BLUETOOTH_SCO` → `BLUETOOTH_CLASSIC`
- everything else → `OTHER`

Use `productName.toString().ifBlank { fallback }`; keep `address`, `sampleRates.toList()`, `channelCounts.toList()`. `currentInputs()` reads `audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)`, filters `isSource`, maps, and drops `OTHER`.

- [ ] **Step 4: Implement `AudioInputStore`**

Follow `AppSettingsStore`: `SharedPreferences("everheard_audio_input")`, `MutableStateFlow`, `get(context)` singleton. Keys: `mode`, `bluetooth_experimental`. Defaults come from `decodeAudioInputSettings`: `AUTO`, `false`.

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./gradlew testDebugUnitTest --tests '*AudioInputStoreTest*'`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/io/github/markyu0401/everheard/capture/audio/AudioDeviceCatalog.kt \
  app/src/main/java/io/github/markyu0401/everheard/data/settings/AudioInputStore.kt \
  app/src/test/java/io/github/markyu0401/everheard/data/settings/AudioInputStoreTest.kt
git commit -m "feat(capture): add audio device catalog and input preference store"
```

---

### Task 4: Preferred-device capture source

**Files:**
- Modify: `app/src/main/java/io/github/markyu0401/everheard/capture/audio/AudioRecordSource.kt`
- Test: `app/src/androidTest/java/io/github/markyu0401/everheard/capture/audio/AudioRecordSourceTest.kt`

**Interfaces:**
- Consumes: `AudioDeviceInfo` from the framework.
- Produces:
```kotlin
class AudioRecordSource(
    private val sampleRate: Int = 16_000,
    private val frameSamples: Int = 320,
    private val preferredDevice: AudioDeviceInfo? = null,
    private val audioSource: Int = MediaRecorder.AudioSource.VOICE_RECOGNITION
) {
    val routedDevice: AudioDeviceInfo?
    val isRecording: Boolean
    fun start(onFrame: (ShortArray) -> Unit, onError: (Int) -> Unit = {})
    fun stop()
}
```

- [ ] **Step 1: Write the failing instrumentation test**

`AudioRecordSourceTest` records 1 s with `preferredDevice = null` and asserts: `isRecording` is true, at least 10 frames arrive, and `routedDevice != null`. Add a second test with the USB device if present: `routedDevice.type` is `TYPE_USB_DEVICE` or `TYPE_USB_HEADSET`.

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew :app:assembleDebugAndroidTest` then `adb shell am instrument -w -e class io.github.markyu0401.everheard.capture.audio.AudioRecordSourceTest io.github.markyu0401.everheard.test/androidx.test.runner.AndroidJUnitRunner`
Expected: FAIL — `preferredDevice`/`routedDevice`/`onError` unresolved.

- [ ] **Step 3: Implement the source**

Switch to `AudioRecord.Builder` with `setAudioSource`, `setAudioFormat`, `setBufferSizeInBytes`. Call `setPreferredDevice(preferredDevice)` before `startRecording()`; log requested/preferred/routed. Add `addOnRoutingChangedListener` updating `routedDevice`. In the read loop, if `read == ERROR_DEAD_OBJECT`, call `onError(read)` and stop. Keep the existing 320-sample frame fill and `THREAD_PRIORITY_AUDIO`.

- [ ] **Step 4: Run the test to verify it passes**

Run the instrumentation command from Step 2; expected PASS for the built-in case, and PASS for USB when connected.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/io/github/markyu0401/everheard/capture/audio/AudioRecordSource.kt \
  app/src/androidTest/java/io/github/markyu0401/everheard/capture/audio/AudioRecordSourceTest.kt
git commit -m "feat(capture): pin AudioRecord to a preferred input device"
```

---

### Task 5: Capture service integration and input-missing state

**Files:**
- Modify: `app/src/main/java/io/github/markyu0401/everheard/capture/CaptureState.kt`
- Modify: `app/src/main/java/io/github/markyu0401/everheard/capture/CaptureStateMachine.kt`
- Modify: `app/src/main/java/io/github/markyu0401/everheard/capture/CaptureService.kt`
- Test: `app/src/test/java/io/github/markyu0401/everheard/capture/CaptureStateMachineTest.kt`

**Interfaces:**
- Consumes: `AudioInputStore`, `AudioDeviceCatalog`, `AudioInputResolver`, `AudioRecordSource`.
- Produces:
```kotlin
enum class CaptureState { /* existing + PAUSED_INPUT_MISSING */ }

sealed interface CaptureEvent { /* existing + InputMissing, InputAvailable */ }

data class CaptureContext(
    val scheduleAllows: Boolean = true,
    val batteryOk: Boolean = true,
    val inputAvailable: Boolean = true,
    val micBusy: Boolean = false,
    val excludedAppPlaying: Boolean = false
)
```

- [ ] **Step 1: Write the failing state-machine tests**

```kotlin
@Test fun input_missing_pauses_recording()
@Test fun input_available_resumes_from_input_pause()
@Test fun battery_low_wins_over_input_missing()
@Test fun schedule_block_wins_over_input_missing()
@Test fun mic_busy_does_not_override_input_missing()
@Test fun mic_available_with_no_input_enters_input_pause()
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew testDebugUnitTest --tests '*CaptureStateMachineTest*'`
Expected: FAIL — unresolved `PAUSED_INPUT_MISSING`/events.

- [ ] **Step 3: Implement the state**

Add `PAUSED_INPUT_MISSING` (service alive, mic closed). Add `InputMissing`/`InputAvailable`. `InputMissing` applies to `RECORDING`, `ARMED`, `PAUSED_SILENCE`; it leaves `STOPPED`, `ERROR`, `PAUSED_USER`, `PAUSED_SCHEDULE`, `PAUSED_BATTERY`, `PAUSED_THERMAL`, `PAUSED_APP_AUDIO`, and `PAUSED_MIC_BUSY` unchanged. `InputAvailable` resumes only from `PAUSED_INPUT_MISSING`. `resumeState` precedence: battery → schedule → input → mic busy → excluded app audio → `RECORDING`. Update the precedence comment.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew testDebugUnitTest --tests '*CaptureStateMachineTest*'`
Expected: PASS.

- [ ] **Step 5: Integrate into `CaptureService`**

- In `onCreate`, get `audioInputStore` and `AudioDeviceCatalog`; register an `AudioDeviceCallback`; collect `audioInputStore.mode` and `bluetoothExperimental` to re-evaluate.
- In `openMicrophone()`, resolve the input with `AudioInputResolver.resolve(mode, catalog.currentInputs(), allowClassicBluetooth)`. If `mode == EXTERNAL` and no device, apply `PAUSED_INPUT_MISSING` and return. If `mode == AUTO` and no external, use built-in. Pass `preferredDevice = catalog.inputById(resolved.id)` and `audioSource = VOICE_RECOGNITION` (Bluetooth overrides this in Task 7).
- On `AudioRecordSource.onError(ERROR_DEAD_OBJECT)`, post to the main handler, then close the mic and re-evaluate: `AUTO` falls back to built-in; `EXTERNAL` enters `PAUSED_INPUT_MISSING`.
- On `onAudioDevicesAdded`/`onAudioDevicesRemoved`, post `reevaluateInput()`: if recording and the resolved device changed, close/open; if `PAUSED_INPUT_MISSING` and a device appeared, transition with `InputAvailable`; if the active device disappeared and `EXTERNAL`, transition with `InputMissing`.
- In `updateNotification`, pass the active input name as `detail` (e.g., "USB mic · DJI Mic Mini") and add a `notification_state_paused_input_missing` string.
- Delay the first `openMicrophone()` by 250 ms after `playStartTone()` so the 180 ms beep is not captured.
- In `closeMicrophone()`/`onDestroy`, unregister the device callback and release the source.

- [ ] **Step 6: Run unit tests and manual hotplug checks**

Run: `./gradlew testDebugUnitTest` then on the Samsung: start capture with built-in, plug the DJI RX, confirm the notification detail changes and frames continue; unplug it, confirm fallback; set `EXTERNAL`, unplug, confirm `PAUSED_INPUT_MISSING` and auto-resume on replug. Record results in the task notes.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/io/github/markyu0401/everheard/capture/CaptureState.kt \
  app/src/main/java/io/github/markyu0401/everheard/capture/CaptureStateMachine.kt \
  app/src/main/java/io/github/markyu0401/everheard/capture/CaptureService.kt \
  app/src/test/java/io/github/markyu0401/everheard/capture/CaptureStateMachineTest.kt
git commit -m "feat(capture): route capture to the selected input and handle hotplug"
```

---

### Task 6: Settings UI, strings, and manifest

**Files:**
- Create: `app/src/main/java/io/github/markyu0401/everheard/ui/settings/AudioInputActivity.kt`
- Modify: `app/src/main/java/io/github/markyu0401/everheard/ui/settings/SettingsActivity.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-zh-rCN/strings.xml`
- Modify: `app/src/main/AndroidManifest.xml`

**Interfaces:**
- Consumes: `AudioInputStore`, `AudioDeviceCatalog`, `AudioInputResolver`, `AudioRecordSource`.
- Produces: a `LocalizedActivity` screen and a Settings row.

- [ ] **Step 1: Add strings**

Add English and `zh-rCN` entries for: `settings_microphone`, `audio_input_title`, `audio_input_subtitle`, `audio_input_auto`, `audio_input_auto_desc`, `audio_input_builtin`, `audio_input_external`, `audio_input_external_desc`, `audio_input_none`, `audio_input_test`, `audio_input_testing`, `audio_input_level`, `audio_input_note`, `audio_input_bt_experimental`, `audio_input_bt_warning`, `audio_input_permission_denied`, `audio_input_route`, `audio_input_silenced`, `audio_input_copy`, `audio_input_copied`, `notification_state_paused_input_missing`.

- [ ] **Step 2: Add the activity to the manifest**

`AudioInputActivity` with `android:exported="false"` and the app's theme/parent activity pattern.

- [ ] **Step 3: Build the screen**

Use `EverheardAppTheme`, `ScreenHeader`, `EverheardCard`, `SectionLabel`. Show three radio rows for `AUTO`, `BUILT_IN`, `EXTERNAL` with the descriptions above. Below, list `catalog.currentInputs()` filtered by `allowClassicBluetooth`; each row shows kind + name + sample rates, and a checkmark for the resolved device. Add a 5-second test button that starts `AudioRecordSource` with the resolved `AudioDeviceInfo`, displays RMS as a level bar, and reports "No signal" if RMS stays below a threshold. If capture is already running, the test panel must not open a second `AudioRecord`; show "Pause capture to test" and a button that pauses the service for the test. Add a note: USB is full-band; Bluetooth is call-quality; Android allows only one app to record at a time. Add a diagnostics block (route, sample rate, channels, `isClientSilenced`) and a copy button.

- [ ] **Step 4: Add the Settings row**

In the Capture section of `SettingsActivity`, add a `SettingsRow` with `Icons.Outlined.Mic`, title `settings_microphone`, subtitle = the mode label ("Automatic", "Phone microphone", "External microphone"), `showDivider = true`, opening `AudioInputActivity`.

- [ ] **Step 5: Request Bluetooth permission only when needed**

On API 31+, when the user selects `EXTERNAL` and a `BLUETOOTH_CLASSIC`/`BLUETOOTH_LE` device is present, request `BLUETOOTH_CONNECT` with `rememberLauncherForActivityResult`. If denied, keep USB working and show `audio_input_permission_denied`. Do not request it on app start.

- [ ] **Step 6: Manual UI check**

Run on the emulator (built-in only) and the Samsung (built-in + DJI RX): switch modes, plug/unplug, run the test, confirm the level bar and route text. Record results.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/io/github/markyu0401/everheard/ui/settings/AudioInputActivity.kt \
  app/src/main/java/io/github/markyu0401/everheard/ui/settings/SettingsActivity.kt \
  app/src/main/res/values/strings.xml app/src/main/res/values-zh-rCN/strings.xml \
  app/src/main/AndroidManifest.xml
git commit -m "feat(settings): add microphone selection and diagnostics"
```

---

### Task 7: Bluetooth classic route (experimental, gated by Task 1)

**Files:**
- Create: `app/src/main/java/io/github/markyu0401/everheard/capture/audio/BluetoothMicRoute.kt`
- Modify: `app/src/main/java/io/github/markyu0401/everheard/capture/CaptureService.kt`
- Modify: `app/src/main/java/io/github/markyu0401/everheard/capture/audio/AudioRecordSource.kt` (source selection only)
- Modify: `app/src/main/java/io/github/markyu0401/everheard/data/settings/AudioInputStore.kt`
- Modify: `app/src/main/java/io/github/markyu0401/everheard/ui/settings/AudioInputActivity.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/res/values/strings.xml`, `app/src/main/res/values-zh-rCN/strings.xml`

**Interfaces:**
- Consumes: `AudioDeviceInfo`, `AudioInputKind.BLUETOOTH_CLASSIC`, `AudioInputStore.bluetoothExperimental`.
- Produces:
```kotlin
class BluetoothMicRoute(context: Context) {
    val isActive: Boolean
    fun activate(input: AudioDeviceInfo): Boolean
    fun deactivate()
}
```

- [ ] **Step 1: Implement the route manager**

API 31+: find the sink in `audioManager.availableCommunicationDevices` matching the input's `type` and `address`; set `audioManager.mode = MODE_IN_COMMUNICATION`; `audioManager.setCommunicationDevice(sink)`; return the boolean. API 26–30: register a receiver for `ACTION_SCO_AUDIO_STATE_UPDATED`, call `startBluetoothSco()` and `isBluetoothScoOn = true`, wait up to 2 s for `SCO_AUDIO_STATE_CONNECTED`. `deactivate()` calls `clearCommunicationDevice()` (31+) or `stopBluetoothSco()`/`isBluetoothScoOn = false` (26–30), then restores `MODE_NORMAL` if this class set it.

- [ ] **Step 2: Add permissions**

Manifest: `MODIFY_AUDIO_SETTINGS`; `BLUETOOTH_CONNECT` (API 31+, runtime); `BLUETOOTH` with `android:maxSdkVersion="30"`. Keep `RECORD_AUDIO` unchanged.

- [ ] **Step 3: Integrate into the service**

If the resolved kind is `BLUETOOTH_CLASSIC` and `bluetoothExperimental` is true, call `activate()` before creating `AudioRecord`; use `MediaRecorder.AudioSource.VOICE_COMMUNICATION` if the Task 1 probe shows `VOICE_RECOGNITION` returns silence. If activation fails, fall back to built-in (`AUTO`) or `PAUSED_INPUT_MISSING` (`EXTERNAL`) and surface `audio_input_bt_warning`. Call `deactivate()` in `closeMicrophone()`.

- [ ] **Step 4: Add the experimental toggle**

In `AudioInputActivity`, add a switch "Experimental: Bluetooth microphone" bound to `bluetoothExperimental`; show classic Bluetooth devices only when it is on. Keep it default off.

- [ ] **Step 5: Gate on the probe**

If Task 1 shows Bluetooth capture is silent, or its transcript is worse than the built-in mic on the fixed phrase, stop: do not merge the route manager; document "use the DJI receiver over USB-C" in `docs/AUDIO-INPUT.md` and ship USB-only. If it passes, run the manual matrix below.

- [ ] **Step 6: Manual matrix**

Samsung S26 Ultra, screen off, unplugged: built-in vs DJI RX vs DJI TX BT; start with each connected, unplug mid-session, play media, take a phone call, record with another app, 1 h soak. Record route, RMS, transcript quality, battery, and any media-routing change in `docs/testing/audio-input-matrix.md`.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/io/github/markyu0401/everheard/capture/audio/BluetoothMicRoute.kt \
  app/src/main/java/io/github/markyu0401/everheard/capture/CaptureService.kt \
  app/src/main/java/io/github/markyu0401/everheard/capture/audio/AudioRecordSource.kt \
  app/src/main/java/io/github/markyu0401/everheard/data/settings/AudioInputStore.kt \
  app/src/main/java/io/github/markyu0401/everheard/ui/settings/AudioInputActivity.kt \
  app/src/main/AndroidManifest.xml app/src/main/res/values/strings.xml \
  app/src/main/res/values-zh-rCN/strings.xml
git commit -m "feat(capture): add experimental Bluetooth microphone routing"
```

---

### Task 8: Diagnostics, documentation, and release

**Files:**
- Modify: `app/src/main/java/io/github/markyu0401/everheard/ui/settings/AudioInputActivity.kt`
- Modify: `docs/DESIGN.md` §3.1
- Modify: `README.md`, `README.zh-CN.md`
- Modify: `app/build.gradle.kts`
- Create: `docs/testing/audio-input-matrix.md`
- Create: `build/RELEASE_NOTES-0.2.0.md`

**Interfaces:**
- Consumes: everything above.
- Produces: a shipped 0.2.0-alpha build with documented input support.

- [ ] **Step 1: Finish the diagnostics panel**

Show active mode, resolved device type/name/address, `AudioRecord.getRoutedDevice()`, sample rate, channel count, `AudioRecordingConfiguration.isClientSilenced`, and last RMS. Add "Copy diagnostics" that writes the same text to the clipboard.

- [ ] **Step 2: Fill the test matrix**

`docs/testing/audio-input-matrix.md`: rows for built-in, USB (DJI RX), Bluetooth classic (DJI TX), each with start-connected, hot-unplug, screen-off, media playback, phone call, concurrent recorder, 1 h soak, battery, transcript quality. Mark pass/fail with the build SHA.

- [ ] **Step 3: Update the design doc**

In `docs/DESIGN.md` §3.1, add: USB-C UAC is the preferred external path (full-band, `setPreferredDevice()`); Bluetooth classic is 8/16 kHz and experimental; external input does not bypass the concurrent-capture policy; media may be routed to a USB device with an output endpoint.

- [ ] **Step 4: Update the READMEs**

Add a feature bullet: "Optional USB-C microphone input (automatic or manual), with hotplug fallback; Bluetooth microphones are experimental." State Android 8.0+, pre-alpha, ~360 MB. Do not claim concurrent capture or iOS.

- [ ] **Step 5: Bump the version and write release notes**

`versionCode = 3`, `versionName = "0.2.0-alpha"`. `build/RELEASE_NOTES-0.2.0.md` follows the 0.1.1 template: new in this build, requirements/install, known limits (including "capture still pauses when another app holds the mic" and "Bluetooth is call-quality and experimental"), legal, SHA-256 placeholder.

- [ ] **Step 6: Run the full verification**

Run: `./gradlew lint testDebugUnitTest assembleDebug`
Expected: green. Then run the instrumentation suite on the Samsung; record `OK (n tests)`.

- [ ] **Step 7: Build and verify the release APK**

Run:
```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew assembleRelease
apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk
aapt dump badging app/build/outputs/apk/release/app-release.apk | head
shasum -a 256 app/build/outputs/apk/release/app-release.apk
```
Expected: signature valid, version `0.2.0-alpha`, SHA-256 recorded in the release notes.

- [ ] **Step 8: Publish and sync**

Follow the project release process: tag `v0.2.0-alpha`, `gh release create` with the APK + `SHA256SUMS.txt` + notes, copy to the NAS `app/releases/0.2.0/`, and update the launch-kit version references. Use the `everheard-project` skill's commands; do not commit the keystore.

- [ ] **Step 9: Commit the docs**

```bash
git add docs/DESIGN.md docs/testing/audio-input-matrix.md README.md README.zh-CN.md \
  app/build.gradle.kts build/RELEASE_NOTES-0.2.0.md
git commit -m "docs: document external audio input and release 0.2.0-alpha"
```
