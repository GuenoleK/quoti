# Create Component

Use this skill when creating a React component.

## Steps

- Place the component in the feature folder that owns it.
- Move it to shared only when it is used in at least two different contexts.
- Create a `Component.tsx` file.
- Create a colocated `Component.css` file.
- Use BEM class names.
- Use theme tokens for visual values.
- Keep sub-components inside the parent component folder until reused.

## Done When

- The component has one clear responsibility.
- Styling is colocated and token-based.
- No premature shared abstraction was introduced.
