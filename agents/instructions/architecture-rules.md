# Architecture Rules

## Runtime

The MVP runs entirely in the browser.

- No backend.
- No database.
- No social platform API dependency.

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
