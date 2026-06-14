# Quoti Native Renderer

This helper is the Phase 2 local renderer for Quoti video exports.

Chrome starts it through Native Messaging. The extension sends source video URLs and a rendered Quoti template PNG, then the helper runs the bundled native FFmpeg binary and exposes the final MP4 through a one-time loopback URL.

## Bundled FFmpeg

The production path is intentionally not `PATH` based. Put the FFmpeg binary here:

```text
native/quoti-renderer/vendor/ffmpeg/win32-x64/ffmpeg.exe
```

For local diagnostics only, `QUOTI_FFMPEG_PATH` can point to another FFmpeg executable. The helper never silently falls back to a system `ffmpeg` from `PATH`.

## Windows Dev Registration

1. Build or load the extension in Chrome.
2. Copy the unpacked extension ID from `chrome://extensions`.
3. Register the host:

```powershell
npm run native:install -- -ExtensionId <extension-id>
```

4. Check the helper:

```powershell
npm run native:check
```

5. Unregister it when needed:

```powershell
npm run native:uninstall
```

The helper writes the Native Messaging manifest outside the repository, under the current Windows user profile, and stores the Chrome registry pointer under `HKCU`.

## Protocol Shape

- `render.video`: render one MP4 from candidate media URLs and a template PNG.
- `render.progress`: report Quoti progress states.
- `render.ready`: return a temporary `http://127.0.0.1:<port>/...` download URL.
- `render.release`: let the helper close the temporary server and delete its files.
- `render.cancel`: stop the active FFmpeg process and clean up.
