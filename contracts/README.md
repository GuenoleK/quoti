# Quoti Contracts

This folder contains language-neutral contracts shared by Quoti surfaces.

The browser extension keeps its TypeScript model in `src/shared/types/post.types.ts`. Mobile apps should not import TypeScript directly. Instead, each runtime should implement its own typed model from the shared contract and use the fixtures in `fixtures/posts` for cross-surface checks.

## Files

- `post.schema.json`: canonical JSON Schema for an extracted Quoti post.

## Change Rules

- Keep the contract backend-free and social-API-free.
- Update fixtures whenever a contract field changes.
- Keep optional fields optional unless every runtime can provide them reliably.
- Prefer additive changes over breaking changes while the mobile app is being prototyped.
