# Video Rendering Roadmap

This roadmap describes the long-term plan for Quoti video exports.

The product goal is simple: when the captured post is primarily a video, Quoti should let the user export a polished video card with the same editorial quality as image cards, with reliable sound, good quality, and minimal friction.

## Current State

Quoti currently supports:

- X/Twitter post capture.
- Image card export through `html-to-image`.
- Video preview through extracted X media URLs.
- Experimental video export through browser playback, canvas capture, and `MediaRecorder`.
- Static image copy for video posts by replacing the video player with a poster snapshot during image export.

The current browser playback export is useful as a prototype, but it is not the target architecture. It records in real time, depends on the visible media element, and can lose audio if the player state changes.

## Product Principles

- The extension should remain useful without a backend.
- The default experience should stay "bundle and go".
- A user should not need to manually start a renderer process.
- Video export should not depend on the popup staying open longer than necessary once a real rendering pipeline exists.
- Copy image should always remain available as a fast fallback, even for video posts.
- A paid or always-on backend should be optional, not a requirement for the core product.

## Technical Principles

- Prefer source media rendering over recording the visible player.
- Keep renderer selection behind one controller contract.
- Keep UI components unaware of FFmpeg, HLS internals, Native Messaging, and media source quirks.
- Keep external APIs behind adapters.
- Keep video/audio/template composition deterministic and debuggable.
- Avoid remotely hosted executable code in the extension package.

## Roadmap Status

Status legend:

- `Planned`: documented but not started.
- `In progress`: actively being implemented.
- `Blocked`: needs a decision, dependency, or technical proof.
- `Done`: shipped and verified.

| Area | Status | Notes |
| --- | --- | --- |
| Video preview from X media URLs | In progress | HLS and MP4 fallback exist, but X URL discovery can still be timing-sensitive. |
| Static image fallback for video posts | Done | Video nodes are replaced with a poster snapshot during image export. |
| Real-time browser video export | Prototype | Useful for learning, but not the long-term path. |
| Phase 1 FFmpeg WASM renderer | In progress | Initial controller/service/adapter implementation exists; real X media QA remains. |
| Phase 2 Native Messaging renderer | Planned | Optional fast local renderer after Phase 1 is stable. |

## Phase 1: Bundled FFmpeg WASM Renderer

### Goal

Render final Quoti videos entirely inside the extension package, without a hosted backend and without a separate local program.

### User Experience

The user clicks `Download video`.

Quoti:

1. Reads the captured post.
2. Resolves the best available source video/audio.
3. Generates the Quoti visual template.
4. Runs FFmpeg WASM inside the extension.
5. Downloads a final MP4.

The popup should show progress states:

- `Preparing media`
- `Loading renderer`
- `Rendering video`
- `Finalizing MP4`
- `Video ready`

If rendering fails, the user should still be able to use `Copy image`.

### Why This Phase Exists

This phase preserves the best part of the current product experience: install the extension, use it directly, no server, no manual process.

It also removes the main weaknesses of the current prototype:

- no real-time recording requirement;
- no dependence on the video player mute state;
- no dependence on the popup video continuing to play;
- better control over output format and quality.

### Planned Architecture

```text
Popup action
  -> video-render.controller.ts
     -> media-source.service.ts
     -> template-render.service.ts
     -> wasm-ffmpeg-render.service.ts
        -> ffmpeg-wasm.adapter.ts
```

The controller owns the public contract. Services own implementation. Adapters own browser or library details.

### Proposed Source Layout

```text
src/rendering/video/
  video-render.controller.ts
  video-render.types.ts
  services/
    media-source.service.ts
    template-render.service.ts
    wasm-ffmpeg-render.service.ts
  adapters/
    ffmpeg-wasm.adapter.ts
    hls-media.adapter.ts
```

This structure should only be created when Phase 1 implementation starts. Until then, keep the current code stable.

### Phase 1 Work Items

| Step | Status | Notes |
| --- | --- | --- |
| Define `VideoRenderRequest`, `VideoRenderProgress`, and `VideoRenderResult` | Done | Contract lives in `src/rendering/video/video-render.types.ts`. |
| Move existing realtime export behind a renderer interface | Done | Current MediaRecorder/WebM export is now the browser fallback renderer. |
| Add FFmpeg WASM dependency | Done | Uses `@ffmpeg/ffmpeg`, `@ffmpeg/util`, and local `@ffmpeg/core` assets. |
| Bundle FFmpeg core assets locally | Done | Vite emits `assets/ffmpeg/ffmpeg-core.js` and `assets/ffmpeg/ffmpeg-core.wasm`. |
| Resolve source media into local input files | Done | MP4 is preferred when available; HLS playlists and segments are materialized locally. |
| Generate card/template overlay assets | Done | The popup card DOM is rendered to a PNG template with a media slot. |
| Compose MP4 with audio | Done | WASM FFmpeg maps source audio when available and transcodes to AAC. |
| Add progress reporting | Done | Popup labels map to preparing, loading, rendering, finalizing, and ready states. |
| Add quality presets | Done | Fast, Balanced, and High presets exist; Balanced is the popup default. |
| Add failure fallbacks | Done | WASM failures fall back to the existing browser WebM renderer when a preview video is available. |
| Protect first video startup | Done | The popup keeps the captured post visible, retries missing video URL hydration, and warms the video renderer in the background. |
| Add manual QA checklist | In progress | Build/typecheck pass; real X cases still need manual verification. |

### Expected FFmpeg Shape

The exact command will evolve, but the target shape is:

```bash
ffmpeg \
  -i source-video.mp4 \
  -i quoti-card-background.png \
  -filter_complex "[0:v]scale=...,crop=...[media];[1:v][media]overlay=x:y,format=yuv420p[v]" \
  -map "[v]" \
  -map 0:a? \
  -c:v libx264 \
  -crf 18 \
  -preset veryfast \
  -c:a aac \
  output.mp4
```

For Phase 1, this command runs through FFmpeg WASM, not native FFmpeg.

### Phase 1 Risks

- FFmpeg WASM can significantly increase extension size.
- The current local FFmpeg core adds about 32 MB uncompressed to the extension build.
- Initial renderer load may be slow.
- Large videos can hit browser memory limits.
- HLS handling can be complex because X may expose separate video/audio playlists.
- Manifest V3 and extension CSP must be respected.

### Phase 1 Definition of Done

- Video output includes the Quoti template and the original video.
- Audio is present when the source has audio.
- Muting the preview player does not affect output audio.
- Export is faster than or comparable to current real-time capture for common short videos.
- Output quality is visibly better than the current canvas/MediaRecorder prototype.
- `Copy image` remains available and reliable for video posts.
- The implementation is behind controller/service/adapter boundaries.
- Development docs are updated with setup, known limits, and testing steps.

## Phase 2: Optional Native Messaging Renderer

### Goal

Add an optional local renderer that uses native FFmpeg for faster, higher-quality exports without a hosted backend.

The extension should still work without this helper. Phase 2 is a turbo mode, not a requirement.

### User Experience

Without the helper:

```text
Download video -> FFmpeg WASM renderer
```

With the helper installed:

```text
Download video -> Quoti Renderer local helper -> native FFmpeg -> MP4
```

The user should not manually start the helper. Chrome starts the Native Messaging host when the extension connects to it.

### Why This Phase Exists

Native FFmpeg is faster and more capable than FFmpeg WASM. It can handle larger media, better presets, and fewer browser memory constraints.

This avoids hosted backend cold starts and recurring server cost while keeping the extension usable by itself.

### Planned Architecture

```text
Extension
  -> video-render.controller.ts
     -> native-render.service.ts
        -> native-messaging.adapter.ts
           -> Chrome Native Messaging
              -> Quoti Renderer host
                 -> native FFmpeg
```

### Recommended Phase 2 Implementation Language

Start with Node.js and TypeScript because the project already uses TypeScript.

No Go or Rust should be required for the first native helper prototype.

Possible later choices:

- Keep Node and package it as a desktop helper.
- Move to Go for a small single binary.
- Move to Rust for stricter binary control and distribution.

The first implementation should optimize for maintainability, not perfect packaging.

### Native Helper Responsibilities

The helper should:

- read JSON messages from stdin;
- write JSON messages to stdout;
- write logs only to stderr;
- validate request schema;
- download or receive source media references;
- run native FFmpeg;
- report progress;
- return a downloadable result reference;
- clean up temporary files.

The helper should not:

- know about React components;
- parse social platform DOM;
- own product layout decisions;
- send large video files through Native Messaging JSON messages.

### Native Messaging Contract

Initial request:

```json
{
  "type": "render.video",
  "requestId": "uuid",
  "payload": {
    "post": {},
    "media": {
      "sourceUrl": "https://video.twimg.com/..."
    },
    "template": {
      "theme": "light",
      "contentMode": "with-media"
    },
    "output": {
      "format": "mp4",
      "quality": "balanced"
    }
  }
}
```

Progress response:

```json
{
  "type": "render.progress",
  "requestId": "uuid",
  "stage": "rendering",
  "progress": 0.42
}
```

Ready response:

```json
{
  "type": "render.ready",
  "requestId": "uuid",
  "result": {
    "downloadUrl": "http://127.0.0.1:49152/output.mp4",
    "filename": "quoti-video.mp4"
  }
}
```

Error response:

```json
{
  "type": "render.error",
  "requestId": "uuid",
  "error": {
    "code": "FFMPEG_FAILED",
    "message": "FFmpeg exited with code 1."
  }
}
```

### Why Return A Local URL Instead Of Video Bytes

Native Messaging is for structured messages, not large binary files. The helper should not send the final MP4 through stdout.

Preferred options:

1. Helper starts a temporary loopback HTTP endpoint and returns a one-time `127.0.0.1` URL.
2. Helper writes the file to a known output directory and returns the path for user-facing reporting.
3. Helper streams chunks through a separate local channel if needed later.

Option 1 is the best first target because the extension can download the result like a normal URL.

### Phase 2 Work Items

| Step | Status | Notes |
| --- | --- | --- |
| Add renderer capability detection | Planned | Extension checks whether `com.quoti.renderer` is available. |
| Add `nativeMessaging` permission behind a deliberate change | Planned | This affects install permissions and should be documented. |
| Create helper protocol package | Planned | Shared request/response schemas between extension and helper. |
| Create Node-based helper prototype | Planned | TypeScript first, native binary packaging later. |
| Add native host manifest templates | Planned | Windows first, macOS/Linux after. |
| Add dev registration scripts | Planned | Scripts should install/uninstall native messaging host manifests. |
| Add native FFmpeg integration | Planned | Discover `ffmpeg` from PATH first, bundle later if needed. |
| Add progress parser | Planned | Parse FFmpeg stderr progress into Quoti stages. |
| Add local download handoff | Planned | One-time local URL or controlled output file. |
| Add user-facing fallback | Planned | If helper is missing or fails, use WASM renderer. |
| Add packaging plan | Planned | Decide installer strategy only after prototype works. |

### Phase 2 Definition of Done

- Extension detects whether the native renderer is installed.
- Native renderer can be installed and uninstalled in development.
- User does not manually start the renderer.
- Native renderer produces MP4 with reliable audio.
- Native renderer reports progress.
- Native renderer handles errors without breaking the popup.
- Extension falls back to FFmpeg WASM when native rendering is unavailable.
- Documentation covers Windows development setup first, then macOS/Linux.

## Backend Option

A hosted backend remains possible, but it is not the preferred next step.

Use a backend only if:

- public distribution needs zero helper installation;
- FFmpeg WASM is too slow for common videos;
- native helper installation is too much friction for target users;
- operating cost and cold start strategy are acceptable.

If a backend is introduced later, it should use the same controller contract as WASM and native rendering.

## Roadmap Maintenance

Update this file whenever:

- a phase starts or finishes;
- a rendering strategy changes;
- a major risk is discovered;
- dependencies or platform constraints change;
- a new renderer is added.

Every roadmap update should include:

- status table changes;
- work item status changes;
- new risks or removed risks;
- links to relevant implementation PRs or commits when available.

## References

- Chrome Native Messaging: https://developer.chrome.com/docs/extensions/develop/concepts/native-messaging
- Chrome Extension CSP: https://developer.chrome.com/docs/extensions/reference/manifest/content-security-policy
- Chrome remote hosted code policy: https://developer.chrome.com/docs/extensions/develop/migrate/remote-hosted-code
- FFmpeg WASM usage: https://ffmpegwasm.netlify.app/docs/getting-started/usage/
- FFmpeg downloads: https://ffmpeg.org/download.html
