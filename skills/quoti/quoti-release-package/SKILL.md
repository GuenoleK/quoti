---
name: quoti-release-package
description: Version, package, verify, and prepare GitHub release assets for Quoti Android and the Quoti browser extension. Use when the user asks to create a Quoti release package, generate release notes, bump or synchronize release versions, package the Android APK, package the extension ZIP, draft a GitHub release, or publish release assets.
---

# Quoti Release Package

## Overview

Use this skill in `C:\dev\open-source\quoti` to produce local release assets that can be attached to a GitHub release.

The release workflow is intentionally local-first: scripts create the Android APK, extension ZIP, copied release notes, aggregate GitHub notes, checksums, and a release manifest under `release/packages/v<version>/`.

## Core Files

- Strategy: `docs/release/release-strategy.md`
- Common notes: `docs/release/notes/common/<version>.md`
- Android notes: `docs/release/notes/android/<version>.md`
- Extension notes: `docs/release/notes/extension/<version>.md`
- Version sync: `npm run release:sync-version`
- Package builder: `npm run release:package`

## Workflow

1. Inspect the repo state and release docs:

```powershell
git status --short
Get-Content -Raw docs/release/release-strategy.md
```

Do not overwrite user changes. If release notes already changed, preserve and extend them.

2. Decide the public version.

For the early phase, keep `0.1.0` unless the user asks for a visible bump. Android `versionCode` may still need to increase for installable APK updates.

3. Update release notes before packaging.

Edit the common file plus each surface file that ships:

```text
docs/release/notes/common/<version>.md
docs/release/notes/android/<version>.md
docs/release/notes/extension/<version>.md
```

Use community-facing language: what changed, what is included, known limits, and install expectations. Do not describe internal implementation details unless they affect users.

4. Synchronize versions.

For a new Android package, increment the Android code:

```powershell
npm run release:sync-version -- -- --release-version=0.1.0 --increment-android-code
```

For a rerun that should preserve the Android code:

```powershell
npm run release:sync-version -- -- --release-version=0.1.0 --android-version-code=<current-code>
```

5. Build the release package:

```powershell
npm run release:package -- -- --release-version=0.1.0
```

Use scoped packaging only when requested:

```powershell
npm run release:package -- -- --release-version=0.1.0 --skip-extension
npm run release:package -- -- --release-version=0.1.0 --skip-android
```

6. Verify generated files.

Expected output folder:

```text
release/packages/v<version>/
```

Expected contents for a full release:

- `quoti-android-v<version>+<versionCode>-debug.apk`
- `quoti-extension-v<version>.zip`
- `quoti-common-v<version>-release-notes.md`
- `quoti-android-v<version>-release-notes.md`
- `quoti-extension-v<version>-release-notes.md`
- `quoti-v<version>-release-notes.md`
- `SHA256SUMS.txt`
- `release-manifest.json`

7. Draft or create the GitHub release only after the package is verified.

With GitHub CLI:

```powershell
gh release create v0.1.0 release/packages/v0.1.0/* --title "Quoti 0.1.0" --notes-file release/packages/v0.1.0/quoti-v0.1.0-release-notes.md
```

If `gh` is unavailable or the user did not ask to publish, stop after producing local assets and provide the exact command.

## Verification

The package script runs:

- Android: `:app:testDebugUnitTest :app:assembleDebug`
- Extension: `npm run build`

If a surface is skipped, say it explicitly. If instrumentation tests are needed, use the Android release skill or run them separately when an emulator/device is available.

## Cleanup

Before final response:

- Remove `.codex`, `.codex-*`, `.codex-remote-attachments`, logs, screenshots, and temporary Codex artifacts from the Quoti workspace.
- Keep `release/packages/v<version>/` when it is the requested deliverable.
- Do not delete Gradle, npm, or Vite build outputs required by the generated package.

## Final Response

Report:

- version and Android `versionCode`;
- generated folder;
- Android APK name and SHA256, if built;
- extension ZIP name and SHA256, if built;
- tests/builds run;
- GitHub release command or published URL;
- anything that could not be verified.
