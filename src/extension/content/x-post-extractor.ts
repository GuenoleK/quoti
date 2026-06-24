import type {
  ExtractedPost,
  ImagePostMedia,
  PostExtractionResult,
  PostMedia,
  RelatedPost,
  SocialPlatform,
  VideoPostMedia
} from "../../shared/types/post.types";

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

type PlatformConfig = {
  isX: boolean;
  label: string;
  platform: SocialPlatform;
};

type ImagePostMediaCandidate = {
  alt?: string;
  url: string;
};

const inlineButtonClassName = "quoti-inline-button";
const inlineButtonInsertedClassName = "quoti-inline-button-inserted";
const settingsStorageKey = "quoti-settings";
const inlineButtonPreferenceVersion = 1;
const maxLocalObservedVideoUrls = 120;
const postElementSelector =
  'article, [role="article"], .feed-shared-update-v2, [data-urn^="urn:li:activity"], [data-pagelet^="FeedUnit_"]';
const avatarImageSelector =
  '[data-testid="Tweet-User-Avatar"] img, [data-testid^="UserAvatar-Container"] img, [data-testid="UserAvatar-Container-unknown"] img, img[src*="/profile_images/"], img[srcset*="/profile_images/"]';
const avatarContainerSelector = '[data-testid="Tweet-User-Avatar"], [data-testid^="UserAvatar-Container"], [data-testid="UserAvatar-Container-unknown"]';
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
  return extractArticle(getCandidateArticle(), "Open a supported social post or hover a post before opening Quoti.", observedVideoUrls);
}

export function extractContextXPost(observedVideoUrls: string[] = []): PostExtractionResult {
  return extractArticle(
    contextArticle?.isConnected ? contextArticle : getCandidateArticle(),
    "Right-click a post before choosing Create Quoti card.",
    observedVideoUrls
  );
}

export function extractInlineXPost(postId: string, observedVideoUrls: string[] = []): PostExtractionResult {
  const article = document.querySelector<HTMLElement>(`[data-quoti-post-id="${postId}"]`);

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

  const article = findClosestPostElement(target);

  hoveredArticle = article;
}

function handleContextMenu(event: MouseEvent): void {
  const target = event.target;

  if (!(target instanceof HTMLElement)) {
    return;
  }

  const article = findClosestPostElement(target);

  contextArticle = article;
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

  if (!hasExtractedPostContent(post)) {
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
  const articles = getPostElements();

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
  const platform = getCurrentPlatform();

  if (platform.isX) {
    return Boolean(
      article.querySelector('[data-testid="tweetText"], time, img[src*="/media/"], img[src*="/card_img/"], img[srcset*="/media/"], img[srcset*="/card_img/"], video')
    );
  }

  return Boolean(readGenericPostContent(article, platform.platform) || readGenericPostMedia(article).length > 0);
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

  const sourceLink = findGenericSourceLink(article, getCurrentPlatform().platform);
  const sourceLinkContainer = sourceLink?.parentElement;

  if (sourceLinkContainer instanceof HTMLElement && sourceLink) {
    return {
      after: sourceLink,
      kind: "meta",
      target: sourceLinkContainer
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
    .sort((left, right) => left.text.length - right.text.length)[0]?.candidate ?? null;
}

function isViewsText(text: string): boolean {
  return text.length <= 80 && /\d[\d\s.,]*\s*[km]?\s*(?:vues?|views?)\b/i.test(text);
}

function getPostElements(): HTMLElement[] {
  return [...new Set(Array.from(document.querySelectorAll<HTMLElement>(postElementSelector)))];
}

function findClosestPostElement(element: HTMLElement): HTMLElement | null {
  const postElement = element.closest(postElementSelector);

  return postElement instanceof HTMLElement ? postElement : null;
}

function getCurrentPlatform(): PlatformConfig {
  const hostname = window.location.hostname.toLowerCase();

  if (hostname === "x.com" || hostname.endsWith(".x.com") || hostname === "twitter.com" || hostname.endsWith(".twitter.com")) {
    return {
      isX: true,
      label: "X",
      platform: "x"
    };
  }

  if (hostname === "threads.net" || hostname.endsWith(".threads.net") || hostname === "threads.com" || hostname.endsWith(".threads.com")) {
    return {
      isX: false,
      label: "Threads",
      platform: "threads"
    };
  }

  if (hostname === "linkedin.com" || hostname.endsWith(".linkedin.com")) {
    return {
      isX: false,
      label: "LinkedIn",
      platform: "linkedin"
    };
  }

  if (hostname === "facebook.com" || hostname.endsWith(".facebook.com") || hostname === "fb.watch") {
    return {
      isX: false,
      label: "Facebook",
      platform: "facebook"
    };
  }

  return {
    isX: true,
    label: "X",
    platform: "x"
  };
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
  const article = element ? findClosestPostElement(element) : null;

  return article;
}

function getMostVisibleArticle(): HTMLElement | null {
  const articles = getPostElements();
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
  const platform = getCurrentPlatform();

  if (!platform.isX) {
    return extractGenericPostFromElement(article, platform, observedVideoUrls);
  }

  return extractXPostFromArticle(article, observedVideoUrls);
}

function extractXPostFromArticle(article: HTMLElement, observedVideoUrls: string[]): ExtractedPost {
  const authorBlock = article.querySelector<HTMLElement>('[data-testid="User-Name"]');
  const authorName = readAuthorName(authorBlock);
  const authorHandle = readAuthorHandle(authorBlock);
  const { content, relatedContainer, relatedPost } = readPostContent(article, observedVideoUrls);
  const authorAvatarUrl = readAuthorAvatarUrl(article, authorBlock, authorHandle, relatedContainer);
  const time = article.querySelector<HTMLTimeElement>("time");
  const sourceUrl = readSourceUrl(time);
  const media = readPostMedia(article, observedVideoUrls, relatedContainer);

  return {
    id: sourceUrl ?? `${authorHandle}-${content.slice(0, 32)}`,
    platform: "x",
    authorName,
    authorHandle,
    authorAvatarUrl,
    content,
    relatedPost,
    publishedAt: time?.dateTime,
    sourceUrl,
    media,
    capturedAt: new Date().toISOString()
  };
}

function extractGenericPostFromElement(article: HTMLElement, platform: PlatformConfig, _observedVideoUrls: string[]): ExtractedPost {
  const sourceUrl = readGenericSourceUrl(article, platform.platform);
  const authorLink = readGenericAuthorLink(article, platform.platform, sourceUrl);
  const authorHandle = readGenericAuthorHandle(platform.platform, sourceUrl, authorLink);
  const authorName = readGenericAuthorName(article, platform, authorLink, authorHandle);
  const authorAvatarUrl = readGenericAuthorAvatarUrl(article, authorLink);
  const content = readGenericPostContent(article, platform.platform);
  const media = readGenericPostMedia(article, authorAvatarUrl);
  const publishedAt = readGenericPublishedAt(article);

  return {
    id: sourceUrl ?? `${platform.platform}-${authorHandle}-${content.slice(0, 32)}`,
    platform: platform.platform,
    authorName,
    authorHandle,
    authorAvatarUrl,
    content,
    publishedAt,
    sourceUrl,
    media,
    capturedAt: new Date().toISOString()
  };
}

function readGenericSourceUrl(article: HTMLElement, platform: SocialPlatform): string | undefined {
  const timeUrl = readSourceUrl(article.querySelector<HTMLTimeElement>("time"));

  if (timeUrl && isLikelyGenericSourceUrl(timeUrl, platform)) {
    return normalizeGenericUrl(timeUrl);
  }

  const sourceLink = findGenericSourceLink(article, platform);
  const sourceUrl = normalizeGenericUrl(sourceLink?.getAttribute("href"));

  if (sourceUrl) {
    return sourceUrl;
  }

  return isLikelyGenericSourceUrl(window.location.href, platform) ? normalizeGenericUrl(window.location.href) : undefined;
}

function findGenericSourceLink(article: HTMLElement, platform: SocialPlatform): HTMLAnchorElement | null {
  const candidates = queryGenericAll<HTMLAnchorElement>(article, getGenericSourceLinkSelectors(platform))
    .map((link) => ({
      link,
      url: normalizeGenericUrl(link.getAttribute("href"))
    }))
    .filter((candidate): candidate is { link: HTMLAnchorElement; url: string } => Boolean(candidate.url))
    .filter(({ url }) => isLikelyGenericSourceUrl(url, platform))
    .sort((left, right) => scoreGenericSourceUrl(right.url, platform) - scoreGenericSourceUrl(left.url, platform));

  return candidates[0]?.link ?? null;
}

function getGenericSourceLinkSelectors(platform: SocialPlatform): string[] {
  if (platform === "threads") {
    return ['a[href*="/post/"]'];
  }

  if (platform === "linkedin") {
    return ['a[href*="/feed/update/"]', 'a[href*="/posts/"]', 'a[href*="urn:li:activity"]', 'a[href*="/pulse/"]'];
  }

  if (platform === "facebook") {
    return [
      'a[href*="/posts/"]',
      'a[href*="/permalink.php"]',
      'a[href*="/story.php"]',
      'a[href*="story_fbid="]',
      'a[href*="/photo.php"]',
      'a[href*="/videos/"]',
      'a[href*="/watch/"]',
      'a[href*="/share/"]'
    ];
  }

  return [];
}

function scoreGenericSourceUrl(value: string, platform: SocialPlatform): number {
  try {
    const url = new URL(value);
    const path = url.pathname.toLowerCase();

    if (platform === "threads" && /\/@[^/]+\/post\//.test(path)) {
      return 100;
    }

    if (platform === "linkedin" && path.includes("/feed/update/")) {
      return 100;
    }

    if (platform === "facebook" && (path.includes("/posts/") || path.includes("/story.php") || url.searchParams.has("story_fbid"))) {
      return 100;
    }

    if (platform === "facebook" && url.hostname.toLowerCase() === "fb.watch") {
      return 100;
    }

    return 10;
  } catch {
    return 0;
  }
}

function isLikelyGenericSourceUrl(value: string, platform: SocialPlatform): boolean {
  try {
    const url = new URL(value, window.location.origin);
    const path = url.pathname.toLowerCase();

    if (platform === "threads") {
      return /\/@[^/]+\/post\//.test(path) || path.includes("/post/");
    }

    if (platform === "linkedin") {
      return path.includes("/feed/update/") || path.includes("/posts/") || path.includes("/pulse/") || value.includes("urn:li:activity");
    }

    if (platform === "facebook") {
      return (
        url.hostname.toLowerCase() === "fb.watch" ||
        path.includes("/posts/") ||
        path.includes("/permalink.php") ||
        path.includes("/story.php") ||
        path.includes("/photo.php") ||
        path.includes("/videos/") ||
        path.includes("/watch/") ||
        path.includes("/share/") ||
        url.searchParams.has("story_fbid")
      );
    }

    return false;
  } catch {
    return false;
  }
}

function readGenericAuthorLink(article: HTMLElement, platform: SocialPlatform, sourceUrl: string | undefined): HTMLAnchorElement | null {
  const sourceUrlNormalized = normalizeGenericUrl(sourceUrl);
  const candidates = queryGenericAll<HTMLAnchorElement>(article, getGenericAuthorLinkSelectors(platform))
    .map((link) => ({
      link,
      text: cleanGenericAuthorName(link.textContent),
      url: normalizeGenericUrl(link.getAttribute("href"))
    }))
    .filter(({ text, url }) => Boolean(text || url))
    .filter(({ url }) => !url || url !== sourceUrlNormalized)
    .filter(({ url }) => !url || !isLikelyGenericSourceUrl(url, platform))
    .filter(({ text }) => !text || !isGenericUiNoise(text));

  return candidates[0]?.link ?? null;
}

function getGenericAuthorLinkSelectors(platform: SocialPlatform): string[] {
  if (platform === "threads") {
    return ['a[href^="/@"]', 'a[href*="threads.com/@"]', 'a[href*="threads.net/@"]'];
  }

  if (platform === "linkedin") {
    return ['a[href*="/in/"]', 'a[href*="/company/"]', 'a[href*="/school/"]'];
  }

  if (platform === "facebook") {
    return [
      '[data-ad-rendering-role="profile_name"] a[href]',
      'h2 a[href]',
      'h3 a[href]',
      'strong a[href]',
      'a[role="link"][href*="facebook.com"]'
    ];
  }

  return [];
}

function readGenericAuthorHandle(platform: SocialPlatform, sourceUrl: string | undefined, authorLink: HTMLAnchorElement | null): string {
  return (
    resolveGenericHandleFromUrl(sourceUrl, platform) ??
    resolveGenericHandleFromUrl(authorLink?.getAttribute("href"), platform) ??
    getCurrentPlatform().label
  );
}

function readGenericAuthorName(
  article: HTMLElement,
  platform: PlatformConfig,
  authorLink: HTMLAnchorElement | null,
  authorHandle: string
): string {
  const selectorName = queryGenericAll<HTMLElement>(article, getGenericAuthorNameSelectors(platform.platform))
    .map((element) => cleanGenericAuthorName(element.textContent))
    .find((name): name is string => Boolean(name));

  const linkName = cleanGenericAuthorName(authorLink?.textContent);
  const handleName = authorHandle.startsWith("@") ? authorHandle.slice(1) : "";

  return selectorName ?? linkName ?? (handleName || `Shared ${platform.label} post`);
}

function getGenericAuthorNameSelectors(platform: SocialPlatform): string[] {
  if (platform === "threads") {
    return ['a[href^="/@"] span', 'a[href*="threads.com/@"] span', 'a[href*="threads.net/@"] span', 'a[href^="/@"]'];
  }

  if (platform === "linkedin") {
    return [
      '[data-test-id="actor-name"]',
      ".update-components-actor__name",
      ".feed-shared-actor__name",
      ".update-components-actor__title span[aria-hidden='true']",
      ".feed-shared-actor__title span[aria-hidden='true']"
    ];
  }

  if (platform === "facebook") {
    return [
      '[data-ad-rendering-role="profile_name"]',
      'h2 strong',
      'h3 strong',
      'strong a[role="link"]',
      'h2 a[role="link"]',
      'h3 a[role="link"]'
    ];
  }

  return [];
}

function readGenericAuthorAvatarUrl(article: HTMLElement, authorLink: HTMLAnchorElement | null): string | undefined {
  const authorImage =
    authorLink?.querySelector<HTMLImageElement>("img") ??
    authorLink?.closest<HTMLElement>("div, header")?.querySelector<HTMLImageElement>("img");
  const url = normalizeGenericImageUrl(authorImage ? readImageSourceUrl(authorImage) : undefined);

  if (url) {
    return url;
  }

  const firstSmallImage = Array.from(article.querySelectorAll<HTMLImageElement>("img"))
    .filter((image) => {
      const rect = image.getBoundingClientRect();

      return rect.width > 0 && rect.height > 0 && Math.max(rect.width, rect.height) <= 96;
    })
    .map((image) => normalizeGenericImageUrl(readImageSourceUrl(image)))
    .find((candidate): candidate is string => Boolean(candidate));

  return firstSmallImage;
}

function readGenericPostContent(article: HTMLElement, platform: SocialPlatform): string {
  const selectorCandidates = queryGenericAll<HTMLElement>(article, getGenericContentSelectors(platform))
    .map((element) => cleanGenericPostContent(readElementText(element)))
    .filter((content) => isGenericPostContentText(content));
  const fallbackCandidates = queryGenericAll<HTMLElement>(article, ["p", '[dir="auto"]', ".break-words"])
    .filter(isGenericTextCandidateElement)
    .map((element) => cleanGenericPostContent(readElementText(element)))
    .filter((content) => isGenericPostContentText(content));

  return [...uniqueStrings(selectorCandidates), ...uniqueStrings(fallbackCandidates)]
    .sort((left, right) => scoreGenericPostContent(right) - scoreGenericPostContent(left))[0] ?? "";
}

function getGenericContentSelectors(platform: SocialPlatform): string[] {
  if (platform === "threads") {
    return ['div[dir="auto"]', 'span[dir="auto"]'];
  }

  if (platform === "linkedin") {
    return [
      ".update-components-text",
      ".feed-shared-update-v2__description",
      ".feed-shared-inline-show-more-text",
      ".break-words"
    ];
  }

  if (platform === "facebook") {
    return [
      '[data-ad-comet-preview="message"]',
      '[data-ad-preview="message"]',
      '[data-ad-rendering-role="story_message"]',
      'div[dir="auto"]'
    ];
  }

  return ["p", '[dir="auto"]'];
}

function readGenericPostMedia(article: HTMLElement, authorAvatarUrl?: string): ImagePostMedia[] {
  const imageUrls = new Set<string>();
  const normalizedAvatarUrl = normalizeGenericImageUrl(authorAvatarUrl);

  return Array.from(article.querySelectorAll<HTMLImageElement>("img"))
    .map((image): ImagePostMedia | null => {
      const url = normalizeGenericImageUrl(readImageSourceUrl(image));

      if (!url || url === normalizedAvatarUrl || imageUrls.has(url) || !isLikelyGenericPostImage(image)) {
        return null;
      }

      imageUrls.add(url);

      return {
        type: "image",
        url,
        alt: normalizeText(image.alt)
      };
    })
    .filter((media): media is ImagePostMedia => media !== null);
}

function readGenericPublishedAt(article: HTMLElement): string | undefined {
  const dateTime = article.querySelector<HTMLTimeElement>("time")?.dateTime;

  if (!dateTime) {
    return undefined;
  }

  const time = Date.parse(dateTime);

  return Number.isFinite(time) ? new Date(time).toISOString() : dateTime;
}

function queryGenericAll<T extends Element>(root: ParentNode, selectors: string[]): T[] {
  return selectors.flatMap((selector) => {
    try {
      return Array.from(root.querySelectorAll<T>(selector));
    } catch {
      return [];
    }
  });
}

function readElementText(element: HTMLElement): string {
  const parts: string[] = [];
  const visit = (node: ChildNode): void => {
    if (node.nodeType === Node.TEXT_NODE) {
      parts.push(node.textContent ?? "");
      return;
    }

    if (!(node instanceof HTMLElement) || isHiddenElement(node) || ["BUTTON", "SCRIPT", "STYLE", "SVG"].includes(node.tagName)) {
      return;
    }

    if (node.tagName === "BR") {
      parts.push("\n");
      return;
    }

    if (node instanceof HTMLImageElement && node.alt && node.alt.length <= 8) {
      parts.push(node.alt);
      return;
    }

    node.childNodes.forEach(visit);
  };

  element.childNodes.forEach(visit);

  return normalizePostContent(parts.join(""));
}

function isHiddenElement(element: HTMLElement): boolean {
  return element.hidden || element.getAttribute("aria-hidden") === "true";
}

function isGenericTextCandidateElement(element: HTMLElement): boolean {
  if (element.closest(`.${inlineButtonClassName}`) || ["A", "BUTTON"].includes(element.tagName)) {
    return false;
  }

  const text = cleanGenericPostContent(readElementText(element));

  if (!isGenericPostContentText(text)) {
    return false;
  }

  const childTextElements = Array.from(element.children).filter((child): child is HTMLElement => {
    if (!(child instanceof HTMLElement)) {
      return false;
    }

    return isGenericPostContentText(cleanGenericPostContent(readElementText(child)));
  });

  return childTextElements.length <= 2;
}

function cleanGenericPostContent(value: string | null | undefined): string {
  return normalizePostContent(value)
    .split("\n")
    .map((line) => line.trim())
    .filter((line) => Boolean(line) && !isGenericUiNoise(line))
    .join("\n")
    .replace(/\n{3,}/g, "\n\n")
    .trim();
}

function cleanGenericAuthorName(value: string | null | undefined): string | undefined {
  const name = normalizeText(value)
    .split(/\n|\u00b7|\u2022/)
    .map((part) => part.trim())
    .find((part) => Boolean(part) && !part.startsWith("@") && !isGenericUiNoise(part));

  return name && name.length <= 120 ? name : undefined;
}

function isGenericPostContentText(value: string): boolean {
  const compact = compactText(value);

  if (compact.length < 3 || compact.length > 2800) {
    return false;
  }

  if (/^https?:\/\/\S+$/i.test(compact)) {
    return false;
  }

  return !isGenericUiNoise(compact);
}

function isGenericUiNoise(value: string): boolean {
  const compact = compactText(value).replace(/[.,:;]+$/, "").toLowerCase();

  return (
    compact.length <= 80 &&
    /^(like|comment|share|send|follow|following|followers?|repost|quote|reply|replies|views?|view|see more|see translation|open profile|j'aime|commenter|partager|envoyer|suivre|abonnes?|voir plus|voir la traduction|\d+\s+(comments?|replies|shares?|reposts?|likes?|views?))$/.test(compact)
  );
}

function scoreGenericPostContent(value: string): number {
  const compact = compactText(value);
  const lineCount = value.split("\n").filter(Boolean).length;

  return compact.length + Math.min(lineCount, 6) * 12;
}

function uniqueStrings(values: string[]): string[] {
  return [...new Set(values)];
}

function isLikelyGenericPostImage(image: HTMLImageElement): boolean {
  if (image.closest(`.${inlineButtonClassName}`)) {
    return false;
  }

  const rect = image.getBoundingClientRect();

  if (rect.width === 0 || rect.height === 0 || Math.max(rect.width, rect.height) < 96) {
    return false;
  }

  const url = normalizeGenericImageUrl(readImageSourceUrl(image));

  if (!url) {
    return false;
  }

  try {
    const parsedUrl = new URL(url);
    const path = parsedUrl.pathname.toLowerCase();

    if (path.includes("/emoji/") || path.includes("/profile_images/") || path.includes("profile-displayphoto")) {
      return false;
    }
  } catch {
    return false;
  }

  return true;
}

function normalizeGenericImageUrl(value: string | undefined): string | null {
  if (!value || value.startsWith("blob:") || value.startsWith("data:")) {
    return null;
  }

  try {
    const url = new URL(value, window.location.origin);

    if (!["http:", "https:"].includes(url.protocol)) {
      return null;
    }

    return url.toString();
  } catch {
    return null;
  }
}

function normalizeGenericUrl(value: string | null | undefined): string | undefined {
  if (!value || value.startsWith("#")) {
    return undefined;
  }

  try {
    const url = new URL(value, window.location.origin);

    if (!["http:", "https:"].includes(url.protocol)) {
      return undefined;
    }

    url.hash = "";

    return url.toString();
  } catch {
    return undefined;
  }
}

function resolveGenericHandleFromUrl(value: string | null | undefined, platform: SocialPlatform): string | undefined {
  const normalizedUrl = normalizeGenericUrl(value);

  if (!normalizedUrl) {
    return undefined;
  }

  try {
    const url = new URL(normalizedUrl);
    const segments = url.pathname.split("/").filter(Boolean);

    if (platform === "threads") {
      const handle = segments.find((segment) => segment.startsWith("@")) ?? segments[0];

      return handle ? `@${handle.replace(/^@/, "")}` : undefined;
    }

    if (platform === "linkedin") {
      const scope = segments[0]?.toLowerCase();
      const slug = segments[1];

      if (slug && ["in", "company", "school"].includes(scope)) {
        return `@${slug}`;
      }
    }

    if (platform === "facebook") {
      const slug = segments[0];

      if (slug && !isReservedFacebookPathSegment(slug)) {
        return `@${slug}`;
      }
    }
  } catch {
    return undefined;
  }

  return undefined;
}

function isReservedFacebookPathSegment(value: string): boolean {
  return ["permalink.php", "story.php", "photo.php", "watch", "groups", "share", "reel", "events", "pages", "profile.php"].includes(
    value.toLowerCase()
  );
}

function hasExtractedPostContent(post: ExtractedPost): boolean {
  return Boolean(
    post.content.trim() ||
      post.media.length > 0 ||
      post.relatedPost?.content.trim() ||
      (post.relatedPost?.media?.length ?? 0) > 0
  );
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

function readAuthorAvatarUrl(
  root: HTMLElement,
  authorBlock: HTMLElement | null,
  authorHandle?: string,
  excludedContainer?: HTMLElement | null
): string | undefined {
  if (!authorBlock) {
    return undefined;
  }

  const candidates = readAuthorAvatarCandidates(root, authorBlock, authorHandle, excludedContainer);

  if (candidates.length === 0 && authorHandle) {
    candidates.push(...readAuthorAvatarCandidates(document.body, authorBlock, authorHandle, excludedContainer, true));
  }

  return candidates[0]?.url;
}

function readAuthorAvatarCandidates(
  root: HTMLElement,
  authorBlock: HTMLElement,
  authorHandle?: string,
  excludedContainer?: HTMLElement | null,
  requireHandleMatch = false
): Array<{ image: HTMLImageElement; score: number; url: string }> {
  const expectedHandle = normalizeHandle(authorHandle);

  return Array.from(root.querySelectorAll<HTMLImageElement>(avatarImageSelector))
    .filter((image) => !isInsideExcludedContainer(image, excludedContainer))
    .filter((image) => !requireHandleMatch || readAvatarLinkHandle(image) === expectedHandle)
    .map((image) => ({
      image,
      score: scoreAuthorAvatarCandidate(image, authorBlock, authorHandle),
      url: normalizeAuthorAvatarUrl(readImageSourceUrl(image))
    }))
    .filter((candidate): candidate is { image: HTMLImageElement; score: number; url: string } => Boolean(candidate.url))
    .sort((left, right) => right.score - left.score);
}

function scoreAuthorAvatarCandidate(image: HTMLImageElement, authorBlock: HTMLElement, authorHandle?: string): number {
  const expectedHandle = normalizeHandle(authorHandle);
  const imageHandle = readAvatarLinkHandle(image);
  const imageRect = image.getBoundingClientRect();
  const authorRect = authorBlock.getBoundingClientRect();
  let score = 0;

  if (image.closest(avatarContainerSelector)) {
    score += 100;
  }

  if (expectedHandle && imageHandle === expectedHandle) {
    score += 1000;
  } else if (expectedHandle && imageHandle) {
    score -= 300;
  }

  if (imageRect.width > 0 && imageRect.height > 0 && authorRect.width > 0 && authorRect.height > 0) {
    const imageCenterY = imageRect.top + imageRect.height / 2;
    const authorCenterY = authorRect.top + authorRect.height / 2;

    score -= Math.min(500, Math.abs(imageCenterY - authorCenterY));

    if (imageRect.right <= authorRect.left + 16) {
      score += 40;
    }
  }

  return score;
}

function readAvatarLinkHandle(image: HTMLImageElement): string | undefined {
  const href = image.closest<HTMLAnchorElement>("a[href]")?.getAttribute("href");

  if (!href) {
    return undefined;
  }

  try {
    const url = new URL(href, window.location.origin);
    const handle = url.pathname.split("/").filter(Boolean)[0];

    if (!handle || ["home", "i", "intent", "search", "settings"].includes(handle)) {
      return undefined;
    }

    return normalizeHandle(handle);
  } catch {
    return undefined;
  }
}

function normalizeHandle(handle: string | undefined): string | undefined {
  const normalized = handle?.replace(/^@/, "").trim().toLowerCase();

  return normalized || undefined;
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

  return {
    content: ""
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
  const authorName = authorBlock ? readAuthorName(authorBlock) : undefined;
  const authorHandle = authorBlock ? readAuthorHandle(authorBlock) : undefined;
  const media = relatedContainer ? readPostMedia(relatedContainer, observedVideoUrls) : [];
  const content = readBestRelatedPostContent(relatedBlock.content, relatedContainer, article);
  const sourceUrl = relatedContainer ? readRelatedPostSourceUrl(relatedContainer) : undefined;

  return {
    container: relatedContainer,
    post: {
      authorName,
      authorHandle,
      authorAvatarUrl: relatedContainer ? readAuthorAvatarUrl(relatedContainer, authorBlock, authorHandle) : undefined,
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

  return readPostImageCandidates(root, excludedContainer)
    .map((candidate): ImagePostMedia | null => {
      if (imageUrls.has(candidate.url)) {
        return null;
      }

      imageUrls.add(candidate.url);

      return {
        type: "image" as const,
        url: candidate.url,
        alt: candidate.alt
      };
    })
    .filter((media): media is ImagePostMedia => media !== null);
}

function readPostImageCandidates(root: HTMLElement, excludedContainer?: HTMLElement | null): ImagePostMediaCandidate[] {
  return [
    ...readPostDomImageCandidates(root, excludedContainer),
    ...readPostBackgroundImageCandidates(root, excludedContainer),
    ...readPostReactImageCandidates(root, excludedContainer)
  ];
}

function readPostDomImageCandidates(root: HTMLElement, excludedContainer?: HTMLElement | null): ImagePostMediaCandidate[] {
  return Array.from(root.querySelectorAll<HTMLImageElement>("img"))
    .filter((image) => !isInsideExcludedContainer(image, excludedContainer))
    .map((image): ImagePostMediaCandidate | null => {
      const normalizedUrl = normalizePostImageUrl(readImageSourceUrl(image));

      if (!normalizedUrl || !isLikelyPostImage(image)) {
        return null;
      }

      return {
        url: normalizedUrl,
        alt: normalizeText(image.alt)
      };
    })
    .filter((candidate): candidate is ImagePostMediaCandidate => candidate !== null);
}

function readPostBackgroundImageCandidates(root: HTMLElement, excludedContainer?: HTMLElement | null): ImagePostMediaCandidate[] {
  return getScopedElementCandidates(root, excludedContainer)
    .filter(isLikelyPostImageElement)
    .flatMap((element) => extractTwitterPostImageUrls(readElementBackgroundImageValue(element)))
    .map((url) => normalizePostImageUrl(url))
    .filter((url): url is string => Boolean(url))
    .map((url) => ({ url }));
}

function readPostReactImageCandidates(root: HTMLElement, excludedContainer?: HTMLElement | null): ImagePostMediaCandidate[] {
  const urls = new Set<string>();
  const seen = new WeakSet<object>();
  const nodes = getScopedElementCandidates(root, excludedContainer).filter((element) => !excludedContainer || !element.contains(excludedContainer));

  nodes.forEach((node) => {
    Object.keys(node)
      .filter((key) => key.startsWith("__reactProps$") || key.startsWith("__reactFiber$"))
      .forEach((key) => collectTwitterPostImageUrls((node as unknown as Record<string, unknown>)[key], urls, seen, 0));
  });

  return [...urls].map((url) => ({ url }));
}

function getScopedElementCandidates(root: HTMLElement, excludedContainer?: HTMLElement | null): HTMLElement[] {
  return [root, ...Array.from(root.querySelectorAll<HTMLElement>("*"))]
    .filter((element) => !isInsideExcludedContainer(element, excludedContainer))
    .slice(0, 260);
}

function isLikelyPostImageElement(element: HTMLElement): boolean {
  if (element.closest(avatarContainerSelector)) {
    return false;
  }

  const rect = element.getBoundingClientRect();

  if (rect.width > 0 && rect.height > 0 && Math.max(rect.width, rect.height) < 96) {
    return false;
  }

  return true;
}

function readElementBackgroundImageValue(element: HTMLElement): string {
  const values = [element.style.backgroundImage, element.getAttribute("style") ?? ""];

  try {
    values.push(window.getComputedStyle(element).backgroundImage);
  } catch {
    // Inline style values still cover the common card image case.
  }

  return values.join(" ");
}

function collectTwitterPostImageUrls(value: unknown, urls: Set<string>, seen: WeakSet<object>, depth: number): void {
  if (depth > 10 || urls.size > 80 || value === null || value === undefined) {
    return;
  }

  if (typeof value === "string") {
    extractTwitterPostImageUrls(value)
      .map((url) => normalizePostImageUrl(url))
      .filter((url): url is string => Boolean(url))
      .forEach((url) => urls.add(url));
    return;
  }

  if (typeof value !== "object" || seen.has(value)) {
    return;
  }

  seen.add(value);

  if (value instanceof Node || value instanceof Window) {
    return;
  }

  if (Array.isArray(value)) {
    value.slice(0, 80).forEach((item) => collectTwitterPostImageUrls(item, urls, seen, depth + 1));
    return;
  }

  Object.entries(value as Record<string, unknown>)
    .slice(0, 160)
    .forEach(([, item]) => collectTwitterPostImageUrls(item, urls, seen, depth + 1));
}

function extractTwitterPostImageUrls(value: string): string[] {
  const normalizedValue = value.replace(/\\\//g, "/").replace(/\\u002f/gi, "/").replace(/\\u0026/g, "&").replace(/&amp;/g, "&");
  const matches = normalizedValue.match(/https?:\/\/pbs\.twimg\.com\/(?:media|card_img)\/[^"'\s\\<>),\]}]+/g) ?? [];

  return matches.map((url) => url.replace(/&amp;/g, "&"));
}

function isLikelyPostImage(image: HTMLImageElement): boolean {
  if (image.closest(avatarContainerSelector)) {
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

  if (isLikelyAudioOnlySourceUrl(value)) {
    return -1;
  }

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

function normalizeAuthorAvatarUrl(value: string | undefined): string | null {
  if (!value) {
    return null;
  }

  let url: URL;

  try {
    url = new URL(value);
  } catch {
    return null;
  }

  if (!url.hostname.endsWith("twimg.com") || !url.pathname.includes("/profile_images/")) {
    return null;
  }

  return url.toString();
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
