import type { ExtractedPost, ImagePostMedia, PostExtractionResult, PostMedia, RelatedPost, VideoPostMedia } from "../../shared/types/post.types";

type ContentScriptSettings = {
  hoverCaptureEnabled: boolean;
  contextMenuEnabled: boolean;
  inlineButtonEnabled: boolean;
};

type StoredContentScriptSettings = Partial<ContentScriptSettings> & {
  inlineButtonPreferenceVersion?: number;
};

type InlineButtonPlacement = {
  after: ChildNode;
  kind: "meta" | "views";
  target: HTMLElement;
};

const inlineButtonClassName = "quoti-inline-button";
const inlineButtonInsertedClassName = "quoti-inline-button-inserted";
const settingsStorageKey = "quoti-settings";
const inlineButtonPreferenceVersion = 1;
const maxLocalObservedVideoUrls = 120;
const defaultContentScriptSettings: ContentScriptSettings = {
  hoverCaptureEnabled: true,
  contextMenuEnabled: true,
  inlineButtonEnabled: true
};

let hoveredArticle: HTMLElement | null = null;
let contextArticle: HTMLElement | null = null;
let initialized = false;
let localObservedVideoUrls: string[] = [];
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

export function extractSelectedXPost(observedVideoUrls: string[] = []): PostExtractionResult {
  return extractArticle(getCandidateArticle(), "Open an X post or hover a post before opening Quoti.", observedVideoUrls);
}

export function extractContextXPost(observedVideoUrls: string[] = []): PostExtractionResult {
  return extractArticle(
    contextArticle?.isConnected ? contextArticle : getCandidateArticle(),
    "Right-click a post before choosing Create Quoti card.",
    observedVideoUrls
  );
}

export function extractInlineXPost(postId: string, observedVideoUrls: string[] = []): PostExtractionResult {
  const article = document.querySelector<HTMLElement>(`article[data-quoti-post-id="${postId}"]`);

  return extractArticle(article, "Quoti could not read this post.", observedVideoUrls);
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

function extractArticle(article: HTMLElement | null, emptyReason: string, observedVideoUrls: string[]): PostExtractionResult {
  if (!article) {
    return {
      status: "empty",
      reason: emptyReason
    };
  }

  const post = extractPostFromArticle(article, observedVideoUrls);

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

  settings = normalizeContentScriptSettings(changes[settingsStorageKey].newValue as StoredContentScriptSettings);
  updateInlineButtons();
}

async function readContentScriptSettings(): Promise<ContentScriptSettings> {
  const stored = await chrome.storage.sync.get(settingsStorageKey);
  const storedSettings = stored[settingsStorageKey] as StoredContentScriptSettings | undefined;

  return normalizeContentScriptSettings(storedSettings);
}

function normalizeContentScriptSettings(storedSettings: StoredContentScriptSettings | undefined): ContentScriptSettings {
  return {
    ...defaultContentScriptSettings,
    ...storedSettings,
    inlineButtonEnabled:
      storedSettings?.inlineButtonPreferenceVersion === inlineButtonPreferenceVersion
        ? Boolean(storedSettings.inlineButtonEnabled)
        : defaultContentScriptSettings.inlineButtonEnabled
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
      removeArticleInlineButtons(article);
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
  const result = await extractInlineXPostWithVideoRetry(postId);
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

async function extractInlineXPostWithVideoRetry(postId: string): Promise<PostExtractionResult> {
  let latestResult: PostExtractionResult | null = null;

  for (const delay of [0, 650, 1250]) {
    if (delay > 0) {
      await wait(delay);
    }

    const result = extractInlineXPost(postId, await requestObservedVideoUrls());
    latestResult = result;

    if (result.status !== "success" || !hasMissingVideoUrl(result.post)) {
      return result;
    }
  }

  return latestResult ?? {
    status: "empty",
    reason: "Quoti could not read this post."
  };
}

function hasMissingVideoUrl(post: ExtractedPost): boolean {
  return getAllPostMedia(post).some((media) => media.type === "video" && !hasVideoMediaSource(media));
}

function hasVideoMediaSource(media: VideoPostMedia): boolean {
  return filterVideoUrlsForPoster(media.posterUrl, [media.url, ...(media.variants ?? [])]).length > 0;
}

function getAllPostMedia(post: ExtractedPost): PostMedia[] {
  return [...post.media, ...(post.relatedPost?.media ?? [])];
}

async function requestObservedVideoUrls(): Promise<string[]> {
  try {
    const response = await chrome.runtime.sendMessage({ type: "QUOTI_READ_OBSERVED_VIDEO_URLS" });

    return response?.status === "video-urls" && Array.isArray(response.observedVideoUrls) ? response.observedVideoUrls : [];
  } catch {
    return [];
  }
}

function wait(duration: number): Promise<void> {
  return new Promise((resolve) => window.setTimeout(resolve, duration));
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

function findViewsElement(article: HTMLElement): HTMLElement | null {
  const candidates = Array.from(article.querySelectorAll<HTMLElement>("span, a, div"));

  return candidates
    .map((candidate) => ({
      candidate,
      text: normalizeText(candidate.textContent)
    }))
    .filter(({ candidate, text }) => !candidate.classList.contains(inlineButtonClassName) && isViewsText(text))
    .sort((a, b) => a.text.length - b.text.length)[0]?.candidate ?? null;
}

function isViewsText(text: string): boolean {
  return text.length <= 80 && /\d[\d\s.,]*\s*[km]?\s*(?:vues?|views?)\b/i.test(text);
}

function getCandidateArticle(): HTMLElement | null {
  const selectionArticle = getSelectionArticle();

  if (selectionArticle) {
    return selectionArticle;
  }

  const mostVisibleArticle = getMostVisibleArticle();

  if (mostVisibleArticle) {
    return mostVisibleArticle;
  }

  return hoveredArticle?.isConnected ? hoveredArticle : null;
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

function extractPostFromArticle(article: HTMLElement, observedVideoUrls: string[]): ExtractedPost {
  const authorBlock = article.querySelector<HTMLElement>('[data-testid="User-Name"]');
  const authorName = readAuthorName(authorBlock);
  const authorHandle = readAuthorHandle(authorBlock);
  const { content, relatedContainer, relatedPost } = readPostContent(article, observedVideoUrls);
  const time = article.querySelector<HTMLTimeElement>("time");
  const sourceUrl = readSourceUrl(time);
  const media = readPostMedia(article, observedVideoUrls, relatedContainer);

  return {
    id: sourceUrl ?? `${authorHandle}-${content.slice(0, 32)}`,
    platform: "x",
    authorName,
    authorHandle,
    content,
    relatedPost,
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
    .find((text) => Boolean(text) && !text.startsWith("@") && !isAuthorSeparator(text));

  return name ?? "Unknown author";
}

function isAuthorSeparator(text: string): boolean {
  return /^[·•]+$/.test(text) || text === "Â·";
}

function readAuthorHandle(authorBlock: HTMLElement | null): string {
  const handle = Array.from(authorBlock?.querySelectorAll("span") ?? [])
    .map((span) => normalizeText(span.textContent))
    .find((text) => text.startsWith("@"));

  return handle ?? "";
}

function readPostContent(
  article: HTMLElement,
  observedVideoUrls: string[]
): { content: string; relatedContainer?: HTMLElement | null; relatedPost?: RelatedPost } {
  const tweetTextBlocks = Array.from(article.querySelectorAll<HTMLElement>('[data-testid="tweetText"]'));
  const postBlocks = tweetTextBlocks
    .map((block) => ({
      block,
      content: readTweetTextBlockContent(block)
    }))
    .filter(({ content }) => Boolean(content));
  const text = postBlocks[0]?.content ?? "";

  if (text) {
    const related = readRelatedPost(article, postBlocks[0].block, postBlocks.slice(1), observedVideoUrls);

    return {
      content: text,
      relatedContainer: related?.container,
      relatedPost: related?.post
    };
  }

  const fallbackText = normalizePostContent(article.innerText);
  const linesToRemove = new Set(["Reply", "Repost", "Like", "View", "Share"]);

  return {
    content: fallbackText
      .split("\n")
      .map((line) => line.trim())
      .filter((line) => line && !linesToRemove.has(line))
      .slice(0, 12)
      .join("\n")
  };
}

function readRelatedPost(
  article: HTMLElement,
  primaryBlock: HTMLElement,
  postBlocks: Array<{ block: HTMLElement; content: string }>,
  observedVideoUrls: string[]
): { container: HTMLElement | null; post: RelatedPost } | undefined {
  const relatedBlock = postBlocks[0];

  if (!relatedBlock) {
    return undefined;
  }

  const relatedContainer = findRelatedPostContainer(article, relatedBlock.block, primaryBlock);
  const authorBlock = relatedContainer?.querySelector<HTMLElement>('[data-testid="User-Name"]') ?? null;
  const media = relatedContainer ? readPostMedia(relatedContainer, observedVideoUrls) : [];
  const content = readBestRelatedPostContent(relatedBlock.content, relatedContainer, article);
  const sourceUrl = relatedContainer ? readRelatedPostSourceUrl(relatedContainer) : undefined;

  return {
    container: relatedContainer,
    post: {
      authorName: authorBlock ? readAuthorName(authorBlock) : undefined,
      authorHandle: authorBlock ? readAuthorHandle(authorBlock) : undefined,
      content,
      media,
      sourceUrl
    }
  };
}

function readBestRelatedPostContent(visibleContent: string, relatedContainer: HTMLElement | null, article: HTMLElement): string {
  const roots = [relatedContainer, article].filter((root): root is HTMLElement => Boolean(root));

  if (roots.length === 0) {
    return visibleContent;
  }

  const candidates = roots.flatMap(readReactTextCandidates);
  const normalizedVisibleContent = normalizePostContent(visibleContent);
  const compactVisibleContent = compactText(normalizedVisibleContent);

  return (
    candidates
      .map((content) => buildRelatedPostContentCandidate(content, normalizedVisibleContent))
      .filter((content) => content.length > normalizedVisibleContent.length && content.length <= 1000)
      .filter((content) => compactText(content).startsWith(compactVisibleContent))
      .sort((a, b) => scoreRelatedPostContentCandidate(b, normalizedVisibleContent) - scoreRelatedPostContentCandidate(a, normalizedVisibleContent))[0] ??
    visibleContent
  );
}

function buildRelatedPostContentCandidate(content: string, visibleContent: string): string {
  const fullContentCandidate = sliceCandidateFromVisibleContent(content, visibleContent);

  if (compactText(fullContentCandidate).startsWith(compactText(visibleContent))) {
    return fullContentCandidate;
  }

  const visibleParagraph = getLastVisibleParagraph(visibleContent);

  if (!visibleParagraph) {
    return fullContentCandidate;
  }

  const paragraphCandidate = sliceCandidateFromVisibleContent(content, visibleParagraph);

  if (!compactText(paragraphCandidate).startsWith(compactText(visibleParagraph))) {
    return fullContentCandidate;
  }

  const paragraphIndex = visibleContent.lastIndexOf(visibleParagraph);

  return paragraphIndex >= 0 ? `${visibleContent.slice(0, paragraphIndex)}${paragraphCandidate}`.trim() : fullContentCandidate;
}

function getLastVisibleParagraph(content: string): string {
  return (
    content
      .split(/\n+/)
      .map((paragraph) => paragraph.trim())
      .filter(Boolean)
      .at(-1) ?? ""
  );
}

function sliceCandidateFromVisibleContent(content: string, visibleContent: string): string {
  const normalizedContent = normalizePostContent(content);
  const visibleIndex = normalizedContent.indexOf(visibleContent);

  return visibleIndex >= 0 ? normalizedContent.slice(visibleIndex).trim() : normalizedContent;
}

function scoreRelatedPostContentCandidate(content: string, visibleContent: string): number {
  const extraLength = content.length - visibleContent.length;
  const tail = content.trim().slice(-8);
  const hasClosingQuote = /(?:\u00bb|"|\u201d)/.test(tail) ? 120 : 0;
  const hasSentenceEnding = /[.!?\u2026\u00bb"\u201d]/.test(tail) ? 60 : 0;
  const conciseBonus = Math.max(0, 80 - Math.abs(extraLength - 32));

  return hasClosingQuote + hasSentenceEnding + conciseBonus;
}

function readReactTextCandidates(root: HTMLElement): string[] {
  const candidates = new Set<string>();
  const seen = new WeakSet<object>();
  const nodes = [root, ...Array.from(root.querySelectorAll<HTMLElement>("*"))].slice(0, 160);

  nodes.forEach((node) => {
    Object.keys(node)
      .filter((key) => key.startsWith("__reactProps$") || key.startsWith("__reactFiber$"))
      .forEach((key) => {
        const reactValue = (node as unknown as Record<string, unknown>)[key];
        const renderedText = normalizePostContent(readReactRenderedText(reactValue, new WeakSet<object>(), 0, false));

        if (renderedText.length >= 12 && /\s/.test(renderedText)) {
          candidates.add(renderedText);
        }

        collectReactTextCandidates(reactValue, candidates, seen, 0);
      });
  });

  return [...candidates];
}

function readReactRenderedText(value: unknown, seen: WeakSet<object>, depth: number, includeSibling: boolean): string {
  if (depth > 12 || value === null || value === undefined) {
    return "";
  }

  if (typeof value === "string" || typeof value === "number") {
    return String(value);
  }

  if (typeof value !== "object" || seen.has(value)) {
    return "";
  }

  seen.add(value);

  if (value instanceof Node || value instanceof Window) {
    return "";
  }

  if (Array.isArray(value)) {
    return value.map((item) => readReactRenderedText(item, seen, depth + 1, false)).join("");
  }

  const record = value as Record<string, unknown>;
  const props = readReactProps(record);
  const parts: string[] = [];

  if (props) {
    const propsRecord = props as Record<string, unknown>;

    if (typeof propsRecord.alt === "string") {
      parts.push(propsRecord.alt);
    }

    parts.push(readReactRenderedText(propsRecord.children, seen, depth + 1, false));
  }

  parts.push(readReactRenderedText(record.child, seen, depth + 1, true));

  if (includeSibling) {
    parts.push(readReactRenderedText(record.sibling, seen, depth + 1, true));
  }

  return parts.join("");
}

function readReactProps(record: Record<string, unknown>): unknown {
  return record.memoizedProps ?? record.pendingProps ?? record.props;
}

function collectReactTextCandidates(value: unknown, candidates: Set<string>, seen: WeakSet<object>, depth: number): void {
  if (depth > 14 || candidates.size > 160 || value === null || value === undefined) {
    return;
  }

  if (typeof value === "string") {
    const content = normalizePostContent(value);

    if (content.length >= 12 && /\s/.test(content)) {
      candidates.add(content);
    }
    return;
  }

  if (typeof value !== "object" || seen.has(value)) {
    return;
  }

  seen.add(value);

  if (value instanceof Node || value instanceof Window) {
    return;
  }

  collectKnownTweetTextCandidates(value as Record<string, unknown>, candidates, seen, depth);

  if (Array.isArray(value)) {
    value.slice(0, 120).forEach((item) => collectReactTextCandidates(item, candidates, seen, depth + 1));
    return;
  }

  Object.entries(value as Record<string, unknown>)
    .sort(([keyA], [keyB]) => scoreReactTextCandidateKey(keyB) - scoreReactTextCandidateKey(keyA))
    .slice(0, 240)
    .forEach(([, item]) => collectReactTextCandidates(item, candidates, seen, depth + 1));
}

function collectKnownTweetTextCandidates(record: Record<string, unknown>, candidates: Set<string>, seen: WeakSet<object>, depth: number): void {
  [
    record.note_tweet,
    record.noteTweet,
    record.note_tweet_results,
    record.noteTweetResults,
    record.legacy,
    record.tweet,
    record.result
  ].forEach((candidate) => collectReactTextCandidates(candidate, candidates, seen, depth + 1));

  ["full_text", "fullText", "text"].forEach((key) => {
    const value = record[key];

    if (typeof value === "string") {
      const content = normalizePostContent(value);

      if (content.length >= 12 && /\s/.test(content)) {
        candidates.add(content);
      }
    }
  });
}

function scoreReactTextCandidateKey(key: string): number {
  if (/note|tweet|legacy|result|full_text|fullText|text/i.test(key)) {
    return 2;
  }

  if (/child|sibling|props|memoized|pending/i.test(key)) {
    return 1;
  }

  return 0;
}

function readTweetTextBlockContent(block: HTMLElement): string {
  const parts: string[] = [];
  const visit = (node: ChildNode): void => {
    if (node.nodeType === Node.TEXT_NODE) {
      parts.push(node.textContent ?? "");
      return;
    }

    if (!(node instanceof HTMLElement)) {
      return;
    }

    if (node.tagName === "BR") {
      parts.push("\n");
      return;
    }

    if (node instanceof HTMLImageElement && node.alt) {
      parts.push(node.alt);
      return;
    }

    node.childNodes.forEach(visit);
  };

  block.childNodes.forEach(visit);

  return normalizePostContent(parts.join(""));
}

function readRelatedPostSourceUrl(relatedContainer: HTMLElement): string | undefined {
  const timeUrl = readSourceUrl(relatedContainer.querySelector<HTMLTimeElement>("time"));

  if (timeUrl) {
    return timeUrl;
  }

  return Array.from(relatedContainer.querySelectorAll<HTMLAnchorElement>('a[href*="/status/"]'))
    .map((link) => normalizeStatusUrl(link.getAttribute("href")))
    .find((url): url is string => Boolean(url));
}

function normalizeStatusUrl(href: string | null): string | undefined {
  if (!href) {
    return undefined;
  }

  try {
    const url = new URL(href, window.location.origin);

    return /\/status\/\d+/.test(url.pathname) ? url.toString() : undefined;
  } catch {
    return undefined;
  }
}

function findRelatedPostContainer(article: HTMLElement, block: HTMLElement, primaryBlock: HTMLElement): HTMLElement | null {
  let current = block.parentElement;

  while (current && current !== article) {
    if (current.contains(primaryBlock)) {
      return null;
    }

    if (current.querySelector('[data-testid="User-Name"]') && containsOnlyTweetTextBlock(current, block)) {
      return current;
    }

    current = current.parentElement;
  }

  return null;
}

function containsOnlyTweetTextBlock(container: HTMLElement, block: HTMLElement): boolean {
  const blocks = Array.from(container.querySelectorAll<HTMLElement>('[data-testid="tweetText"]'));

  return blocks.length === 1 && blocks[0] === block;
}

function readSourceUrl(time: HTMLTimeElement | null): string | undefined {
  const link = time?.closest("a");
  const href = link?.getAttribute("href");

  if (!href) {
    return undefined;
  }

  return new URL(href, window.location.origin).toString();
}

function readPostMedia(root: HTMLElement, observedVideoUrls: string[], excludedContainer?: HTMLElement | null): PostMedia[] {
  const videos = readPostVideos(root, observedVideoUrls, excludedContainer);
  const images = readPostImages(root, excludedContainer);

  return [...videos, ...images];
}

function readPostVideos(root: HTMLElement, observedVideoUrls: string[], excludedContainer?: HTMLElement | null): VideoPostMedia[] {
  const videoKeys = new Set<string>();
  const videos = Array.from(root.querySelectorAll<HTMLVideoElement>("video")).filter((video) => !isInsideExcludedContainer(video, excludedContainer));
  const articleVideoUrls = readArticleAttachedVideoUrls(root);

  return videos
    .map((video): VideoPostMedia | null => {
      const posterUrl = normalizeImageUrl(video.poster) ?? findVideoPosterUrl(video);
      const videoUrls = readVideoUrls(video, posterUrl, observedVideoUrls, articleVideoUrls);
      const url = videoUrls[0];
      const key = url ?? posterUrl;

      if (!key || videoKeys.has(key)) {
        return null;
      }

      videoKeys.add(key);

      return {
        type: "video" as const,
        url,
        posterUrl,
        variants: videoUrls,
        alt: readVideoAlt(video),
        duration: Number.isFinite(video.duration) && video.duration > 0 ? video.duration : undefined
      };
    })
    .filter((media): media is VideoPostMedia => media !== null);
}

function readPostImages(root: HTMLElement, excludedContainer?: HTMLElement | null): ImagePostMedia[] {
  const imageUrls = new Set<string>();

  return Array.from(root.querySelectorAll<HTMLImageElement>("img"))
    .filter((image) => !isInsideExcludedContainer(image, excludedContainer))
    .map((image): ImagePostMedia | null => {
      const normalizedUrl = normalizePostImageUrl(readImageSourceUrl(image));

      if (!normalizedUrl || imageUrls.has(normalizedUrl) || !isLikelyPostImage(image)) {
        return null;
      }

      imageUrls.add(normalizedUrl);

      return {
        type: "image" as const,
        url: normalizedUrl,
        alt: normalizeText(image.alt)
      };
    })
    .filter((media): media is ImagePostMedia => media !== null);
}

function isLikelyPostImage(image: HTMLImageElement): boolean {
  if (image.closest('[data-testid^="UserAvatar-Container"], [data-testid="UserAvatar-Container-unknown"]')) {
    return false;
  }

  const rect = image.getBoundingClientRect();

  if (rect.width > 0 && rect.height > 0 && Math.max(rect.width, rect.height) < 96) {
    return false;
  }

  return true;
}

function isInsideExcludedContainer(element: Element, excludedContainer: HTMLElement | null | undefined): boolean {
  return Boolean(excludedContainer?.contains(element));
}

function readVideoUrls(
  video: HTMLVideoElement,
  posterUrl: string | undefined,
  observedVideoUrls: string[],
  articleVideoUrls: string[]
): string[] {
  rememberLocalObservedVideoUrls(observedVideoUrls);
  rememberLocalObservedVideoUrls(articleVideoUrls);

  const candidates = [
    video.currentSrc,
    video.src,
    ...Array.from(video.querySelectorAll<HTMLSourceElement>("source")).map((source) => source.src),
    ...readObservedVideoUrls(posterUrl, articleVideoUrls),
    ...readObservedVideoUrls(posterUrl, observedVideoUrls),
    ...readObservedVideoUrls(posterUrl, readLocalObservedVideoUrls())
  ];

  return filterVideoUrlsForPoster(posterUrl, candidates);
}

function readArticleAttachedVideoUrls(article: HTMLElement): string[] {
  const urls = new Set<string>();
  const seen = new WeakSet<object>();
  const nodes = [article, ...Array.from(article.querySelectorAll<HTMLElement>("*"))].slice(0, 220);

  nodes.forEach((node) => {
    Object.keys(node)
      .filter((key) => key.startsWith("__reactProps$") || key.startsWith("__reactFiber$"))
      .forEach((key) => collectTwitterVideoUrls((node as unknown as Record<string, unknown>)[key], urls, seen, 0));
  });

  return [...urls].sort((a, b) => scoreVideoUrl(b) - scoreVideoUrl(a));
}

function collectTwitterVideoUrls(value: unknown, urls: Set<string>, seen: WeakSet<object>, depth: number): void {
  if (depth > 10 || urls.size > 120 || value === null || value === undefined) {
    return;
  }

  if (typeof value === "string") {
    extractTwitterVideoUrls(value).forEach((url) => urls.add(url));
    return;
  }

  if (typeof value !== "object") {
    return;
  }

  if (seen.has(value)) {
    return;
  }

  seen.add(value);

  if (value instanceof Node || value instanceof Window) {
    return;
  }

  if (Array.isArray(value)) {
    value.slice(0, 80).forEach((item) => collectTwitterVideoUrls(item, urls, seen, depth + 1));
    return;
  }

  Object.entries(value as Record<string, unknown>)
    .slice(0, 160)
    .forEach(([, item]) => collectTwitterVideoUrls(item, urls, seen, depth + 1));
}

function extractTwitterVideoUrls(value: string): string[] {
  const matches = value.match(/https?:\/\/video\.twimg\.com\/[^"'\s\\<>]+/g) ?? [];

  return matches
    .map((url) => url.replace(/\\u0026/g, "&").replace(/&amp;/g, "&"))
    .map(normalizeVideoUrl)
    .filter((url): url is string => Boolean(url))
    .filter((url) => !isVideoSegmentUrl(url));
}

function readObservedVideoUrls(posterUrl: string | undefined, observedVideoUrls: string[]): string[] {
  return filterVideoUrlsForPoster(posterUrl, observedVideoUrls);
}

function filterVideoUrlsForPoster(posterUrl: string | undefined, sourceUrls: Array<string | undefined>): string[] {
  const mediaId = extractTwitterVideoMediaId(posterUrl);
  const urls = sourceUrls
    .map(normalizeVideoUrl)
    .filter((url): url is string => Boolean(url))
    .filter((url) => !isVideoSegmentUrl(url));

  if (!mediaId) {
    return [...new Set(urls)].sort((a, b) => scoreVideoUrl(b) - scoreVideoUrl(a));
  }

  return [...new Set(urls.filter((url) => isMatchingTwitterVideoSourceId(url, mediaId)))].sort((a, b) => scoreVideoUrl(b) - scoreVideoUrl(a));
}

function readLocalObservedVideoUrls(): string[] {
  try {
    rememberLocalObservedVideoUrls(performance.getEntriesByType("resource").map((entry) => entry.name));
  } catch {
    // Keep the URLs already seen during previous captures.
  }

  return localObservedVideoUrls;
}

function rememberLocalObservedVideoUrls(urls: string[]): void {
  const nextUrls = [...localObservedVideoUrls];

  urls.forEach((url) => {
    if (!url || nextUrls.includes(url)) {
      return;
    }

    nextUrls.push(url);
  });

  localObservedVideoUrls = nextUrls.slice(-maxLocalObservedVideoUrls);
}

function extractTwitterVideoMediaId(value: string | undefined): string | undefined {
  if (!value) {
    return undefined;
  }

  let url: URL;

  try {
    url = new URL(value);
  } catch {
    return undefined;
  }

  return /\/(?:ext_tw_video_thumb|amplify_video_thumb|tweet_video_thumb)\/([^/]+)\//.exec(url.pathname)?.[1];
}

function isVideoSegmentUrl(value: string): boolean {
  const pathname = new URL(value).pathname.toLowerCase();

  return pathname.endsWith(".m4s") || pathname.endsWith(".ts") || pathname.includes("/seg/") || isTwitterDashInitUrl(pathname) || isTwitterHlsInitUrl(pathname);
}

function scoreVideoUrl(value: string): number {
  const url = new URL(value);
  const pathname = url.pathname.toLowerCase();
  const resolution = /\/(\d{2,5})x(\d{2,5})(?:\/|$)/.exec(pathname);
  const pixels = resolution ? Number(resolution[1]) * Number(resolution[2]) : 0;
  let score = pixels / 1000;

  if (pathname.endsWith(".m3u8")) {
    score += 160_000;
  }

  if (isLikelyPlayableMp4(pathname)) {
    score += 120_000;
  }

  return score;
}

function isTwitterDashInitUrl(pathname: string): boolean {
  return pathname.endsWith(".mp4") && /\/vid\/avc1\/0\/0\/\d{2,5}x\d{2,5}\//.test(pathname);
}

function isTwitterHlsInitUrl(pathname: string): boolean {
  return pathname.endsWith(".mp4") && pathname.includes("/pl/") && /(?:^|\/)(init|map)\.mp4$/.test(pathname);
}

function isLikelyPlayableMp4(pathname: string): boolean {
  return pathname.endsWith(".mp4") && !isTwitterDashInitUrl(pathname) && !isTwitterHlsInitUrl(pathname) && !pathname.includes("/pl/");
}

function normalizeVideoUrl(value: string | undefined): string | null {
  if (!value || value.startsWith("blob:") || value.startsWith("data:")) {
    return null;
  }

  let url: URL;

  try {
    url = new URL(value);
  } catch {
    return null;
  }

  if (!["http:", "https:"].includes(url.protocol) || !url.hostname.endsWith("twimg.com")) {
    return null;
  }

  if (isLikelyAudioOnlySourceUrl(url.toString())) {
    return null;
  }

  return url.toString();
}

function extractTwitterVideoSourceId(value: string): string | undefined {
  try {
    const pathname = new URL(value).pathname;

    return /\/(?:ext_tw_video|amplify_video|tweet_video)\/([^/]+)\//.exec(pathname)?.[1];
  } catch {
    return undefined;
  }
}

function isMatchingTwitterVideoSourceId(value: string, mediaId: string): boolean {
  return extractTwitterVideoSourceId(value) === mediaId;
}

function isLikelyAudioOnlySourceUrl(value: string): boolean {
  try {
    const pathname = new URL(value).pathname.toLowerCase();

    return /(?:^|\/)(?:audio|aud|mp4a|aac)(?:[./_-]|\/|$)/.test(pathname);
  } catch {
    return false;
  }
}

function findVideoPosterUrl(video: HTMLVideoElement): string | undefined {
  const labelledContainer = video.closest<HTMLElement>('[aria-label], [role="group"]');
  const nearbyImages = Array.from(labelledContainer?.querySelectorAll<HTMLImageElement>("img") ?? []);

  return nearbyImages
    .map((image) => normalizeImageUrl(image.currentSrc || image.src))
    .find((url): url is string => Boolean(url));
}

function readVideoAlt(video: HTMLVideoElement): string | undefined {
  const label = normalizeText(video.getAttribute("aria-label") ?? video.closest<HTMLElement>("[aria-label]")?.getAttribute("aria-label"));

  return label || undefined;
}

function normalizeImageUrl(value: string): string | null {
  return normalizeTwitterImageUrl(value, {
    allowCardImage: false,
    allowVideoThumb: true
  });
}

function normalizePostImageUrl(value: string | undefined): string | null {
  return normalizeTwitterImageUrl(value, {
    allowCardImage: true,
    allowVideoThumb: false
  });
}

function normalizeTwitterImageUrl(
  value: string | undefined,
  options: {
    allowCardImage: boolean;
    allowVideoThumb: boolean;
  }
): string | null {
  if (!value) {
    return null;
  }

  let url: URL;

  try {
    url = new URL(value);
  } catch {
    return null;
  }

  if (!url.hostname.endsWith("twimg.com")) {
    return null;
  }

  const pathname = url.pathname;
  const isPostImage = pathname.includes("/media/") || (options.allowCardImage && pathname.includes("/card_img/"));
  const isVideoThumb = options.allowVideoThumb && pathname.includes("_thumb/");

  if (!isPostImage && !isVideoThumb) {
    return null;
  }

  url.searchParams.set("name", "large");

  return url.toString();
}

function readImageSourceUrl(image: HTMLImageElement): string | undefined {
  return image.currentSrc || image.src || readBestSrcsetCandidate(image.srcset);
}

function readBestSrcsetCandidate(srcset: string): string | undefined {
  return srcset
    .split(",")
    .map((candidate) => candidate.trim().split(/\s+/)[0])
    .filter(Boolean)
    .at(-1);
}

function normalizeText(value: string | null | undefined): string {
  return value?.replace(/\s+\n/g, "\n").replace(/\n\s+/g, "\n").replace(/[ \t]+/g, " ").trim() ?? "";
}

function compactText(value: string): string {
  return value.replace(/\s+/g, " ").trim();
}

function normalizePostContent(value: string | null | undefined): string {
  return (
    value
      ?.replace(/\r\n?/g, "\n")
      .replace(/\u00a0/g, " ")
      .replace(/\b(https?:\/\/)\s*\n+\s*/gi, "$1")
      .replace(/\b(https?:\/\/[^\s\n]+)\n+([A-Za-z0-9._~:/?#[\]@!$&'()*+,;=%-]+)/g, "$1$2")
      .split("\n")
      .map((line) => line.trim())
      .join("\n")
      .replace(/\n{3,}/g, "\n\n")
      .trim() ?? ""
  );
}
