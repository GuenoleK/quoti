# Quoti Mobile

Phase 3 adds the Quoti mobile companion app here. The active mobile app is native Android.

The Android app now lives at:

```text
mobile/quoti_android
```

It is a native Kotlin app built with Jetpack Compose and Material 3 Expressive. The earlier Flutter prototype has been removed to keep the mobile workspace focused. iOS can be created later when a macOS/Xcode and iPhone testing environment is available.

## Commands

Run native Android commands from the app folder:

```powershell
cd mobile/quoti_android
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:installDebug
```

To test the Android share flow:

```powershell
%USERPROFILE%\AppData\Local\Android\Sdk\platform-tools\adb.exe shell am start -a android.intent.action.SEND -t text/plain --es android.intent.extra.TEXT "Regarde ca https://x.com/dexerto/status/123" -p com.quoti.android
```

Keep the app aligned with `docs/architecture/mobile-app-architecture.md`, `contracts/post.schema.json`, and `fixtures/posts`.
