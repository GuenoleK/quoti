# Quoti Release Strategy

Quoti ships two early distribution surfaces from this repository:

- Android: an installable debug APK for early community testing.
- Browser extension: a Chrome/Chromium extension ZIP built from `dist/`.

The current public release line is `0.1.x`. Keep `0.1.0` until the external story changes enough to justify a visible patch bump.

## Versioning

Use one public version across the repo:

- `package.json` version.
- `public/manifest.json` extension version.
- Android `versionName` in `mobile/quoti_android/app/build.gradle.kts`.

Android also has a technical `versionCode`. It must stay a positive, monotonically increasing integer for installable APK updates, even when the public `versionName` remains `0.1.0`.

Use this command to synchronize versions:

```powershell
npm run release:sync-version -- -- --release-version=0.1.0 --increment-android-code
```

Use an explicit Android code when rerunning the same release workflow without wanting another code bump:

```powershell
npm run release:sync-version -- -- --release-version=0.1.0 --android-version-code=47
```

Use the extra `--` before script flags with npm 11. Without it, npm may treat release flags as npm config and print warnings.

## Release Notes

Maintain one common note file plus one note file per product surface and public version:

```text
docs/release/notes/common/<version>.md
docs/release/notes/android/<version>.md
docs/release/notes/extension/<version>.md
```

The packaging script copies those files into the generated release folder and creates an aggregate GitHub release note file ordered as common, Android, then extension.

## Build Package

Create the full early release package:

```powershell
npm run release:package -- -- --release-version=0.1.0
```

Useful variants:

```powershell
npm run release:package -- -- --release-version=0.1.0 --skip-extension
npm run release:package -- -- --release-version=0.1.0 --skip-android
npm run release:package -- -- --release-version=0.1.0 --skip-build
```

Generated files are written under:

```text
release/packages/v<version>/
```

That folder is ignored by Git. It contains:

- Android APK, when Android packaging is enabled.
- Extension ZIP, when extension packaging is enabled.
- copied common release notes.
- copied Android and extension release notes.
- aggregate GitHub release notes.
- `SHA256SUMS.txt`.
- `release-manifest.json`.

## GitHub Release

After reviewing the generated package, create a GitHub release from the aggregate notes and upload all files in the generated package folder.

With GitHub CLI:

```powershell
gh release create v0.1.0 release/packages/v0.1.0/* --title "Quoti 0.1.0" --notes-file release/packages/v0.1.0/quoti-v0.1.0-release-notes.md
```

For a rerun of the same tag, delete or edit the draft release first. Do not overwrite published assets without making the replacement explicit in the release notes.

## Current Limits

- Android packaging uses the debug APK because no production signing or Play/App Bundle setup exists yet.
- The extension package is suitable for unpacked/developer install or later store submission preparation; it is not a Chrome Web Store upload workflow by itself.
- No CI/CD automation is required yet. The scripts are local and deterministic enough for the current early phase.
