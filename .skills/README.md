# Project Skills

This folder is the single entry point for Quoti agent skills.

Skills follow the `SKILL.md` directory format used by the open agent skills standard. Each skill lives in its own directory and may include `references/`, `scripts/`, or `assets/` as needed.

## Quoti Skills

Project-specific workflows live under `.skills/quoti/`.

- `chrome-extension-development`: Chrome Extension runtime behavior, Manifest V3, popup, service worker, content scripts, and messaging.
- `create-component`: React component creation with colocated CSS, BEM naming, and theme tokens.
- `create-theme-token`: reusable visual tokens for color, typography, spacing, radius, shadow, and motion.
- `extract-social-post`: isolated social post extraction and normalization.
- `fix-bug`: scoped defect investigation, fix, and verification.
- `generate-context-card`: Quoti context card generation and export-oriented visual rendering.
- `implement-feature`: small, architecture-aligned feature implementation.
- `refactor-code`: behavior-preserving code structure improvements.
- `review-code`: correctness-first code review.
- `support-new-platform`: isolated support for a new social platform.

## Android Skills

Official Google Android skills live under `.skills/android/`.

They are vendored from `https://github.com/android/skills` and include their own `README.md`, `LICENSE.txt`, `SKILL.md` files, and `references/` folders.

Selected Android skills:

- `android-cli`
- `edge-to-edge`
- `testing-setup`
- `styles`

`styles` is experimental and uses alpha Compose APIs.
