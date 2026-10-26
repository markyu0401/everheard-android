# ROM compatibility matrix

Android's foreground-service rules are only half the problem. Chinese OEM ROMs add autostart managers, battery "optimisation", and a separate background-microphone policy. This matrix is the test plan and the in-app guidance source.

## Supported target

| Vendor / ROM | Android base | Status | Required user settings | Notes |
|---|---|---|---|---|
| Samsung One UI (Android 16) | 16 | Primary dev device — untested | Battery → Background usage limits → add app to "Never sleeping apps"; turn off "Put unused apps to sleep" | Most predictable FGS behaviour |
| Xiaomi HyperOS 3 | 16 | Untested | App info → Autostart on; Battery → No restrictions; lock the app in Recents | "Background microphone" may need a separate grant |
| OPPO ColorOS 17 | 16 | Untested | Battery → App battery management → Allow background activity; Autostart on | Aggressive kill after screen-off |
| vivo OriginOS 6 | 16 | Untested | Background power consumption management → Allow; Autostart on | Similar to ColorOS |
| Honor MagicOS 10 | 16 | Untested | App launch → Manage manually (all three toggles); Battery → Allow background | |
| Huawei HarmonyOS 4.x (Android-based) | 12+ | Untested | App launch → Manage manually; Battery → Allow background | EMUI-style manager |
| Huawei HarmonyOS NEXT (5.x) | — | **Not supported** | — | Not Android; APKs do not run |
| AOSP / Pixel | 16 | Untested | None | Reference behaviour |

## What "works" means

A ROM passes when all of these hold for 8 hours with the screen off:

1. The foreground service is still alive and the notification is present.
2. `AudioRecord` is reading frames (the app logs a heartbeat, not content).
3. The mic indicator turns off when the schedule pauses capture.
4. After a forced stop from Recents, the app posts a "tap to resume" notification within 5 minutes.
5. Battery drain in armed-but-paused mode is under 1 %/h.

## In-app guidance

Settings → Reliability shows the detected manufacturer and a three-step checklist with deep links where they exist:

- `Settings.ACTION_APPLICATION_DETAILS_SETTINGS` (all ROMs)
- `Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` (where allowed)
- Vendor-specific autostart screens are not stable public APIs; the app shows text instructions instead of guessing an Intent.

## Test log

| Date | ROM | Device | Result | Notes |
|---|---|---|---|---|
| — | — | — | — | Fill in during M0-T8 |
