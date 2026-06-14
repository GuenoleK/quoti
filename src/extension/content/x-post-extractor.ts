import type { ExtractedPost, ImagePostMedia, PostExtractionResult, PostMedia, RelatedPost, VideoPostMedia } from "../../shared/types/post.types";

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
const maxLocalObservedVideoUrls = 120;
const defaultContentScriptSettings: ContentScriptSettings = {
  hoverCaptureEnabled: true,
  contextMenuEnabled: true,
  inlineButtonEnabled: false
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
  return post.media.some((media) => media.type === "video" && !hasVideoMediaSource(media));
}

function hasVideoMediaSource(media: VideoPostMedia): boolean {
  return Boolean(media.url || media.variants?.some((url) => Boolean(normalizeVideoUrl(url))));
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
  const { content, relatedPost } = readPostContent(article);
  const time = article.querySelector<HTMLTimeElement>("time");
  const sourceUrl = readSourceUrl(time);
  const media = readPostMedia(article, observedVideoUrls);

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

function readPostContent(article: HTMLElement): { content: string; relatedPost?: RelatedPost } {
  const tweetTextBlocks = Array.from(article.querySelectorAll<HTMLElement>('[data-testid="tweetText"]'));
  const postBlocks = tweetTextBlocks
    .map((block) => ({
      block,
      content: normalizePostContent(block.innerText)
    }))
    .filter(({ content }) => Boolean(content));
  const text = postBlocks[0]?.content ?? "";

  if (text) {
    return {
      content: text,
      relatedPost: readRelatedPost(article, postBlocks.slice(1))
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

function readRelatedPost(article: HTMLElement, postBlocks: Array<{ block: HTMLElement; content: string }>): RelatedPost | undefined {
  const relatedBlock = postBlocks[0];

  if (!relatedBlock) {
    return undefined;
  }

  const relatedContainer = findRelatedPostContainer(article, relatedBlock.block);
  const authorBlock = relatedContainer?.querySelector<HTMLElement>('[data-testid="User-Name"]') ?? null;

  return {
    authorName: authorBlock ? readAuthorName(authorBlock) : undefined,
    authorHandle: authorBlock ? readAuthorHandle(authorBlock) : undefined,
    content: relatedBlock.content
  };
}

function findRelatedPostContainer(article: HTMLElement, block: HTMLElement): HTMLElement | null {
  let current = block.parentElement;

  while (current && current !== article) {
    if (current.querySelector('[data-testid="User-Name"]')) {
      return current;
    }

    current = current.parentElement;
  }

  return null;
}

function readSourceUrl(time: HTMLTimeElement | null): string | undefined {
  const link = time?.closest("a");
  const href = link?.getAttribute("href");

  if (!href) {
    return undefined;
  }

  return new URL(href, window.location.origin).toString();
}

function readPostMedia(article: HTMLElement, observedVideoUrls: string[]): PostMedia[] {
  const videos = readPostVideos(article, observedVideoUrls);
  const images = readPostImages(article);

  return [...videos, ...images];
}

function readPostVideos(article: HTMLElement, observedVideoUrls: string[]): VideoPostMedia[] {
  const videoKeys = new Set<string>();
  const videos = Array.from(article.querySelectorAll<HTMLVideoElement>("video"));
  const articleVideoUrls = readArticleAttachedVideoUrls(article);

  return videos
    .map((video): VideoPostMedia | null => {
      const posterUrl = normalizeImageUrl(video.poster) ?? findVideoPosterUrl(video);
      const videoUrls = readVideoUrls(video, posterUrl, observedVideoUrls, articleVideoUrls, videos.length === 1);
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

function readPostImages(article: HTMLElement): ImagePostMedia[] {
  const imageUrls = new Set<string>();

  return Array.from(article.querySelectorAll<HTMLImageElement>('img[src*="pbs.twimg.com/media/"]'))
    .map((image): ImagePostMedia | null => {
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
    .filter((media): media is ImagePostMedia => media !== null);
}

function readVideoUrls(
  video: HTMLVideoElement,
  posterUrl: string | undefined,
  observedVideoUrls: string[],
  articleVideoUrls: string[],
  allowArticleFallback: boolean
): string[] {
  rememberLocalObservedVideoUrls(observedVideoUrls);
  rememberLocalObservedVideoUrls(articleVideoUrls);

  const candidates = [
    video.currentSrc,
    video.src,
    ...Array.from(video.querySelectorAll<HTMLSourceElement>("source")).map((source) => source.src),
    ...readObservedVideoUrls(posterUrl, articleVideoUrls),
    ...readObservedVideoUrls(posterUrl, observedVideoUrls),
    ...readObservedVideoUrls(posterUrl, readLocalObservedVideoUrls()),
    ...(allowArticleFallback ? readScopedFallbackVideoUrls(articleVideoUrls) : []),
    ...(allowArticleFallback ? readScopedFallbackVideoUrls(observedVideoUrls) : []),
    ...(allowArticleFallback ? readScopedFallbackVideoUrls(readLocalObservedVideoUrls()) : [])
  ];

  return [...new Set(candidates.map(normalizeVideoUrl).filter((url): url is string => Boolean(url)))].sort((a, b) => scoreVideoUrl(b) - scoreVideoUrl(a));
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

function readScopedFallbackVideoUrls(videoUrls: string[]): string[] {
  const urls = videoUrls
    .map(normalizeVideoUrl)
    .filter((url): url is string => Boolean(url))
    .filter((url) => !isVideoSegmentUrl(url));

  return [...new Set(urls)].sort((a, b) => scoreVideoUrl(b) - scoreVideoUrl(a));
}

function readObservedVideoUrls(posterUrl: string | undefined, observedVideoUrls: string[]): string[] {
  const mediaId = extractTwitterVideoMediaId(posterUrl);

  if (!mediaId) {
    return [];
  }

  const urls = observedVideoUrls
    .map(normalizeVideoUrl)
    .filter((url): url is string => Boolean(url))
    .filter((url) => url.includes(`/${mediaId}/`))
    .filter((url) => !isVideoSegmentUrl(url));

  return [...new Set(urls)].sort((a, b) => scoreVideoUrl(b) - scoreVideoUrl(a));
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

function normalizeVideoUrl(value: string): string | null {
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

  return url.toString();
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

  if (!url.pathname.includes("/media/") && !url.pathname.includes("_thumb/")) {
    return null;
  }

  url.searchParams.set("name", "large");

  return url.toString();
}

function normalizeText(value: string | null | undefined): string {
  return value?.replace(/\s+\n/g, "\n").replace(/\n\s+/g, "\n").replace(/[ \t]+/g, " ").trim() ?? "";
}

function normalizePostContent(value: string | null | undefined): string {
  return (
    value
      ?.replace(/\r\n?/g, "\n")
      .replace(/\u00a0/g, " ")
      .split("\n")
      .map((line) => line.trim())
      .join("\n")
      .replace(/\n{3,}/g, "\n\n")
      .trim() ?? ""
  );
}
