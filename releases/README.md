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
