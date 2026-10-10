# LAD — Lazy Ass Developer for OTPOTG

Windows-only local development helper. GPL-3.0-or-later. No Android app networking is added.

## Start

Double-click `Launch-LAD.cmd` in the extracted project folder. Windows PowerShell 5.1 and WinForms are included in ordinary Windows installations. The launcher's execution-policy bypass applies only to that invocation; it does not change machine policy.

Buttons: **Environment**, **Core Tests**, **F-Droid Audit**, **Build Debug**, **Build Release**, **Install Debug**, **Read Logs**, **Setup Wrapper**.

- **Environment:** reports Java, Android SDK, Gradle, ADB, Git, and attached Android devices.
- **Core Tests:** compiles and runs the standalone OTP format/round-trip tests using JDK 17.
- **F-Droid Audit:** source-only readiness checklist, not an official acceptance or security audit.
- **Build Debug:** `:app:assembleDebug`.
- **Build Release:** `:app:assembleRelease` (normally unsigned, no embedded credentials).
- **Install Debug:** `adb install -r` using the debug APK. Enable USB debugging and authorize the PC on the device.
- **Read Logs:** extracts recent ADB logs. Logs may contain sensitive material; inspect before sharing.
- **Setup Wrapper:** requires **locally installed Gradle 8.9** accessible as `gradle.bat`, then generates the wrapper files. Once committed, the wrapper is used for subsequent builds.

The starter project currently lacks the Gradle wrapper JAR and launchers. Until `Setup Wrapper` succeeds, you can build from Android Studio or use locally installed Gradle 8.9. Android Studio's bundled JBR is auto-detected at common installation locations. The SDK is found through `ANDROID_HOME`, `ANDROID_SDK_ROOT`, or the default `%LOCALAPPDATA%\Android\Sdk` path.

## CLI usage

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\LAD.ps1 -Cli -Action Tests
powershell -NoProfile -ExecutionPolicy Bypass -File .\LAD.ps1 -Cli -Action Audit
powershell -NoProfile -ExecutionPolicy Bypass -File .\LAD.ps1 -Cli -Action BuildDebug
```

Output is recorded in `lad-logs/`, which should remain local. Never publish debug logs containing sensitive information, pad material, or exported key slips.

## F-Droid note

LAD is a developer-side helper and does not ship inside the APK. The app has no `INTERNET` permission. A development PC may need network access for Gradle/Android SDK dependency downloads; this does not imply any app networking. Review the merged manifest, dependency licenses, and APK before release.

## Windows PowerShell 5.1 compatibility

`LAD.ps1` is saved as UTF-8 **with BOM** so Windows PowerShell 5.1 parses Unicode punctuation correctly. If upgrading an existing checkout, replace `LAD.ps1` with the patched file.
