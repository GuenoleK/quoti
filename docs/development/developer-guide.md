# Developer Guide

This guide is for people developing Quoti.

It is separate from user installation instructions. Users should only need to install the built extension. Developers need the tools below to build, test, and evolve the codebase.

## Project Stack

- Chrome Extension, Manifest V3.
- React.
- TypeScript.
- Vite.
- CSS with BEM naming.
- `html-to-image` for static image export.
- `hls.js` for video preview playback.

## Base Development Prerequisites

Install these first:

- Git.
- Node.js LTS.
- npm, bundled with Node.js.
- Google Chrome or Chrome for Testing.
- A code editor with TypeScript support.

You do not need Go, Rust, Python, Docker, or system FFmpeg for the current extension build.

Verify the basics:

```bash
git --version
node --version
npm --version
```

## Local Setup

Install dependencies:

```bash
npm install
```

Run the Vite development server:

```bash
npm run dev
```

Type-check the project:

```bash
npm run typecheck
```

Build the extension:

```bash
npm run build
```

Load the extension in Chrome:

1. Build with `npm run build`.
2. Open `chrome://extensions`.
3. Enable Developer mode.
4. Click `Load unpacked`.
5. Select the `dist/` folder.
6. Open `https://x.com` or `https://twitter.com`.
7. Capture a visible post with the Quoti extension.

For UI-only checks, run the dev server and open:

```text
http://localhost:5173/popup.html
```

Outside the Chrome extension runtime, the popup uses a built-in preview post.

## Current Development Workflow

Before changing code:

1. Read `AGENT.md`.
2. Read the relevant docs under `docs/`.
3. Keep the change small and reversible.
4. Prefer existing patterns over new abstractions.
5. Update docs when a behavior, architecture, or workflow changes.

After changing code:

1. Run `npm run typecheck` for logic-only changes.
2. Run `npm run build` for extension/runtime changes.
3. Manually reload the unpacked extension in Chrome.
4. Test the popup against real X posts when capture logic changes.

## Phase 1 Video Rendering Development

Phase 1 adds FFmpeg WASM inside the extension package.

### Additional Tools

No system FFmpeg is required for Phase 1.

No Go or Rust is required for Phase 1.

Expected packages when implementation starts:

```bash
npm install @ffmpeg/ffmpeg @ffmpeg/util @ffmpeg/core
```

Do not load FFmpeg WASM code from a CDN in extension runtime. Manifest V3 extensions must package executable code with the extension.

The extension build copies the local FFmpeg core assets into:

```text
dist/assets/ffmpeg/ffmpeg-core.js
dist/assets/ffmpeg/ffmpeg-core.wasm
```

The Manifest V3 CSP includes `wasm-unsafe-eval` so Chrome can compile the packaged WebAssembly module. Do not replace the local core URLs with CDN URLs.

### Development Goals

The Phase 1 implementation should:

- add a renderer controller contract first;
- keep FFmpeg behind an adapter;
- keep UI components independent from FFmpeg details;
- use local extension-packaged WASM assets;
- preserve `Copy image` as a fallback;
- provide progress and useful errors.

Current implementation notes:

- The public entry point is `src/rendering/video/video-render.controller.ts`.
- The popup loads the video controller lazily and does not call FFmpeg directly.
- When a video post is captured without a playable URL, the popup keeps the post context visible, warms the renderer, and retries media URL hydration before asking the user to refresh.
- Direct MP4 sources are preferred for reliability.
- HLS playlists are downloaded, rewritten, and passed to FFmpeg as local virtual files.
- If WASM rendering fails and a preview video element is available, Quoti falls back to the previous browser WebM renderer.
- The popup keeps media nodes mounted while toggling Text only/With media so loaded image and video context is not lost.
- Tall video media is height-capped in the card layout while preserving the source aspect ratio.
- Video templates render at 1.5x instead of the static image 2x export scale to keep WASM encoding practical.
- The default WASM preset favors faster x264 encoding over smaller output files, then copies audio when MP4-compatible.

### Expected Manual Test Matrix

Test these cases before considering Phase 1 stable:

- short MP4 video with audio;
- short HLS video with audio;
- vertical video;
- landscape video;
- video without audio;
- video where the preview player is muted;
- post with text plus video;
- post with video only;
- failure to resolve video URL;
- interrupted render.

## Phase 2 Native Renderer Development

Phase 2 adds an optional local renderer through Chrome Native Messaging.

The extension must still work without the native renderer installed.

### Development Philosophy

Start with a Node.js and TypeScript helper because the repository already uses TypeScript.

Do not require Go or Rust for the first prototype.

Go or Rust can be considered later if packaging a small single binary becomes more important than sharing TypeScript types.

### Additional Tools For Phase 2

Install only when working on the native renderer:

- Node.js LTS, already required for the extension.
- A bundled FFmpeg binary at `native/quoti-renderer/vendor/ffmpeg/win32-x64/ffmpeg.exe`.
- A terminal with permission to write the Native Messaging host manifest during development.

Verify FFmpeg:

```powershell
npm run native:check
```

If the bundled binary is not found on Windows:

- put the production FFmpeg binary at `native/quoti-renderer/vendor/ffmpeg/win32-x64/ffmpeg.exe`;
- for diagnostics only, run with `QUOTI_FFMPEG_PATH` pointing to another FFmpeg executable.

The helper should not silently discover `ffmpeg` from `PATH`. Missing bundled FFmpeg must be treated as "native renderer unavailable" so the extension can fall back to WASM.

### Native Messaging Development Concepts

Chrome starts the native host when the extension calls `chrome.runtime.connectNative` or `chrome.runtime.sendNativeMessage`.

The host communicates through stdin/stdout using length-prefixed JSON messages.

Important rules:

- write protocol messages to stdout only;
- write logs to stderr only;
- keep messages small;
- do not send the final video file through Native Messaging;
- return a one-time local download URL or write the file directly from the helper.

### Windows Development Registration

The development script should:

1. Locate `native/quoti-renderer/bin/quoti-renderer.cmd`.
2. Write a Native Messaging host manifest under the current Windows user profile.
3. Register the manifest path under:

```text
HKCU\Software\Google\Chrome\NativeMessagingHosts\com.quoti.renderer
```

The host manifest should contain:

```json
{
  "name": "com.quoti.renderer",
  "description": "Quoti local video renderer",
  "path": "C:\\Path\\To\\quoti-renderer.cmd",
  "type": "stdio",
  "allowed_origins": ["chrome-extension://EXTENSION_ID/"]
}
```

During development, the extension ID can change if the extension is loaded from a different folder. Keep this visible in the setup script output.

Register the host with:

```powershell
npm run native:install -- -ExtensionId <extension-id>
```

Unregister it with:

```powershell
npm run native:uninstall
```

### Phase 2 Suggested Repository Layout

```text
native/quoti-renderer/
  package.json
  src/
    host.mjs
  scripts/
    install-native-host.ps1
    uninstall-native-host.ps1
  manifests/
    com.quoti.renderer.windows.json
  vendor/
    ffmpeg/
      win32-x64/
        ffmpeg.exe
```

The native renderer is intentionally separate from `src/` because it runs outside the browser extension.

## Debugging

### Popup

- Use Chrome DevTools on the extension popup.
- Keep logs concise and remove noisy logs before shipping.
- Prefer user-facing error states over silent failures.

### Content Script

- Test against real X timeline posts.
- Watch for virtualized timeline behavior.
- Be careful with hovered post state, visible post state, and context menu state.

### Service Worker

- Inspect from `chrome://extensions`.
- Remember that Manifest V3 service workers can stop and restart.
- Store required transient state explicitly when needed.

### Native Renderer

- During Native Messaging development, log to stderr.
- Never print debug text to stdout.
- Use Chrome's native messaging error logs when the host does not start.
- Add a standalone CLI test mode before connecting the extension.

## Documentation Expectations

Update documentation when:

- a user-visible workflow changes;
- a renderer changes;
- a new permission is added;
- a new dependency is added;
- architecture boundaries change;
- setup requirements change.

Use English for new project documentation.

## Key References

- Video rendering roadmap: `docs/roadmap/video-rendering-roadmap.md`
- Service architecture: `docs/architecture/service-architecture.md`
- Frontend architecture: `docs/architecture/frontend-architecture.md`
- Agent entry point: `AGENT.md`
