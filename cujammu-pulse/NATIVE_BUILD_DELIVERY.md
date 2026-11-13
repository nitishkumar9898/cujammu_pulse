# Shared Android build checkpoint

Date: 2026-10-10. Immediate milestone: isolated local professor browser review. APK build and device acceptance remain unverified.

## Completed

The shared Expo client generated 36 Android project files from the template shipped with the installed, locked Expo 56.0.23 package. The successful command ran from `cujammu-pulse`:

```powershell
rtk proxy node node_modules/expo/bin/cli prebuild --platform android --no-install --template ./node_modules/expo/template.tgz --skip-dependency-update react,react-native,expo
```

Prebuild changed only the Android package script from `expo start --android` to `expo run:android`. Existing authentication dependencies, application scheme, secure-store plugin, shared TypeScript source and exported browser assets were preserved. Native files remain generated/ignored under the existing repository policy.

The generated wrapper pins Gradle 9.3.1 and its official distribution SHA-256 `b266d5ff6b90eada6dc3b20cb090e3731302e553a27c5d3e4df1f0d76beaff06`, checked against the [official checksum](https://services.gradle.org/distributions/gradle-9.3.1-bin.zip.sha256). Generated Gradle configuration disables automatic SDK/JDK installation and uses the already established short JVM socket directory on this Windows host. The release build no longer inherits the public debug signing key; production signing requires its separate reviewed configuration.

## Actual attempt and blockers

`:app:assembleDebug --no-daemon` ran with process-local OpenJDK 21.0.10, the installed Android SDK and `-Djdk.net.unixdomain.tmpdir=C:\tools\pulse-jvm-sockets`. It exited 1 before project compilation because the official Gradle distribution download timed out while connecting with the wrapper's 10000 ms timeout. No debug APK was produced.

The locked React Native 0.85.3 native version catalogue requires:

| Component | Required | Installed evidence |
|---|---|---|
| Android platform | compile/target API 36 | API 35 and 36.1; exact API 36 absent |
| Android build tools | 36.0.0 | 34.0.0, 35.0.0, 36.1.0, 37.0.0; exact version absent |
| Android NDK | 27.1.12297006 | NDK directory absent |
| Gradle | 9.3.1 | Wrapper pinned; download attempt timed out |
| Android Gradle plugin | 8.12.0 | Selected by the locked native dependency catalogue; compilation not reached |

No system SDK/NDK components were installed and the application was not retargeted to older APIs. Prebuild also reported that the requested Android `userInterfaceStyle` handling requires `expo-system-ui`; it is not currently installed. That warning is a separate compatibility item and was not hidden by changing the requested theme policy.

Before a future native build, review and provision the exact supported Android components, verify the pinned Gradle download, reproduce the generated hardening settings, configure approved signing and run real emulator/device tests. Native identity/provider, secure file handling and camera adapters retain the boundaries in `NATIVE_IDENTITY_DELIVERY.md`. The legacy Kotlin prototype APK is not evidence for this shared client.
