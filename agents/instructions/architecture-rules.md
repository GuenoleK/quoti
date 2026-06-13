# Architecture Rules

## Runtime

The MVP runs entirely in the browser.

- No backend.
- No database.
- No social platform API dependency.

Video rendering can evolve beyond the MVP runtime in two explicit phases:

- Phase 1 keeps rendering browser-local with FFmpeg WASM bundled in the extension.
- Phase 2 may add an optional Native Messaging renderer for faster local FFmpeg exports.

The extension must remain useful without the optional native renderer.

## Theme

All colors, typography, spacing, radius, shadows, and motion values must come from the theme system.

Never hardcode design values inside components when a token should exist.

## CSS

Use BEM.

Example:

```css
.post-card {}
.post-card__header {}
.post-card--compact {}
```

## Components

- Move components to shared only when reused.
- Avoid abstractions before they are needed.
- Keep feature-specific components inside their feature folder.

## Boundaries

- extension contains browser extension runtime logic.
- popup contains the user interface.
- card-generator creates visual cards and exports images.
- shared contains reusable components, theme, types, and utilities.

## Service Organization

For complex features, use the controller -> service -> adapter shape described in `docs/architecture/service-architecture.md`.

- Controllers own contracts, validation, renderer selection, progress mapping, and product-level errors.
- Services own implementation and orchestration.
- Adapters own external APIs, third-party libraries, browser APIs, platform APIs, and process boundaries.

Keep the structure modular but not scattered. Do not split code into many tiny files unless the feature has enough complexity to justify the boundary.

UI components should call controllers or narrow hooks. They should not know FFmpeg commands, Native Messaging protocol details, HLS parsing details, or platform DOM extraction internals.

Use short comments only when they explain why a non-obvious choice exists. Prefer readable, debuggable code over comments that repeat what the code already says.
