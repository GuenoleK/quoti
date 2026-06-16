---
name: extract-social-post
description: Use when implementing or updating Quoti post extraction from a social platform while keeping platform parsing isolated and normalized.
---

# Extract Social Post

Use this skill when implementing or updating post extraction from a social platform.

## Steps

- Extract only the data needed by Quoti's card model.
- Capture author, handle, content, date, link, and platform when available.
- Keep selectors and platform-specific parsing isolated.
- Handle missing fields gracefully.
- Avoid relying on private APIs.
- Prefer robust DOM extraction over brittle visual screenshot capture.

## Done When

- Extracted data is normalized.
- The extraction logic is platform-scoped.
- Missing or partial posts do not break the extension.
