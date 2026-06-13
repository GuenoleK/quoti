# Frontend Architecture

## Philosophy

The project follows a simple and maintainable architecture based on:

* React
* TypeScript
* CSS
* BEM naming convention

The goal is to keep the codebase easy to navigate, scalable, and pleasant to work with over time.

For controller/service/adapter organization, use `docs/architecture/service-architecture.md`.

---

# Project Structure

```text
src/
├─ extension/
│  ├─ content/
│  │  ├─ content-script.ts
│  │  └─ content-script.css
│  │
│  └─ background/
│     └─ service-worker.ts
│
├─ popup/
│  ├─ Popup.tsx
│  ├─ Popup.css
│  │
│  └─ components/
│     ├─ PostCardPreview/
│     │  ├─ PostCardPreview.tsx
│     │  ├─ PostCardPreview.css
│     │  │
│     │  └─ PostCardActions/
│     │     ├─ PostCardActions.tsx
│     │     └─ PostCardActions.css
│     │
│     └─ CommentComposer/
│        ├─ CommentComposer.tsx
│        └─ CommentComposer.css
│
├─ shared/
│  ├─ components/
│  │  ├─ Button/
│  │  │  ├─ Button.tsx
│  │  │  └─ Button.css
│  │  │
│  │  └─ IconButton/
│  │     ├─ IconButton.tsx
│  │     └─ IconButton.css
│  │
│  ├─ theme/
│  │  ├─ tokens.css
│  │  ├─ light-theme.css
│  │  ├─ dark-theme.css
│  │  └─ theme.css
│  │
│  ├─ types/
│  │  └─ post.types.ts
│  │
│  └─ utils/
│     ├─ clipboard.util.ts
│     └─ image-export.util.ts
│
├─ card-generator/
│  ├─ card-generator.ts
│  └─ card-template.css
│
└─ main.tsx
```

---

# Folder Responsibilities

## extension

Contains all Chrome Extension runtime logic.

### content

Scripts injected into supported social media platforms.

Responsibilities:

* Detect social posts
* Extract post metadata
* Communicate with the extension

### background

Chrome Extension service worker.

Responsibilities:

* Handle extension events
* Manage messaging
* Coordinate extension features

---

## popup

Main user interface displayed when the extension icon is clicked.

Responsibilities:

* Display extracted content
* Allow user interactions
* Configure exports and sharing

---

## card-generator

Responsible for transforming extracted content into visual cards.

Responsibilities:

* Generate card layouts
* Export PNG images
* Manage rendering templates

---

## shared

Contains reusable assets shared across the application.

Responsibilities:

* Common components
* Utility functions
* Theme system
* Shared types

---

# Component Organization

Each component owns its implementation files.

Example:

```text
Button/
├─ Button.tsx
└─ Button.css
```

Components with internal sub-components keep them inside their own folder.

Example:

```text
PostCardPreview/
├─ PostCardPreview.tsx
├─ PostCardPreview.css
└─ PostCardActions/
   ├─ PostCardActions.tsx
   └─ PostCardActions.css
```

---

# Shared Components Rule

A component should only be moved to the shared folder when it is used in at least two different places.

This avoids creating an oversized shared library too early.

Rule:

> A component becomes shared only when it is used in at least two different contexts.

---

# Styling Guidelines

The project uses:

* CSS
* BEM methodology
* CSS Variables
* Design Tokens

No utility-first CSS frameworks.

---

# BEM Convention

Example:

```css
.post-card-preview {}

.post-card-preview__header {}

.post-card-preview__avatar {}

.post-card-preview__author {}

.post-card-preview__content {}

.post-card-preview__footer {}

.post-card-preview--compact {}

.post-card-preview--selected {}
```

Rules:

* Block represents the component.
* Element represents a child part.
* Modifier represents a variation.

---

# Theme System

All design decisions should be centralized in the theme layer.

## Tokens

```css
:root {
  --font-family-sans: Inter, system-ui, sans-serif;
  --font-family-serif: Georgia, serif;

  --radius-xs: 8px;
  --radius-sm: 12px;
  --radius-md: 18px;
  --radius-lg: 28px;
  --radius-xl: 40px;

  --space-1: 4px;
  --space-2: 8px;
  --space-3: 12px;
  --space-4: 16px;
  --space-5: 20px;
  --space-6: 24px;
  --space-8: 32px;

  --duration-fast: 120ms;
  --duration-medium: 220ms;
  --duration-slow: 320ms;

  --shadow-sm: 0 4px 12px rgba(0, 0, 0, 0.08);
  --shadow-md: 0 10px 30px rgba(0, 0, 0, 0.12);
}
```

---

## Light Theme

```css
:root {
  --color-background: #f7f2ea;
  --color-surface: #fffaf2;

  --color-text-primary: #241f1a;
  --color-text-secondary: #756f66;

  --color-border: rgba(36, 31, 26, 0.12);

  --color-accent: #9d6b53;
}
```

---

## Dark Theme

```css
[data-theme="dark"] {
  --color-background: #1d1916;
  --color-surface: #27211d;

  --color-text-primary: #f6f2eb;
  --color-text-secondary: #c8c0b5;

  --color-border: rgba(255, 255, 255, 0.08);

  --color-accent: #d2a082;
}
```

---

# Design Principles

Quoti follows a visual direction called Editorial Craft.

Principles:

* Premium but approachable
* Modern but timeless
* Elegant but practical
* Distinctive without being eccentric
* Calm and enjoyable for everyday use

The interface should feel like a modern editorial tool rather than a traditional browser extension.

---

# Long-Term Goal

Keep the architecture simple.

Prefer clarity over abstraction.

Prefer maintainability over cleverness.

Prefer consistency over premature optimization.
