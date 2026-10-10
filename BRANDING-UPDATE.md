# OTPOTG branding update

- App name: **OTPOTG**
- Android application ID / namespace: **us.norec.otpotg**
- Java source package moved to `app/src/main/java/us/norec/otpotg/`.
- Launcher icon: selected white D10 on dark slate background; PNG resources installed in `mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}`.
- Manifest declares `@mipmap/ic_launcher` and `@mipmap/ic_launcher_round`.
- License: **GPL-3.0-or-later**, existing `LICENSE` and source SPDX headers.
- LAD now looks for JDK 17 and rejects incompatible Java 25.

## Applying to your Windows project

Back up your current project first. Extract this archive into a **new directory**, then copy over your local Gradle wrapper files (`gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`) and `local.properties` as needed. The provided starter ZIP does not contain those binaries. Alternatively use Android Studio to open the extracted project and regenerate the Gradle wrapper. Do not overwrite your existing project without backing it up.

Build with `./gradlew.bat :app:assembleDebug --console=plain` using Temurin 17.

**Important:** `us.norec.otpotg` installs as a separate app from the older `us.norec.otgotp`; Android will not migrate local pad databases automatically. Export or preserve anything important before removing the older app. Never share private pads or key slips through a public repository.

The project has not been compiled in this environment. Verify the APK, manifest, icon, and smoke tests on your machine before public release.
