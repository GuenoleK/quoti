---
name: fix-bug
description: Use when investigating and fixing a Quoti defect with a small scoped patch, source-level cause analysis, and appropriate verification.
---

# Fix Bug

Use this skill when fixing a defect.

## Steps

- Reproduce or clearly identify the bug.
- Locate the smallest responsible area.
- Fix the cause instead of only hiding the symptom.
- Keep the patch scoped.
- Preserve Quoti's product and design direction.
- Add or update tests when behavior is important and testable.
- Document any architectural implication if the fix changes a boundary.

## Done When

- The bug is fixed at its source.
- The change is minimal and understandable.
- Important behavior is verified.
