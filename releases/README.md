# Releases

APKs are **not** stored in git (avoids GitHub’s large-file warnings).

Download private builds from **GitHub Releases**:

https://github.com/OverclockedWRX/AndroidWRXOBDII/releases

Only collaborators on this private repo can see or download them.

### Publishing a new build (maintainers)

```bash
./gradlew :app:assembleDebug
gh release create vX.Y.Z app/build/outputs/apk/debug/app-debug.apk \
  --title "WRX Race HUD vX.Y.Z" \
  --notes "Release notes here."
```
