# Quoti Agent Skills

This folder contains reusable agent workflows for common Quoti tasks.

Skills describe how to approach a task. They should stay practical, focused, and complementary to the permanent instructions in `agents/instructions/`.

## Available Skills

- `chrome-extension-development.md`: add or change Chrome Extension runtime behavior, including content scripts, service worker, popup, and messaging boundaries.
- `create-component.md`: create a React component with colocated CSS, BEM naming, and theme-token usage.
- `create-theme-token.md`: add or update reusable design tokens for colors, typography, spacing, radius, shadows, or motion.
- `extract-social-post.md`: implement or update social post extraction while keeping platform-specific parsing isolated.
- `fix-bug.md`: investigate and fix defects with a small, source-level patch.
- `generate-context-card.md`: create or update visual context card generation from normalized post data.
- `implement-feature.md`: add a new product feature while keeping MVP scope, architecture, and documentation aligned.
- `refactor-code.md`: improve code structure without changing behavior.
- `review-code.md`: review code for correctness, regressions, architecture, design alignment, and missing verification.
- `support-new-platform.md`: add support for a new social platform through isolated extraction and shared card generation.

## Maintenance Rules

- Add a skill only when the workflow is likely to be reused.
- Keep each skill short and actionable.
- Do not duplicate stable project rules already defined in `agents/instructions/`.
- Update `AGENT.md` when adding or removing a skill.
