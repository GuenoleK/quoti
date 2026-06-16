# Quoti Android

Native Android app for Quoti, built with Kotlin, Jetpack Compose, and Material 3.

## Run

```powershell
cd C:\dev\open-source\quoti\mobile\quoti_android
.\gradlew.bat :app:installDebug
```

Or open this folder in Android Studio and run the `app` configuration.

## Share Intent Test

```powershell
adb shell am start -a android.intent.action.SEND -t text/plain --es android.intent.extra.TEXT "Regarde ca https://x.com/dexerto/status/123" -p com.quoti.android
```
