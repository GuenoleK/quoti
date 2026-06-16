---
name: chrome-extension-development
description: Use when adding or changing Quoti Chrome Extension runtime behavior, including content scripts, service workers, popup UI, messaging, Manifest V3 boundaries, and browser-local MVP constraints.
---

# Chrome Extension Development

Use this skill when adding or changing Chrome Extension runtime behavior.

## Steps

- Confirm whether the change belongs to content scripts, background service worker, popup, or shared code.
- Keep Manifest V3 constraints in mind.
- Prefer browser-local behavior for the MVP.
- Avoid backend, database, or social API dependencies.
- Keep platform-specific extraction logic isolated from shared UI code.
- Document architectural changes when extension boundaries change.

## Done When

- Runtime ownership is clear.
- The change works without external services.
- Messaging between extension areas remains explicit and typed.
