---
version: "0.1"
name: "Quoti"
description: "Editorial capture tool that turns public conversation into shareable context cards."
tokens:
  colors:
    app:
      background: "#f7f2ea"
      backgroundSoft: "#efe4d8"
      surface: "#fffaf2"
      surfaceRaised: "#fffdf8"
      surfaceMuted: "#f1e8dc"
      textPrimary: "#241f1a"
      textSecondary: "#756f66"
      textMuted: "#9b9185"
      accent: "#9d6b53"
      accentStrong: "#744632"
      accentSoft: "#ead6ca"
      success: "#35765b"
      danger: "#9d3f3f"
    cardLight:
      surface: "#fffaf2"
      textPrimary: "#241f1a"
      textSecondary: "#756f66"
      brand: "#744632"
      platform: "#241f1a"
      platformBackground: "rgb(36 31 26 / 12%)"
    cardDark:
      surface: "#1f1a16"
      textPrimary: "#f8f0e5"
      textSecondary: "#b9aa9a"
      brand: "#d8a17f"
      platform: "#f8f0e5"
      platformBackground: "rgb(248 240 229 / 12%)"
  typography:
    sans: "Inter, ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, Segoe UI, sans-serif"
    serif: "Georgia, Times New Roman, serif"
    scale:
      xs: "0.75rem"
      sm: "0.875rem"
      md: "1rem"
      lg: "1.125rem"
      xl: "1.5rem"
      "2xl": "2rem"
    weights:
      regular: 400
      medium: 500
      semibold: 650
      bold: 760
  spacing:
    scale:
      "1": "4px"
      "2": "8px"
      "3": "12px"
      "4": "16px"
      "5": "20px"
      "6": "24px"
      "7": "28px"
      "8": "32px"
      "10": "40px"
      "12": "48px"
  radius:
    xs: "8px"
    sm: "12px"
    md: "18px"
    lg: "28px"
    xl: "40px"
  shadows:
    sm: "0 4px 12px rgb(36 31 26 / 8%)"
    md: "0 10px 30px rgb(36 31 26 / 12%)"
    lg: "0 18px 55px rgb(36 31 26 / 16%)"
---

# Quoti Design System

This is the canonical design contract for Quoti. Agents must read this file before changing UI, generated cards, visual tokens, layout, typography, or interaction polish.

For narrative context and taste calibration, read `docs/design/editorial-craft.md` after this file.

## Product Feel

Quoti is an editorial capture tool. It should feel warm, precise, mignon, calm, and shareable. It should never feel generic, corporate, cyberpunk, neobrutalist, or AI-generated.

The visual tone is 85% serious and 15% personality.

## Core Rules

- The quote is always the visual priority.
- Decoration must clarify hierarchy or disappear.
- Use theme tokens before introducing new values.
- Serif type carries quoted content and the Quoti mark.
- Sans type carries controls, metadata, and operational UI.
- Brand color is an accent, not a flood fill.
- Surfaces should feel warm and paper-like.
- Shadows should separate layers softly, never call attention to themselves.
- Dark mode should stay warm and readable, not simply inverted.

## Context Cards

Generated cards are the core product object.

Do:

- Use spacing, hairlines, and typography to create structure.
- Keep source metadata present but quiet.
- Keep author and footer anchoring the card without competing with content.
- Make media feel attached to the quote, not dropped into a template.
- Verify light mode, dark mode, media posts, text-only posts, long text, and replies.

Do not:

- Add ghost letters, fake watermarks, ornamental badges, decorative side bars, dramatic gradients, or random pills.
- Use thick left rails or quote-callout patterns for replies.
- Duplicate labels for one relationship.
- Make nested cards look like copied social posts.
- Let transparent PNG export changes reduce contrast.

## Replies And Related Posts

Replies are secondary context, not a second primary quote.

- Keep the main post as the primary quote.
- Use one concise French relationship label: `Répond à`.
- Separate reply context with subtle horizontal rhythm, smaller type, and spacing.
- Avoid `reply` plus `En réponse à`; never mix English and French for the same relationship.
- Avoid left borders, vertical rails, tabs, fake quote marks, ghost marks, and decorative containers.
- Filter bullets and middle dots from extracted author metadata.

The relationship should be understood at a glance, then recede behind the content.

## Extension UI

- Keep the capture workflow fast and obvious.
- Prefer direct actions: copy image, download PNG, copy text, copy link.
- Avoid heavy onboarding and explanatory panels.
- Controls should feel calm, efficient, and native to Quoti.

## Validation Checklist

Before shipping a visual change:

- Does this still feel like Quoti?
- Is the quote still the clearest element?
- Can the hierarchy be understood without explanation?
- Are colors, spacing, type, radii, and shadows token-based?
- Does it avoid AI-looking effects and unexplained decoration?
- Does it hold up in light mode, dark mode, replies, media posts, and exported PNGs?
