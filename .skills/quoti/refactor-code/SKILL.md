---
name: refactor-code
description: Use when improving Quoti code structure without changing behavior, while keeping changes incremental and architecture aligned.
---

# Refactor Code

Use this skill when improving code structure without changing behavior.

## Steps

- Identify the concrete pain the refactor solves.
- Keep behavior unchanged unless explicitly requested.
- Make incremental changes.
- Avoid moving components to shared before reuse exists.
- Preserve BEM naming and theme-token usage.
- Update documentation if architecture changes.

## Done When

- The code is easier to understand or modify.
- Behavior is unchanged.
- The refactor does not introduce unnecessary abstraction.
