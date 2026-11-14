This is a Kotlin Multiplatform project targeting Android, iOS.

### Scaffold inventory

The existing Gradle root is `Roy`. It includes `:shared` and `:androidApp`. The
separate Xcode app consumes the static `Shared` framework. Backend runs in a
separate build and supplies the accepted local API, auth and WebSocket
contracts and fixtures.

- `:shared` uses `com.example.roy`. Its existing source sets are `commonMain`,
  `androidMain`, `iosMain`, `commonTest`, `androidHostTest` and `iosTest`. The
  iOS targets are `iosArm64` and `iosSimulatorArm64`. Gradle also configures an
  Android device test source set without starter tests in its directory.
- Both entry points already call the shared `App()`: Android `MainActivity`
  via `setContent` and SwiftUI `ComposeView` via iOS `MainViewController`.
- Planned packages under the existing shared common root are
  `presentation/theme` for theme roles, `presentation/components` for
  primitives, `presentation/core` for MVI and `presentation/navigation` for
  routes. Later auth work uses `presentation/auth`, `domain/auth` and
  `data/auth`, with presentation and data depending on domain. These packages
  are not present in the baseline.
- Compose Resources already uses `shared/src/commonMain/composeResources`.
  Russian MVP UI strings go in its `values/strings.xml` as the default
  language. The Android launcher label remains an Android resource.

Baseline on 2026-11-11 before editing application sources:

- Git opened on unborn `master`, with the existing scaffold untracked and
  no local refs. The first commit records that starting tree on local `main`.
  Ignored build output, IDE state and `local.properties` stay local.
- Windows x86-64, Gradle 9.5.1 wrapper, Azul JDK 21, Kotlin 2.4.21,
  Compose Multiplatform 1.12.2, AGP 9.1.1, Android SDK 37 (min 26).
- `gradlew.bat --offline --console=plain projects tasks --all :androidApp:assembleDebug :shared:testAndroidHostTest`
  returned BUILD SUCCESSFUL. Two starter host tests passed. The Android APK
  SHA-256 was `8046a55748142be59fa0da3ebc44a87caf65bdd56b444356c7bae2f77e8e9e4c`.
- An initial sandboxed `adb devices -l` failed to start its daemon.
  With local SDK execution, the Pixel_9 AVD booted on API 36. On
  `emulator-5554`, `adb install -r` succeeded and
  `adb shell am start -W -n com.example.roy/.MainActivity` returned
  `Status: ok`. The UIAutomator hierarchy contained the starter button
  `Click me!`.
- iOS build/link/run **BLOCKED** on Windows. Gradle explicitly disabled
  `:shared:iosSimulatorArm64Test` because the simulator requires macOS.
  Resume on macOS with Xcode, then run the simulator test and build/run the
  `iosApp` scheme on a compatible simulator.

* [/iosApp](./iosApp/iosApp) contains an iOS application. Even if you’re sharing your UI with Compose Multiplatform,
  you need this entry point for your iOS app. This is also where you should add SwiftUI code for your project.

* [/shared](./shared/src) is for code that will be shared across your Compose Multiplatform applications.
  It contains several subfolders:
  - [commonMain](./shared/src/commonMain/kotlin) is for code that’s common for all targets.
  - Other folders are for Kotlin code that will be compiled for only the platform indicated in the folder name.
    For example, if you want to use Apple’s CoreCrypto for the iOS part of your Kotlin app,
    the [iosMain](./shared/src/iosMain/kotlin) folder would be the right place for such calls.
    Similarly, if you want to edit the Desktop (JVM) specific part, the [jvmMain](./shared/src/jvmMain/kotlin)
    folder is the appropriate location.

### Running the apps

#### Local access and session

The mobile authentication implementation was integrated on 2026-11-14 at
`f21b0c42a92d98dae44fb24b0cc66842945d30e1`. It covers login, registration, consent,
encrypted session storage, restart verification, refresh and logout. The
[specification](../../spec/plans/tasks/M1/T2/README.md) records acceptance status.

- Run the [local backend](../backend/README.md) at `127.0.0.1:18080`, with
  the same `PUBLIC_BASE_URL` and local policy enabled. Use synthetic fixtures.
- For Android Debug, run `adb reverse tcp:18080 tcp:18080` on the target device
  before launching the app. The debug network configuration permits the
  loopback connection.
- For iOS Debug, start the backend on the Mac and run the `iosApp` scheme in
  the simulator. The scheme can set `ROY_API_ORIGIN` to the configured API
  origin. Kotlin compilation passed for both iOS targets on Windows. SwiftUI
  linking, simulator tests, launch and Keychain require macOS with Xcode.
- The tested backend returns the session ID in the refresh response's
  `user.id`. The client verifies the account through `/me` before persisting a
  mismatched snapshot. [Backend issue](../backlog/backend/refresh-account-identity.md).

Use the run configurations provided by the run widget in your IDE's toolbar. You can also use these commands and options:

- Android app: `./gradlew :androidApp:assembleDebug`
- iOS app: open the [/iosApp](./iosApp) directory in Xcode and run it from there.

### Running tests

Use the run button in your IDE's editor gutter, or run tests using Gradle tasks:

- Android tests: `./gradlew :shared:testAndroidHostTest`
- iOS tests: `./gradlew :shared:iosSimulatorArm64Test`

---

Learn more about [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html)…
