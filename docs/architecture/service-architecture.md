# Service Architecture

This document defines how Quoti should organize feature logic as the product grows.

The goal is not to create an enterprise architecture. The goal is to keep the code readable, maintainable, and easy to debug while avoiding a flat pile of utilities.

## Core Preference

For service-oriented features, Quoti should use this shape:

```text
controller -> service -> adapter
```

- Controllers own contracts and boundaries.
- Services own implementation and orchestration.
- Adapters own external APIs, libraries, browser APIs, and platform-specific details.

This should be applied when a feature becomes complex enough to need it. Small components and simple utilities should stay simple.

## Controller

A controller is the feature entry point.

It should:

- expose the public function used by UI or extension runtime code;
- validate and normalize input;
- own request/result/progress contracts;
- select the right service or renderer;
- translate low-level errors into product-level errors;
- keep the external behavior stable when implementation changes.

It should not:

- contain UI rendering logic;
- contain detailed FFmpeg commands;
- inspect platform DOM directly;
- call many unrelated browser APIs inline;
- become a large implementation file.

Example:

```text
video-render.controller.ts
```

The popup should call the controller. It should not call FFmpeg, HLS, or Native Messaging directly.

## Service

A service implements a use case or a meaningful part of a use case.

It should:

- orchestrate multiple steps;
- call adapters for external work;
- hold feature-specific business logic;
- be readable from top to bottom;
- expose explicit methods with clear names;
- keep state local unless shared state is truly required.

It should not:

- own a public UI contract unless it is also the controller;
- hide too much behavior behind clever abstractions;
- mix unrelated concerns in one file;
- swallow errors without context.

Examples:

```text
media-source.service.ts
template-render.service.ts
wasm-ffmpeg-render.service.ts
native-render.service.ts
```

## Adapter

An adapter wraps an external system or API.

It should:

- isolate unstable APIs;
- keep third-party library details out of services;
- provide small, typed methods;
- convert external errors into typed internal errors where useful;
- be easy to replace or mock later.

Examples:

```text
ffmpeg-wasm.adapter.ts
native-messaging.adapter.ts
hls-media.adapter.ts
chrome-tabs.adapter.ts
```

Adapters are useful when the dependency is noisy, browser-specific, or likely to change.

## Types And Contracts

Shared contracts should live close to the feature unless reused across multiple domains.

For video rendering:

```text
src/rendering/video/video-render.types.ts
```

Use discriminated unions for async results and progress states:

```ts
export type VideoRenderProgress =
  | { stage: "preparing-media"; progress?: number }
  | { stage: "loading-renderer"; progress?: number }
  | { stage: "rendering"; progress: number }
  | { stage: "finalizing"; progress?: number };
```

Keep the public contract stable. Implementation can move behind it.

## Folder Size Rule

Do not split code into many tiny files by default.

Create a folder when:

- the feature has a clear product name;
- multiple files work together;
- there is a controller or clear entry point;
- the folder can be understood in one glance.

Avoid creating folders that only contain one small file unless there is a near-term reason.

## Suggested Feature Folder Shape

For complex capabilities:

```text
src/<domain>/<feature>/
  <feature>.controller.ts
  <feature>.types.ts
  services/
  adapters/
```

For smaller capabilities:

```text
src/shared/utils/<specific-utility>.util.ts
```

Do not move code into `shared` only because it might be reused later. Move it when at least two real callers need it.

## Error Handling

Errors should be debuggable without exposing confusing internals to the user.

Use two layers:

- internal error detail for logs and debugging;
- product-level message for UI.

Example:

```ts
throw new VideoRenderError("MEDIA_SOURCE_UNAVAILABLE", "No playable video URL was found.");
```

The UI can show a friendly message, while the code keeps a stable error code.

## Comments

Use comments where they explain why a non-obvious choice exists.

Good comment:

```ts
// X often exposes blob URLs in the DOM, so we prefer observed network URLs.
```

Avoid comments that repeat the code:

```ts
// Set the URL.
video.src = url;
```

The code itself should remain readable enough that most comments are unnecessary.

## Debuggability

Every service-oriented feature should make debugging straightforward.

Prefer:

- explicit stage names;
- explicit request IDs for long-running jobs;
- typed progress events;
- predictable error codes;
- small adapters around unstable APIs;
- one controller entry point per feature.

Avoid:

- hidden global state;
- implicit coupling through DOM queries;
- large utility files with unrelated functions;
- UI components that know too much about rendering internals.

## Video Rendering Application

The video rendering roadmap should follow this architecture.

Recommended extension-side shape:

```text
src/rendering/video/
  video-render.controller.ts
  video-render.types.ts
  services/
    media-source.service.ts
    template-render.service.ts
    realtime-browser-render.service.ts
    wasm-ffmpeg-render.service.ts
    native-render.service.ts
  adapters/
    ffmpeg-wasm.adapter.ts
    native-messaging.adapter.ts
    hls-media.adapter.ts
```

Recommended native helper shape:

```text
native-renderer/
  src/
    controller/
    services/
    adapters/
    protocol/
```

The native helper has its own runtime and should not import browser extension code directly. Shared protocol types can be copied initially and extracted later only if duplication becomes risky.

## When To Refactor

Refactor toward this structure when:

- a file becomes hard to scan;
- UI code starts owning business logic;
- a utility starts doing several unrelated jobs;
- new renderer strategies need the same contract;
- testing a behavior requires too much setup.

Do not refactor only to make the tree look perfect. Refactor when it makes the next change easier and safer.
