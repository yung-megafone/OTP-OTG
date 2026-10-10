# F-Droid readiness and submission checklist

## Done in the source project

- [x] GPL-3.0-or-later source license and SPDX source headers
- [x] No app Internet permission, ads, analytics, or proprietary SDK
- [x] Standard Android Gradle Plugin project; Java-only runtime
- [x] Human-readable F-Droid/fastlane listing text (en-US)
- [x] Git ignore for build output, local configuration, signing keys and OTP exports
- [x] Gradle 8.9 wrapper **configuration** (NOT the wrapper JAR/scripts)
- [x] Offline core smoke tests

## Before public release / submission

- [ ] Add project maintainer copyright attribution to CONTRIBUTORS.md
- [ ] Publish complete source to a public Git repository; choose a stable URL
- [ ] Generate, verify, and commit the complete Gradle wrapper (gradlew, gradlew.bat, gradle-wrapper.jar)
- [ ] Build a debug APK and run on a real device; resolve Android Studio warnings
- [ ] Test API 26 and recent Android versions, including airplane mode
- [ ] Confirm merged manifest contains no INTERNET/network permissions
- [ ] Fix crash-resilient key-slip export before calling it production-ready
- [x] Add launcher icon and update listing name; screenshots still required
- [ ] Tag a tested release in Git (e.g., v0.1.0)
- [x] Set applicationId to `us.norec.otpotg` before first release
- [ ] Prepare a metadata entry for the official fdroiddata repository using the real Git URL, license, source tag and build instructions
- [ ] Submit via F-Droid's current inclusion process; acceptance is not guaranteed

## No Internet means NO Internet for the app

The Gradle *development build* can use Google Maven and Maven Central to
retrieve the Android Gradle Plugin and SDK dependencies. The installed APK
does not request Internet permission. An entirely offline **build machine**
is a different goal and requires pre-cached build tools.

## Release signing

Android Studio debug APKs are suitable for local testing only. The F-Droid
repository normally signs its own packages. Reproducible-build workflows
require additional setup and verification; do not assume a locally signed APK
will be bit-identical to an F-Droid build. Never commit private signing keys.
