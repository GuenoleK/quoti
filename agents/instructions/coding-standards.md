# Coding Standards

## General

- Prefer readability over cleverness.
- Prefer explicit code over premature abstraction.
- Prefer maintainability over micro-optimization.
- Keep changes small and easy to review.
- Avoid adding dependencies unless they clearly reduce complexity.

## Frontend

- Use React and TypeScript.
- Use CSS files instead of Tailwind.
- Follow BEM naming conventions.
- Keep component styles colocated with the component.

## Shared Components

A component becomes shared only when it is used in at least two different contexts.

Do not create a shared abstraction only because it might be reused later.

## File Structure

Each component owns:

- Component.tsx
- Component.css

Sub-components stay inside the parent component folder until they are reused elsewhere.
