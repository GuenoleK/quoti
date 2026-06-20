---
name: quoti-android-release
description: Build, test, version, package, and publish installable Quoti Android debug APKs to Google Drive after Android app changes. Use when the user asks Codex to modify only the Quoti Android app and expects a Drive APK upload. For GitHub/community release packages that include release notes or extension assets, use quoti-release-package instead.
---

# Quoti Android Release

## Scope

Use this skill for the Quoti Android app in `C:\dev\open-source\quoti\mobile\quoti_android`.

The expected outcome is an installable debug APK uploaded to Google Drive, with a versioned APK file plus the stable latest APK replaced in place.

For GitHub releases, Android plus extension packages, or community release notes, use `skills/quoti/quoti-release-package/` instead.

## Release Workflow

1. Implement the requested Android change using normal repo patterns.
2. Run focused tests while iterating.
3. Synchronize the Android release version in `mobile/quoti_android/app/build.gradle.kts`.
4. Run the final build and tests.
5. Upload the APK to Drive as both a versioned artifact and the latest artifact.
6. Clean Codex temporary artifacts from the project workspace.
7. Report links, SHA256, version, and verification.

## Versioning

Use the current release strategy in `docs/release/release-strategy.md`.

- Keep `versionName` aligned with the public release version, currently `0.1.0` unless the user asks for a visible version bump.
- Increment `versionCode` by 1 for each installable Android package.
- Prefer the release sync script:

```powershell
npm run release:sync-version -- -- --release-version=0.1.0 --increment-android-code
```

- Use this Drive filename for the versioned APK:
  `quoti-android-debug-v<versionName>+<versionCode>.apk`
- Keep the latest Drive filename:
  `quoti-android-debug.apk`

## Build Commands

Run commands from `C:\dev\open-source\quoti\mobile\quoti_android`.

After implementation:

```powershell
.\gradlew.bat :app:testDebugUnitTest
```

Final local verification:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug
```

Run instrumentation when an emulator/device is available. Prefer the local SDK `adb.exe` if `adb` is not on `PATH`:

```powershell
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" devices
$env:ANDROID_SERIAL='emulator-5554'
.\gradlew.bat :app:connectedDebugAndroidTest
```

If no device is available, explicitly say instrumentation could not be run.

The APK path is:

```text
C:\dev\open-source\quoti\mobile\quoti_android\app\build\outputs\apk\debug\app-debug.apk
```

Compute the SHA256:

```powershell
Get-FileHash -Algorithm SHA256 -Path mobile/quoti_android/app/build/outputs/apk/debug/app-debug.apk
```

## Google Drive Publishing

Use the Google Drive connector. If the Drive skill is available, read it before writing to Drive.

Target Drive folder:

- Folder name: `dev/quoti`
- Folder ID: `REDACTED_DRIVE_FOLDER_ID`

Latest APK file:

- File name: `quoti-android-debug.apk`
- File ID: `REDACTED_DRIVE_FILE_ID`

Publishing steps:

1. Read metadata for the target folder and latest file to verify IDs.
2. Upload the local APK as a new versioned file named `quoti-android-debug-v<versionName>+<versionCode>.apk` into folder `REDACTED_DRIVE_FOLDER_ID`.
3. Replace the bytes of file `REDACTED_DRIVE_FILE_ID` with the same APK, keeping the name `quoti-android-debug.apk`.
4. Read metadata for both Drive files after upload and use only returned Drive URLs in the final answer.

Use MIME type:

```text
application/vnd.android.package-archive
```

## Cleanup

Before the final response, follow the Quoti workspace rule:

- Remove `.codex`, `.codex-*`, `.codex-remote-attachments`, logs, screenshots, and temporary Codex artifacts from `C:\dev\open-source\quoti`.
- Prefer OS temp directories for diagnostics.
- Do not delete user source changes or build outputs required for the APK.

Safe cleanup pattern:

```powershell
$workspace = (Resolve-Path '.').Path
$target = Resolve-Path '.codex-remote-attachments' -ErrorAction SilentlyContinue
if ($target -and $target.Path.StartsWith($workspace, [System.StringComparison]::OrdinalIgnoreCase)) {
  Remove-Item -LiteralPath $target.Path -Recurse -Force
}
Get-ChildItem -Force -Path . -Filter ".codex*" -Recurse -ErrorAction SilentlyContinue
```

## Final Response

Keep the final answer concise and include:

- What changed.
- Version name and version code.
- Latest Drive link.
- Versioned Drive link.
- SHA256.
- Tests run and whether they passed.
- Any verification that could not be run.

Example shape:

```text
Build published in 0.1.0+<versionCode>.

Latest: [quoti-android-debug.apk](...)
Versioned: [quoti-android-debug-v0.1.0+<versionCode>.apk](...)
SHA256: `...`

Tests: `testDebugUnitTest`, `assembleDebug`, `connectedDebugAndroidTest` OK.
```
