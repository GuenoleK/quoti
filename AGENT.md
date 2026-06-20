# Quoti Agent Entry Point

Before making any modification, read the following documents.

## Product

- docs/product/product-vision.md

## Architecture

- docs/architecture/frontend-architecture.md
- docs/architecture/mobile-app-architecture.md
- docs/architecture/service-architecture.md

## Roadmap

- docs/roadmap/video-rendering-roadmap.md
- docs/roadmap/mobile-app-phase-3.md

## Development

- docs/development/developer-guide.md

Extension build rule:

- After any browser extension source, style, asset, or generated-card change, run `npm run build` before finishing so `dist/` reflects the latest implementation.

## Design

- DESIGN.md
- docs/design/editorial-craft.md

For any UI, generated card, theme token, layout, typography, visual hierarchy, or interaction polish change, treat DESIGN.md as the canonical design source. Use docs/design/editorial-craft.md only as narrative context for taste and product feeling.

## Decisions

- docs/decisions/adr-0001-css-over-tailwind.md

## Agent Instructions

- agents/context.md
- agents/instructions/project-overview.md
- agents/instructions/coding-standards.md
- agents/instructions/frontend-guidelines.md
- agents/instructions/architecture-rules.md

## Agent Skills

Project skill organization:

- New Quoti-owned skills created for this repository live under `skills/quoti/<skill-name>/`.
- Keep each skill in the standard `SKILL.md` directory format, with optional `agents/openai.yaml`, `references/`, `scripts/`, or `assets/` only when useful.
- Do not install project skills in the user-level Codex directory (`%USERPROFILE%\.codex\skills`) when they are meant to travel with the repo.
- Do not create `.codex`, `.codex-*`, or `.codex/skills` inside this repository.
- Existing skills under `.skills/` are the historical/vendored skill catalog for Quoti and Android. Read `.skills/README.md` when you need those existing workflows, but place newly authored Quoti project skills under `skills/quoti/`.

Current repo-owned Quoti skills:

- skills/quoti/quoti-android-release/
- skills/quoti/quoti-commit-push/
- skills/quoti/quoti-release-package/

Always follow the instructions defined in these documents.
