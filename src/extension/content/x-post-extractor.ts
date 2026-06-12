import type { ExtractedPost, PostExtractionResult } from "../../shared/types/post.types";

type ContentScriptSettings = {
  hoverCaptureEnabled: boolean;
  contextMenuEnabled: boolean;
  inlineButtonEnabled: boolean;
};

type InlineButtonPlacement = {
  after: ChildNode;
  kind: "meta" | "views";
  target: HTMLElement;
};

const inlineButtonClassName = "quoti-inline-button";
const inlineButtonInsertedClassName = "quoti-inline-button-inserted";
const settingsStorageKey = "quoti-settings";
const defaultContentScriptSettings: ContentScriptSettings = {
  hoverCaptureEnabled: true,
  contextMenuEnabled: true,
  inlineButtonEnabled: false
};

let hoveredArticle: HTMLElement | null = null;
let contextArticle: HTMLElement | null = null;
let initialized = false;
let observer: MutationObserver | null = null;
let pendingInlineInjection = false;
let settings: ContentScriptSettings = defaultContentScriptSettings;

export function initializeXPostExtractor(): void {
  if (initialized) {
    return;
  }

  initialized = true;

  document.addEventListener("mouseover", handleMouseOver, true);
  document.addEventListener("contextmenu", handleContextMenu, true);
  document.addEventListener("click", handleDocumentClick, true);

  observer = new MutationObserver(scheduleInlineButtonInjection);
  observer.observe(document.documentElement, {
    childList: true,
    subtree: true
  });

  void initializeSettings();
}

export function disposeXPostExtractor(): void {
  if (!initialized) {
    return;
  }

  initialized = false;
  hoveredArticle = null;
  contextArticle = null;
  pendingInlineInjection = false;

  document.removeEventListener("mouseover", handleMouseOver, true);
  document.removeEventListener("contextmenu", handleContextMenu, true);
  document.removeEventListener("click", handleDocumentClick, true);
  chrome.storage.onChanged.removeListener(handleStorageChanged);
  observer?.disconnect();
  observer = null;

  removeInlineButtons();
}

export function extractSelectedXPost(): PostExtractionResult {
  return extractArticle(getCandidateArticle(), "Open an X post or hover a post before opening Quoti.");
}

export function extractContextXPost(): PostExtractionResult {
  return extractArticle(contextArticle?.isConnected ? contextArticle : getCandidateArticle(), "Right-click a post before choosing Create Quoti card.");
}

export function extractInlineXPost(postId: string): PostExtractionResult {
  const article = document.querySelector<HTMLElement>(`article[data-quoti-post-id="${postId}"]`);

  return extractArticle(article, "Quoti could not read this post.");
}

function handleMouseOver(event: MouseEvent): void {
  if (!settings.hoverCaptureEnabled) {
    return;
  }

  const target = event.target;

  if (!(target instanceof HTMLElement)) {
    return;
  }

  const article = target.closest("article");

  hoveredArticle = article instanceof HTMLElement ? article : null;
}

function handleContextMenu(event: MouseEvent): void {
  const target = event.target;

  if (!(target instanceof HTMLElement)) {
    return;
  }

  const article = target.closest("article");

  contextArticle = article instanceof HTMLElement ? article : null;
}

function handleDocumentClick(event: MouseEvent): void {
  const target = event.target;

  if (!(target instanceof HTMLElement)) {
    return;
  }

  const button = target.closest<HTMLButtonElement>(`.${inlineButtonClassName}`);

  if (!button?.dataset.quotiPostId) {
    return;
  }

  event.preventDefault();
  event.stopPropagation();
  void captureInlinePost(button.dataset.quotiPostId);
}

function extractArticle(article: HTMLElement | null, emptyReason: string): PostExtractionResult {
  if (!article) {
    return {
      status: "empty",
      reason: emptyReason
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

async function initializeSettings(): Promise<void> {
  settings = await readContentScriptSettings();

  if (!initialized) {
    return;
  }

  updateInlineButtons();
  chrome.storage.onChanged.addListener(handleStorageChanged);
}

function handleStorageChanged(changes: Record<string, chrome.storage.StorageChange>, areaName: string): void {
  if (areaName !== "sync" || !changes[settingsStorageKey]?.newValue) {
    return;
  }

  settings = {
    ...defaultContentScriptSettings,
    ...changes[settingsStorageKey].newValue
  };
  updateInlineButtons();
}

async function readContentScriptSettings(): Promise<ContentScriptSettings> {
  const stored = await chrome.storage.sync.get(settingsStorageKey);
  const storedSettings = stored[settingsStorageKey] as Partial<ContentScriptSettings> | undefined;

  return {
    ...defaultContentScriptSettings,
    ...storedSettings
  };
}

function updateInlineButtons(): void {
  if (settings.inlineButtonEnabled) {
    scheduleInlineButtonInjection();
    return;
  }

  removeInlineButtons();
}

function scheduleInlineButtonInjection(): void {
  if (!initialized || !settings.inlineButtonEnabled || pendingInlineInjection) {
    return;
  }

  pendingInlineInjection = true;

  window.requestAnimationFrame(() => {
    pendingInlineInjection = false;

    if (initialized && settings.inlineButtonEnabled) {
      injectInlineButtons();
      pruneDisconnectedArticleReferences();
    }
  });
}

function removeInlineButtons(): void {
  document.querySelectorAll(`.${inlineButtonClassName}`).forEach((button) => button.remove());
  document.querySelectorAll(`.${inlineButtonInsertedClassName}`).forEach((article) => article.classList.remove(inlineButtonInsertedClassName));
}

function injectInlineButtons(): void {
  const articles = Array.from(document.querySelectorAll<HTMLElement>("article"));

  articles.forEach((article, index) => {
    if (!shouldInjectInlineButton(article)) {
      removeArticleInlineButtons(article);
      return;
    }

    const placement = getInlineButtonPlacement(article);

    if (!placement) {
      return;
    }

    const postId = article.dataset.quotiPostId ?? `quoti-post-${Date.now()}-${index}`;
    article.dataset.quotiPostId = postId;
    article.classList.add(inlineButtonInsertedClassName);

    const existingButtons = Array.from(article.querySelectorAll<HTMLButtonElement>(`.${inlineButtonClassName}`));

    if (existingButtons.length > 0) {
      existingButtons.slice(1).forEach((button) => button.remove());
      updateInlineButton(existingButtons[0], postId, placement);
      return;
    }

    const button = document.createElement("button");
    button.type = "button";
    button.textContent = "Quoti";
    button.setAttribute("aria-label", "Create Quoti card");
    updateInlineButton(button, postId, placement);
  });
}

function shouldInjectInlineButton(article: HTMLElement): boolean {
  if (isStatusPage() && !findViewsElement(article)) {
    return false;
  }

  return Boolean(article.querySelector('[data-testid="tweetText"]'));
}

function removeArticleInlineButtons(article: HTMLElement): void {
  article.querySelectorAll(`.${inlineButtonClassName}`).forEach((button) => button.remove());
  article.classList.remove(inlineButtonInsertedClassName);
}

function updateInlineButton(button: HTMLButtonElement, postId: string, placement: InlineButtonPlacement): void {
  button.className = `${inlineButtonClassName} ${inlineButtonClassName}--${placement.kind}`;
  button.dataset.quotiPostId = postId;

  if (button.parentElement !== placement.target || button.previousSibling !== placement.after) {
    placement.target.insertBefore(button, placement.after.nextSibling);
  }
}

async function captureInlinePost(postId: string): Promise<void> {
  const result = extractInlineXPost(postId);
  const message =
    result.status === "success"
      ? {
          type: "QUOTI_INLINE_POST_CAPTURED" as const,
          post: result.post
        }
      : {
          type: "QUOTI_INLINE_POST_CAPTURED" as const
        };

  try {
    await chrome.runtime.sendMessage(message);
  } catch {
    try {
      await chrome.runtime.sendMessage({ type: "QUOTI_OPEN_POPUP" });
    } catch {
      // The post is already stored; the next manual popup open will pick it up.
    }
  }
}

function getInlineButtonPlacement(article: HTMLElement): InlineButtonPlacement | null {
  const viewsElement = findViewsElement(article);

  if (viewsElement?.parentElement) {
    const target = viewsElement.parentElement;
    target.style.display = "inline-flex";
    target.style.alignItems = "baseline";
    target.style.flexWrap = "wrap";

    return {
      after: viewsElement,
      kind: "views",
      target
    };
  }

  if (isStatusPage()) {
    return null;
  }

  const time = article.querySelector<HTMLTimeElement>("time");
  const timeLink = time?.closest("a");
  const timeContainer = timeLink?.parentElement;

  if (timeContainer instanceof HTMLElement && timeLink) {
    return {
      after: timeLink,
      kind: "meta",
      target: timeContainer
    };
  }

  return null;
}

function isStatusPage(): boolean {
  return /\/status\/\d+/.test(window.location.pathname);
}

function findViewsElement(article: HTMLElement): HTMLElement | null {
  const candidates = Array.from(article.querySelectorAll<HTMLElement>("span"));

  return candidates
    .filter((candidate) => candidate.children.length === 0)
    .map((candidate) => ({
      candidate,
      text: normalizeText(candidate.textContent)
    }))
    .filter(({ text }) => text.length <= 32 && /[\d,.]\s*[\w,.\s]*\s(?:vues|views|posts?)$/i.test(text))
    .sort((a, b) => a.text.length - b.text.length)[0]?.candidate ?? null;
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

function pruneDisconnectedArticleReferences(): void {
  if (hoveredArticle && !hoveredArticle.isConnected) {
    hoveredArticle = null;
  }

  if (contextArticle && !contextArticle.isConnected) {
    contextArticle = null;
  }
}

function extractPostFromArticle(article: HTMLElement): ExtractedPost {
  const authorBlock = article.querySelector<HTMLElement>('[data-testid="User-Name"]');
  const authorName = readAuthorName(authorBlock);
  const authorHandle = readAuthorHandle(authorBlock);
  const content = readPostContent(article);
  const time = article.querySelector<HTMLTimeElement>("time");
  const sourceUrl = readSourceUrl(time);
  const media = readPostImages(article);

  return {
    id: sourceUrl ?? `${authorHandle}-${content.slice(0, 32)}`,
    platform: "x",
    authorName,
    authorHandle,
    content,
    publishedAt: time?.dateTime,
    sourceUrl,
    media,
    capturedAt: new Date().toISOString()
  };
}

function readAuthorName(authorBlock: HTMLElement | null): string {
  const spans = Array.from(authorBlock?.querySelectorAll("span") ?? []);
  const name = spans
    .map((span) => normalizeText(span.textContent))
    .find((text) => Boolean(text) && !text.startsWith("@") && text !== "Â·");

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

function readPostImages(article: HTMLElement) {
  const imageUrls = new Set<string>();

  return Array.from(article.querySelectorAll<HTMLImageElement>('img[src*="pbs.twimg.com/media/"]'))
    .map((image) => {
      const normalizedUrl = normalizeImageUrl(image.currentSrc || image.src);

      if (!normalizedUrl || imageUrls.has(normalizedUrl)) {
        return null;
      }

      imageUrls.add(normalizedUrl);

      return {
        type: "image" as const,
        url: normalizedUrl,
        alt: normalizeText(image.alt)
      };
    })
    .filter((media) => media !== null);
}

function normalizeImageUrl(value: string): string | null {
  if (!value) {
    return null;
  }

  let url: URL;

  try {
    url = new URL(value);
  } catch {
    return null;
  }

  if (!url.hostname.endsWith("twimg.com") || !url.pathname.includes("/media/")) {
    return null;
  }

  url.searchParams.set("name", "large");

  return url.toString();
}

function normalizeText(value: string | null | undefined): string {
  return value?.replace(/\s+\n/g, "\n").replace(/\n\s+/g, "\n").replace(/[ \t]+/g, " ").trim() ?? "";
}
