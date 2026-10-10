# OTPOTG — F-Droid-oriented Android starter POC (v0.1.1)

**Native Java Android application, entirely functional on its own without a PC after installation.**

## Open in Android Studio

1. Unzip `OTPOTG.zip`.
2. In Android Studio choose **File → Open** and select the extracted `OTPOTG` folder.
3. Allow Gradle to sync (the *development machine* may need to download Android Gradle Plugin and Android SDK components; the **app itself never needs internet**).
4. This archive includes Gradle wrapper **configuration only**, not the executable wrapper scripts or JAR. Choose a locally installed **Gradle 8.9** distribution in Gradle settings, or run `gradle wrapper --gradle-version 8.9` from the project directory using installed Gradle, then sync. See `gradle/wrapper/README.md`.
5. Build/Run on an emulator or connected Android device, or choose **Build → Build APK(s)** to generate a debug APK. This archive contains source, **not a precompiled APK**.

AGP: 8.7.3; compileSdk: 35; targetSdk: 34; minSdk: 26; Java 17.

## Features included

- Standalone pad store: manually add physical D10 digits.
- Encrypt & Prune: consumes 12 ID digits plus message-length pad digits; persists the remaining suffix to app-private JSON storage.
- Decrypt using the matching key slip (does not need the database).
- Windows DICE / OTP v0.17+ **V2** compatible decimal message encoding: `999998` + codec ID (0 UTF-8, 1 ASCII, 2 Latin-1) + 3-digit byte offset + 9-digit byte count + transformed DEC3 bytes + zero padding to configured block.
- V1 padded and original legacy UTF-8 DEC3 decryption compatibility.
- Padding block settings 5/30/300/600, byte offset 0–255, charset selection.
- Gauge capped visually at 100%; warning at 100% and pink at 200%; actual percentages still shown.
- Development-only synthetic digits and persistent test-data warning.
- Retire remaining pad and purge database controls.
- Local ciphertext and key-slip export through Android's document picker.
- Offline core self-test in Settings → Dev.

## Security and operational limitations

**Experimental software, not security-audited. Do not use for important secrets.**

- `android.permission.INTERNET` is deliberately absent. No telemetry, advertising, networking, or cloud sync code is present. OS and other apps may still communicate.
- Android automatic app backup is disabled; users control any external exports.
- Keys and pad digits are stored as plaintext in app-private storage. A compromised or rooted device can expose them. Deletion/pruning does not guarantee forensic flash erasure.
- OTP confidentiality requires genuinely uniform, independent, secret, never-reused digits. Pseudorandom development digits are not physical rolls.
- Decimal OTP provides **no message authentication**; ciphertext can be modified without detection.
- Current workflow prunes on encryption and shows the only remaining key slip in memory. **Export the key slip immediately**; crashes or app restarts before export can make messages unrecoverable.
- Key-slip export writes plaintext key material to the user-selected destination; protect it. Clipboard use, if manually selected, may expose secrets to the system/other apps.
- No automatic pad synchronization. **No pad database import/export UI in this starter POC.** For two-way communications use independent directional pads; never let multiple devices encrypt from the same allocation range.
- Existing Windows JSON database is not directly imported. The **ciphertext and key-slip formats**, not the database file layout, are interoperable.
- All source files are Java/Android SDK; no runtime libraries, analytics SDKs, or networking dependencies.

## Compatibility detail

The first 12 decimal digits of ciphertext are the message ID. The following digits are ciphertext.
The key slip contains the matching message ID and exactly one pad digit per ciphertext digit.
Decrypt by decimal digitwise subtraction modulo 10, then parse the V2/V1/legacy payload.

## Testing

`core-tests/CoreSmokeTest.java` runs 40 standalone Java core tests. From the project root:

```bash
mkdir -p core-tests/classes
javac -d core-tests/classes app/src/main/java/us/norec/otpotg/OtpCore.java core-tests/CoreSmokeTest.java
java -cp core-tests/classes CoreSmokeTest
```

The core tests passed in the build environment. **An Android SDK/Gradle build and device GUI test were not possible in the current environment**, so you may encounter integration issues when first syncing in Android Studio.

## License and F-Droid

The source code is **GPL-3.0-or-later**. See `LICENSE` and `CONTRIBUTORS.md`.
The F-Droid listing draft is in `fastlane/metadata/android/en-US/`.
The submission checklist is in `docs/F-DROID.md`.
This project is **not currently listed on F-Droid**; public hosting, a complete
Gradle wrapper, device testing, screenshots, release tagging, and submission
are still required. The app's network-free design does not prohibit the build
computer from downloading Gradle dependencies.

## LAD developer launcher (Windows)

Double-click `Launch-LAD.cmd` to open the **Lazy Ass Developer** GUI. See [LAD-README.md](LAD-README.md) for actions and requirements.
