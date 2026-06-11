# Support New Platform

Use this skill when adding support for a new social platform.

## Steps

- Confirm the platform belongs in the current product phase.
- Add platform-specific extraction logic in an isolated area.
- Normalize extracted data into Quoti's shared post model.
- Handle missing or platform-specific fields gracefully.
- Reuse card generation instead of forking visual output.
- Avoid social API dependencies for the MVP.
- Update documentation when platform support changes product scope.

## Done When

- Platform extraction is isolated.
- Shared card generation still works from normalized data.
- The product scope remains clear.
