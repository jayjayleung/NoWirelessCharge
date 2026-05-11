# Repository Guidelines

## Project Structure & Module Organization
`app/` is the only Gradle module. Main Android code lives in `app/src/main/java/com/wwm/nowirelesscharge/`, resources in `app/src/main/res/`, and the manifest in `app/src/main/AndroidManifest.xml`. Build outputs land under `app/build/` and `app/release/`.

`analysis_out/` contains decoded framework and SystemUI artifacts used for reverse-engineering and compatibility work; treat it as reference material, not app source. `tools/` holds supporting binaries such as `payload-dumper-go.exe`. CI workflow definitions live in `.github/workflows/`.

## Build, Test, and Development Commands
Use the Gradle wrapper from the repo root and match CI with JDK 17.

- `.\gradlew.bat clean` removes Gradle build output.
- `.\gradlew.bat assembleDebug` builds a local debug APK.
- `.\gradlew.bat assembleRelease` builds a release APK; signing is applied only when `RELEASE_STORE_FILE`, `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS`, and `RELEASE_KEY_PASSWORD` are set.
- `.\gradlew.bat lint` runs Android lint checks before review.

GitHub Actions builds debug APKs on branch/PR pushes and publishes signed release APKs for `v*` tags.

## Coding Style & Naming Conventions
Follow the existing Java style: 4-space indentation, braces on the same line, and short method bodies where possible. Keep package names lowercase (`com.wwm.nowirelesscharge`), class names in `UpperCamelCase`, methods/fields in `lowerCamelCase`, and constants in `UPPER_SNAKE_CASE`.

Prefer explicit logging around BYD reflection paths and keep compatibility comments close to the code they explain. Resource names should stay Android-standard lowercase snake case, for example `ic_launcher_round`.

## Testing Guidelines
This repository currently has no committed `src/test` or `src/androidTest` suites, so manual verification is still required on target hardware. At minimum, confirm the app disables wireless charging when launched and after boot.

When adding automated coverage, place JVM tests in `app/src/test/java/` and device tests in `app/src/androidTest/java/`. Name tests after the behavior under test, such as `BYDWirelessChargerTest`.

## Commit & Pull Request Guidelines
History follows concise conventional prefixes such as `fix:`, `fix(byd):`, `fix(ci):`, `ci:`, and `chore:`. Keep subjects imperative and scoped to one change.

PRs should explain the user-visible effect, note the BYD/DiLink version tested, link any issue, and include screenshots only for UI or manifest-driven behavior changes. Do not commit keystores or secret material; release signing is handled through GitHub Actions secrets.
