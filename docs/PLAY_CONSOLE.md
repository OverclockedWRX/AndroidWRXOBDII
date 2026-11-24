# Play Console checklist (WRX Race HUD)

Nothing here has been submitted. This is a prep list for when the app is uploaded to Play
(Internal Testing / Internal App Sharing need none of the review steps; Closed/Open/Production do).

## Foreground service declaration (App content -> Foreground service permissions)

`ObdService` declares `foregroundServiceType="connectedDevice|specialUse|location"` and the
matching permissions: `FOREGROUND_SERVICE_CONNECTED_DEVICE`, `FOREGROUND_SERVICE_SPECIAL_USE`,
`FOREGROUND_SERVICE_LOCATION`. Play asks for a declaration per type.

| Type | Why the app needs it | Form answer (draft) |
| --- | --- | --- |
| `connectedDevice` | Holds the Bluetooth / USB link to the user's ELM327/STN OBD-II adapter while the HUD is shown | "Maintains a user-initiated Bluetooth connection to an OBD-II adapter in the car to read live engine data." |
| `location` | GPS speed, lap/track data and posted speed limit shown on the HUD while driving | "Reads device location while the user has the HUD running, to show vehicle speed and speed limit. Stops when the user stops the HUD." |
| `specialUse` | In-car telemetry plus an offline race-HUD simulator that is not covered by a standard type | Subtype string (manifest property): "In-car OBD telemetry and offline race HUD simulator" |

### Justification text (draft, paste and adjust)

> WRX Race HUD is a read-only OBD-II dashboard for a Subaru WRX. When the driver taps Start, the
> app runs a foreground service that (1) keeps a Bluetooth connection to the driver's OBD-II
> adapter, (2) reads GPS speed from the phone, and (3) updates a full-screen HUD and Android Auto
> screen. It is always user-initiated, shows an ongoing notification with a Stop action, and
> never runs in the background without it. Losing the service while driving would blank the HUD
> and drop the adapter connection. WorkManager / short tasks cannot keep a live Bluetooth
> session, so a foreground service is required.

### Video demo requirement

Play requires a short video (unlisted YouTube link, English or captioned) for each sensitive
foreground service type. Record once and reuse for all three:

1. Fresh install, grant Bluetooth and Location permissions at the prompts.
2. Tap Start: show the ongoing notification.
3. Show live values (RPM, speed) on the HUD with an adapter connected (or the simulator for the
   specialUse segment, clearly labelled).
4. Leave the app (home screen) and show the notification still present; then tap Stop.

## Location permissions

- Foreground location only (`ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION`). The app does NOT
  request `ACCESS_BACKGROUND_LOCATION`; keep it that way, otherwise the separate background-location
  review (video, prominent in-app disclosure, justification) applies.
- Data safety form: location is used on-device for display; if the posted speed limit lookup
  (Overpass) is enabled, approximate coordinates are sent to that service, so declare "Location
  (approximate/precise), shared, optional".
- A privacy policy URL is required once location is declared.

## Android Auto / Car App Library

- The Car App service uses category `androidx.car.app.category.POI` (not IOT, which many head
  units hide). For Internal Testing / Internal App Sharing the Play quality review does not apply.
- Opening the track to Closed/Open/Production triggers Car App quality review for the declared
  category. A race HUD is not a classic POI app, so expect questions; the alternative is to
  keep Android Auto distribution on Internal Testing only.
- Real-car testing needs a build installed from a trusted source (Internal Testing / Internal App
  Sharing); adb sideloads do not appear in the Android Auto launcher.

## Other

- Target SDK / permission declarations: `FOREGROUND_SERVICE_*` permissions need the declaration
  above before a release can roll out beyond internal tracks.
- OBD data never leaves the device; VIN and raw replies are not logged in release builds.
