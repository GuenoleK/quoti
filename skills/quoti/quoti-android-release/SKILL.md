---
name: quoti-android-release
description: Build, test, version, package, and publish installable Quoti Android debug APKs to Google Drive after Android app changes. Use when the user asks Codex to modify only the Quoti Android app and expects a Drive APK upload. For GitHub/community release packages that include release notes or extension assets, use quoti-release-package instead.
---

# Quoti Android Release

## Scope

Use this skill for the Quoti Android app in `C:\dev\open-source\quoti\mobile\quoti_android`.

The expected outcome is an installable debug APK uploaded to Google Drive, with a versioned APK file plus the stable latest APK replaced in place. Keep only one versioned Android APK in the Drive release folder: after the new versioned APK is uploaded and verified, delete the previous versioned Android APK to avoid accumulating Drive storage.

For GitHub releases, Android plus extension packages, or community release notes, use `skills/quoti/quoti-release-package/` instead.

## Release Workflow

1. Implement the requested Android change using normal repo patterns.
2. Run focused tests while iterating.
3. Synchronize the Android release version in `mobile/quoti_android/app/build.gradle.kts`.
4. Run the final build and tests.
5. Upload the APK to Drive as both a versioned artifact and the latest artifact.
6. Delete the previous versioned Android APK from the Drive release folder after the new upload is verified.
7. Clean Codex temporary artifacts from the project workspace.
8. Report links, SHA256, version, verification, and any deleted previous Drive artifact.

## Versioning

Use the current release strategy in `docs/release/release-strategy.md`.

- Keep `versionName` aligned with the current public release version already used by the repo unless the user asks for a visible version bump.
- Increment `versionCode` by 1 for each installable Android package.
- Prefer the release sync script:

```powershell
$releaseVersion = (Get-Content package.json | ConvertFrom-Json).version
npm run release:sync-version -- -- --release-version=$releaseVersion --increment-android-code
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
- Resolve the folder at runtime. Prefer private environment/config values when they are available, or search Drive by the folder name and verify that the result is unique.

Latest APK file:

- File name: `quoti-android-debug.apk`
- Resolve the file at runtime inside the target folder. Prefer private environment/config values when they are available, or search Drive by file name and verify that the result is unique.
- If the target folder is verified and no latest APK exists there, treat this as a first Drive publication and create `quoti-android-debug.apk` with a new upload.

Private configuration names, if the environment provides them:

- `QUOTI_ANDROID_RELEASE_DRIVE_FOLDER_ID`
- `QUOTI_ANDROID_RELEASE_LATEST_APK_FILE_ID`

Never commit concrete Drive IDs, account identifiers, credentials, links, or local user paths to the repository. If runtime Drive resolution is ambiguous, stop and ask the user to identify the correct folder or file.

Publishing steps:

1. Resolve the target folder from private configuration or Drive search, then read metadata to verify it. Resolve the latest APK file if it exists.
2. List the target folder before upload and identify existing versioned Android APKs matching `quoti-android-debug-v*.apk`.
3. Upload the local APK as a new versioned file named `quoti-android-debug-v<versionName>+<versionCode>.apk` into the resolved folder.
4. Replace the bytes of the resolved latest APK file with the same APK, keeping the name `quoti-android-debug.apk`; if no latest APK exists, upload a new latest file with that exact name.
5. Read metadata for both Drive files after upload and use only returned Drive URLs in the final answer.
6. Delete previous versioned Android APKs in the target folder after the new versioned file is verified. Do not delete the newly uploaded versioned APK, the stable latest APK, non-Android files, release notes, or files outside the resolved target folder.
7. List or read metadata after deletion to verify the target folder retains only the new versioned APK and `quoti-android-debug.apk` for Android debug distribution.

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
