# Releases

APKs are **not** stored in git (avoids GitHub’s large-file warnings).

Download builds from **GitHub Releases**:

https://github.com/OverclockedWRX/AndroidWRXOBDII/releases

Anyone can download them; the code is MIT licensed (see ../LICENSE).

### Publishing a new build (maintainers)

```bash
./gradlew :app:assembleDebug
gh release create vX.Y.Z app/build/outputs/apk/debug/app-debug.apk \
  --title "WRX Race HUD vX.Y.Z" \
  --notes "Release notes here."
```

## Changelog

### v1.0.0

Rewrite of WRX Race HUD. **Not yet car-tested** (nor tested on a phone): checked by unit and screenshot tests only. v0.33.0 stays available.

**Install:** this APK is debug-signed with a different debug key than earlier releases, so it cannot update an installed 0.x build. Uninstall the old app first (settings are reset).

- Faster OBD polling: multi-PID requests and response-count hints, with a Legacy polling mode as fallback; poll rate panel
- Auto-reconnect to the adapter; CSV logging moved off the main thread
- Android Auto keeps updating from a shared telemetry source
- Smoother HUD rendering and animation loops that pause in the background
- Eight themes: Dark / Modern (default), Light, High contrast, OLED Black, Claude, Handheld, Night, Retro LCD (Classic and Pinky now map to Dark, E-ink to Light)
- Motion setting: Full, Reduced, Off
- Security: OBD read-only guard and PID mapping checks tightened; speed-limit lookups go only to overpass-api.de servers (off by default); Gradle download checksum pinned
- Updated build stack (AGP 9.5, Gradle 9.9, Kotlin 2.4)
