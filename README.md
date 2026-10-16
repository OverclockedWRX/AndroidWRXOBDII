# WRX Race HUD

Android 15+ race HUD for **2022–present Subaru WRX (VB / FA24)** using an **OBDLink MX+**.

## Features

- AiM-style shift lights and bar tachometer with frame-timed RPM interpolation
- GPS speed display (phone GPS)
- Calculated gear (6MT or SPT/CVT ratios + tire size)
- Toggleable gauges: coolant, oil temp, IAT, boost, battery, odometer (when ECU supports it)
- Zone-colored tach bar (green → amber → red toward redline)
- Warning lamps: CEL, low fuel, temps, overboost, battery, OBD/GPS link, knock/DAM (when Mode 22 DIDs are confirmed)
- Simulator mode for UI development without a car
- Settings stored in DataStore

## Requirements

- Android 15 (API 35) or higher
- OBDLink MX+ paired over classic Bluetooth
- Location permission for GPS speed
- Bluetooth connect permission for the adapter

## Build

Open the project in Android Studio (Ladybug or newer) or:

```bash
./gradlew :app:assembleDebug
```

Install the APK from [`releases/WRXRaceHUD-0.1.0-debug.apk`](releases/WRXRaceHUD-0.1.0-debug.apk), or build with Gradle and use `app/build/outputs/apk/debug/`.

## First run

1. Pair the OBDLink MX+ in Android Bluetooth settings.
2. Launch **WRX Race HUD**.
3. Open Settings (gear icon) and turn **off** “Use simulator” to connect live.
4. Keep the phone landscape and mounted as a dash display.

## Vehicle notes

| Channel | Source |
| --- | --- |
| RPM, coolant, IAT, MAP/boost, voltage, MIL, fuel % | SAE Mode 01 |
| Speed (display) | Phone GPS |
| Gear | Calculated from OBD speed + RPM + WRX ratios |
| Oil temp | Auto-probed: Mode 01 `5C`, then Mode 22 `0113` / `1153` (header `7E0`) |
| Oil pressure | Not available (VB has a switch only, no PSI sensor on OBD) |
| Odometer | Mode 01 `A6` when supported — many 2022+ WRXs return unsupported; tile shows — |
| Coolant vs “water temp” | Same ECT sensor on FA24 (one Coolant tile) |

## Confirming oil-temp DIDs

On connect the app tries each oil-temp source and keeps the first reply that decodes to a plausible temperature (−20…160 °C). The footer shows `OIL:01 5C`, `OIL:22 0113`, or `OIL:none`.

To confirm manually with an MX+ terminal (or Torque custom PID test):

1. Pair the adapter, ignition ON / engine running.
2. Init: `ATZ`, `ATE0`, `ATH0`, `ATSP6`, `ATSH7E0`.
3. Try `015C` — expect `41 5C XX` → temp = XX − 40 °C.
4. If that fails, try `220113` then `221153` — expect `62 …` with data byte(s); scale is typically A−40 °C.
5. Cross-check against a known-good oil temp (Accessport / another gauge). If the live value tracks within a few degrees, that DID is confirmed — tell us and we hard-code it as the primary.

`adb logcat -s ObdPoller` also prints each probe reply during connect.

## License

Private project. All rights reserved unless otherwise noted.
