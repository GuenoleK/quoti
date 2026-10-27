# Android Preview Builds

`.github/workflows/android-preview.yml` builds a debug APK with `-PquotiPreview` and publishes it as a GitHub pre-release, so a branch can be installed from a phone without a local build.

The preview flag:

- appends `.preview` to the application id and `-preview` to the version name, so the APK installs next to the locally built app instead of replacing it;
- labels the launcher entry `Quoti Preview`;
- signs with `preview-debug.keystore`, a throwaway debug key (store and key password `android`, alias `androiddebugkey`) kept in the repo so successive previews update each other.

Never use this key for anything but preview builds.
