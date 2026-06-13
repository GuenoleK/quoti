# Quoti

React everywhere, without losing the context.

## What is Quoti?

Quoti is a browser extension that transforms social media posts into elegant, shareable context cards.

Instead of posting isolated reactions across platforms, Quoti helps users preserve and transport the original conversation.

## Vision

Conversations are fragmented across X, Threads, Bluesky, LinkedIn and many other platforms.

Quoti makes conversations portable.

## MVP

The current MVP is a Chrome Extension built with React, TypeScript, Vite, and Manifest V3.

It supports:

- capturing a visible post on X/Twitter
- capturing a post from the right-click menu
- optionally showing a Quoti button inside supported posts
- normalizing the post into a shared data model
- extracting attached post images and videos
- rendering an Editorial Craft context card
- switching the generated card between light and dark themes
- switching the generated card between text-only and media layouts
- downloading the card as JPG
- copying the generated image
- copying the source text
- opening the original post when a source URL is available

Everything runs locally in the browser. There is no backend, database, or social API dependency.

## Development

Developer documentation lives separately from user installation notes:

- [Developer guide](docs/development/developer-guide.md)
- [Frontend architecture](docs/architecture/frontend-architecture.md)
- [Service architecture](docs/architecture/service-architecture.md)
- [Video rendering roadmap](docs/roadmap/video-rendering-roadmap.md)

Install dependencies:

```bash
npm install
```

Run the popup preview:

```bash
npm run dev
```

Build the extension:

```bash
npm run build
```

## Test The Extension

1. Run `npm run build`.
2. Open Chrome.
3. Go to `chrome://extensions`.
4. Enable Developer mode.
5. Click Load unpacked.
6. Select the `dist/` folder.
7. Open `https://x.com` or `https://twitter.com`.
8. Hover a post.
9. Click the Quoti extension icon.
10. Verify that the popup shows a context card.
11. Try Copy image, Download JPG, Copy text, Source, Light/Dark, and Text only/With media.
12. Right-click a post and choose Create Quoti card.
13. Open the extension options from the popup settings button to enable or disable hover capture, right-click capture, and the inline Quoti button.

For local UI-only checks, open the Vite preview at `http://localhost:5173/popup.html`. Outside Chrome Extension runtime, the popup uses a built-in preview post.
