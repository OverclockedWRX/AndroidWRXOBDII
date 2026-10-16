# WRX Race HUD

Android 15+ race HUD for **2022–present Subaru WRX (VB / FA24)** using an **OBDLink MX+**.

## Features

- AiM-style shift lights and bar tachometer with frame-timed RPM interpolation
- GPS speed display (phone GPS)
- Calculated gear (6MT or SPT/CVT ratios + tire size)
- Toggleable gauges: coolant, oil temp, oil pressure, IAT, boost, battery
- Warning lamps: CEL, low fuel, oil pressure, temps, overboost, battery, OBD/GPS link, knock/DAM (when Mode 22 DIDs are confirmed)
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

Install the APK from `app/build/outputs/apk/debug/`.

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
| Oil temp / oil pressure | Mode 22 candidates — shown only after DID confirmation |
| Coolant vs “water temp” | Same ECT sensor on FA24 (one Coolant tile) |

## License

Private project. All rights reserved unless otherwise noted.
