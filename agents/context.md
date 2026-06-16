# Quoti Agent Context

Quoti is a browser extension that turns social media posts into elegant, shareable context cards.

The product exists to make conversations portable across platforms without losing the original context.

## Current Product Direction

- Start with a lightweight MVP.
- Support X first.
- Generate clean visual cards from extracted post data.
- Export as PNG before adding advanced workflows.
- Keep the core extension browser-local by default.
- For video rendering, prefer a bundled FFmpeg WASM renderer first, then an optional Native Messaging renderer for faster local exports.
- Phase 3 is the mobile companion app direction: native Android first with Kotlin, Jetpack Compose, and Material 3; iOS later when macOS/Xcode and iPhone testing are available; Quoti should appear as a system share target.

## Agent Operating Principles

- Read AGENT.md before changing code or documentation.
- Treat docs/product/product-vision.md as the product source of truth.
- Treat docs/architecture/frontend-architecture.md as the technical source of truth.
- Treat docs/architecture/service-architecture.md as the service organization source of truth.
- Treat docs/roadmap/video-rendering-roadmap.md as the video rendering roadmap source of truth.
- Treat docs/roadmap/mobile-app-phase-3.md as the mobile app Phase 3 roadmap source of truth.
- Treat DESIGN.md as the design source of truth.
- Use docs/design/editorial-craft.md as narrative context for design taste and product feeling.
- Keep new decisions explicit in docs/decisions.
- Use `.skills/` for recurring workflows.
- Prefer small, reversible changes.
