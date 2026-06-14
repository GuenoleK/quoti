# Editorial Craft

This document is the narrative companion to `DESIGN.md`.

`DESIGN.md` is the canonical design contract. This file explains the taste, intent, and product feeling behind that contract.

Quoti turns public conversation into shareable context cards. The design should make quoted content feel clear, intentional, warm, and worth sending.

## Direction

Quoti is an editorial capture tool, not a generic browser extension and not a social automation dashboard.

The visual direction is Editorial Craft:

- Premium without feeling luxurious.
- Modern without feeling futuristic.
- Original without becoming extravagant.
- Alive without becoming childish.
- Calm without feeling generic.
- Useful every day, not just impressive in a mockup.

The tone is roughly 85% serious and 15% personality.

## Personality

Quoti should feel:

- Warm: soft surfaces, natural spacing, human typography.
- Precise: hierarchy is clear and every separator has a job.
- Mignon: small moments of charm are welcome when they stay quiet.
- Editorial: the quote is the star; metadata supports it.
- Shareable: cards should look intentional when posted in a chat or feed.

Quoti should not feel:

- AI-generated.
- Decorative for decoration's sake.
- Amateur, improvised, or over-explained.
- Corporate SaaS.
- Cyberpunk, neobrutalist, or aggressively trendy.

## Visual Language

Use the existing theme tokens first.

- Surfaces are warm, paper-like, and slightly tactile.
- Corners are generous on the outer card and calmer inside it.
- Shadows are soft and sculpted, used to separate layers, not to show off.
- Spacing is generous, but rhythm matters more than size.
- Typography carries the identity: serif for quoted content and brand mark, sans for metadata and controls.
- Brand color is an accent, not a flood fill.
- Dark mode should feel intentionally warm, not like inverted light mode.

Avoid one-note palettes, heavy gradients, frosted glass, glowing blobs, ghost letters, fake watermarks, ornamental badges, unexplained shapes, and decorative side bars.

## Context Cards

The generated card is the core product object.

Priorities:

- The quote must be readable first.
- Source context must be present but quiet.
- The author/footer should anchor the card without competing with the quote.
- Media should feel attached to the quote, not dropped into a template.
- Exported PNGs must look polished on light, dark, and chat app backgrounds.

Do:

- Use strong type hierarchy.
- Use hairlines, spacing, and typography to create structure.
- Keep decorative marks minimal and meaningful.
- Keep rounded corners consistent with the card's physical feel.
- Verify long text, media posts, replies, and dark mode.

Do not:

- Add "AI-looking" effects such as ghost typography, random ornaments, dramatic gradients, or arbitrary pills.
- Add duplicate labels that explain the same relationship twice.
- Use thick left borders or quote-callout patterns unless they are part of a deliberate system.
- Make nested cards feel like copied social posts.
- Let transparent export fixes reduce text contrast.

## Replies And Related Posts

Replies need clear hierarchy without looking like a second full post pasted inside the card.

Preferred treatment:

- The main post remains the primary quote.
- The replied-to post is secondary context.
- Use one concise French label: `Répond à`.
- Separate the reply context with a subtle horizontal rule, spacing, and smaller type.
- Avoid left rails, vertical bars, fake quote marks, decorative tabs, and duplicate terms such as `reply` plus `En reponse a`.
- Filter separator characters from extracted metadata; bullets and middle dots are not author names.

The relation should be understood in one glance, then disappear behind the content.

## UI Controls

The extension UI should be calm and efficient.

- Prefer direct actions: copy image, download PNG, copy text, copy link.
- Controls should be obvious without becoming loud.
- Advanced options should not slow down the basic capture flow.
- Use icons only when they improve scanability.
- Avoid onboarding or explanatory panels unless the user is blocked without them.

## Motion

Motion should feel fluid and useful.

- Keep transitions soft and short.
- Use motion to confirm state changes, not to entertain.
- Avoid bouncy or exaggerated animation.

## Copy

Copy should be short, concrete, and product-native.

- Prefer French labels in the user-facing card experience when the surrounding card is French.
- Avoid mixing English and French for the same concept.
- Avoid technical wording in the popup unless it is an error state.
- Avoid labels that describe implementation details.

## Validation Checklist

Before shipping a visual change, check:

- Does the quote remain the visual priority?
- Can the hierarchy be understood without extra explanation?
- Does the card still feel like Quoti, not X, Instagram, or a generic AI template?
- Are dark mode, light mode, media posts, and text-only posts still polished?
- Do long author names, handles, dates, and reply metadata wrap cleanly?
- Are all colors, radii, shadows, spacing, and typography using theme tokens unless a clear exception is needed?
- Did the change avoid temporary artifacts, screenshots, logs, or Codex files in the project workspace?
