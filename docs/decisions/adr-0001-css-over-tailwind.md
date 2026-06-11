# ADR 0001: CSS Over Tailwind

## Status

Accepted.

## Context

Quoti needs a small, expressive, and maintainable frontend architecture for a browser extension.

The project also has a strong editorial visual direction that should be encoded through a theme system, component CSS, and BEM naming.

## Decision

Use CSS files with BEM naming conventions and CSS variables.

Do not use Tailwind for the MVP.

## Consequences

- Component styles stay close to component implementation.
- Theme tokens remain the source of truth for visual values.
- CSS remains readable without utility-heavy markup.
- Shared visual decisions are centralized in the theme layer.

This decision can be revisited if the product scope changes significantly.
