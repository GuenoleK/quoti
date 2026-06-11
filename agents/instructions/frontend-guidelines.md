# Frontend Guidelines

## Experience

- Make the default workflow fast and obvious.
- Prioritize the quick capture flow before advanced composition features.
- Keep the interface calm, readable, and useful in a browser extension context.
- Avoid heavy onboarding screens unless they are required for the task.

## Components

- Components should have a clear responsibility.
- Component names should describe product concepts, not implementation details.
- Local state should stay close to the component that owns it.
- Shared utilities belong in src/shared only when they serve multiple areas.

## Styling

- Use CSS.
- Use CSS variables from the theme system.
- Use BEM class names.
- Keep layout and visual decisions consistent with the theme tokens.

## Export UX

- Favor direct actions such as copy image, download PNG, copy text, and copy link.
- Avoid workflows that feel like social automation.
