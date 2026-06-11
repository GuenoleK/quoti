import type { ExtractedPost, PostExtractionResult } from "../../shared/types/post.types";

let hoveredArticle: HTMLElement | null = null;

document.addEventListener(
  "mouseover",
  (event) => {
    const target = event.target;

    if (!(target instanceof HTMLElement)) {
      return;
    }

    const article = target.closest("article");

    if (article instanceof HTMLElement) {
      hoveredArticle = article;
    }
  },
  true
);

export function extractSelectedXPost(): PostExtractionResult {
  const article = getCandidateArticle();

  if (!article) {
    return {
      status: "empty",
      reason: "Open an X post or hover a post before opening Quoti."
    };
  }

  const post = extractPostFromArticle(article);

  if (!post.content) {
    return {
      status: "empty",
      reason: "Quoti found a post, but could not read its content."
    };
  }

  return {
    status: "success",
    post
  };
}

function getCandidateArticle(): HTMLElement | null {
  const selectionArticle = getSelectionArticle();

  if (selectionArticle) {
    return selectionArticle;
  }

  if (hoveredArticle?.isConnected) {
    return hoveredArticle;
  }

  return getMostVisibleArticle();
}

function getSelectionArticle(): HTMLElement | null {
  const selection = window.getSelection();

  if (!selection || selection.rangeCount === 0) {
    return null;
  }

  const node = selection.anchorNode;
  const element = node instanceof HTMLElement ? node : node?.parentElement;
  const article = element?.closest("article");

  return article instanceof HTMLElement ? article : null;
}

function getMostVisibleArticle(): HTMLElement | null {
  const articles = Array.from(document.querySelectorAll<HTMLElement>("article"));
  const viewportCenter = window.innerHeight / 2;

  return articles
    .map((article) => {
      const rect = article.getBoundingClientRect();
      const visibleHeight = Math.min(rect.bottom, window.innerHeight) - Math.max(rect.top, 0);
      const distanceFromCenter = Math.abs(rect.top + rect.height / 2 - viewportCenter);

      return {
        article,
        score: Math.max(0, visibleHeight) - distanceFromCenter * 0.1
      };
    })
    .sort((a, b) => b.score - a.score)[0]?.article ?? null;
}

function extractPostFromArticle(article: HTMLElement): ExtractedPost {
  const authorBlock = article.querySelector<HTMLElement>('[data-testid="User-Name"]');
  const authorName = readAuthorName(authorBlock);
  const authorHandle = readAuthorHandle(authorBlock);
  const content = readPostContent(article);
  const time = article.querySelector<HTMLTimeElement>("time");
  const sourceUrl = readSourceUrl(time);

  return {
    id: sourceUrl ?? `${authorHandle}-${content.slice(0, 32)}`,
    platform: "x",
    authorName,
    authorHandle,
    content,
    publishedAt: time?.dateTime,
    sourceUrl,
    capturedAt: new Date().toISOString()
  };
}

function readAuthorName(authorBlock: HTMLElement | null): string {
  const spans = Array.from(authorBlock?.querySelectorAll("span") ?? []);
  const name = spans
    .map((span) => normalizeText(span.textContent))
    .find((text) => Boolean(text) && !text.startsWith("@") && text !== "·");

  return name ?? "Unknown author";
}

function readAuthorHandle(authorBlock: HTMLElement | null): string {
  const handle = Array.from(authorBlock?.querySelectorAll("span") ?? [])
    .map((span) => normalizeText(span.textContent))
    .find((text) => text.startsWith("@"));

  return handle ?? "";
}

function readPostContent(article: HTMLElement): string {
  const tweetTextBlocks = Array.from(article.querySelectorAll<HTMLElement>('[data-testid="tweetText"]'));
  const text = tweetTextBlocks.map((block) => normalizeText(block.innerText)).join("\n\n");

  if (text) {
    return text;
  }

  const fallbackText = normalizeText(article.innerText);
  const linesToRemove = new Set(["Reply", "Repost", "Like", "View", "Share"]);

  return fallbackText
    .split("\n")
    .map((line) => line.trim())
    .filter((line) => line && !linesToRemove.has(line))
    .slice(0, 12)
    .join("\n");
}

function readSourceUrl(time: HTMLTimeElement | null): string | undefined {
  const link = time?.closest("a");
  const href = link?.getAttribute("href");

  if (!href) {
    return undefined;
  }

  return new URL(href, window.location.origin).toString();
}

function normalizeText(value: string | null | undefined): string {
  return value?.replace(/\s+\n/g, "\n").replace(/\n\s+/g, "\n").replace(/[ \t]+/g, " ").trim() ?? "";
}
