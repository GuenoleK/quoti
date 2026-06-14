import type { QuotiMessageResponse } from "../../shared/types/extension-message.types";
import type { ExtractedPost, PostMedia, VideoPostMedia } from "../../shared/types/post.types";
import { latestPostStorageKey, readQuotiSettings } from "../../shared/settings/quoti-settings";

const contextMenuId = "quoti-create-card";
const observedVideoUrlsStorageKey = "quoti-observed-video-urls";
const hydratedRelatedPostsStorageKey = "quoti-hydrated-related-posts";
const maxCachedVideoUrlsPerTab = 80;
const maxHydratedRelatedPosts = 40;
const hydratedRelatedPostsByUrl = new Map<string, ExtractedPost>();
const observedVideoUrlsByTabId = new Map<number, string[]>();

chrome.webRequest.onBeforeRequest.addListener(
  (details) => {
    if (details.tabId < 0 || !isTwitterVideoUrl(details.url)) {
      return undefined;
    }

    void rememberObservedVideoUrl(details.tabId, details.url);
    return undefined;
  },
  {
    urls: ["https://video.twimg.com/*"],
    types: ["media", "xmlhttprequest", "other"]
  }
);

chrome.tabs.onRemoved.addListener((tabId) => {
  observedVideoUrlsByTabId.delete(tabId);
  void removeStoredObservedVideoUrls(tabId);
});

chrome.tabs.onUpdated.addListener((tabId, changeInfo) => {
  if (changeInfo.status === "loading") {
    observedVideoUrlsByTabId.delete(tabId);
    void removeStoredObservedVideoUrls(tabId);
  }
});

chrome.runtime.onInstalled.addListener((details) => {
  chrome.action.setBadgeText({ text: "" });
  void syncContextMenu();

  if (details.reason === "install") {
    void chrome.runtime.openOptionsPage();
  }
});

chrome.runtime.onStartup.addListener(() => {
  void syncContextMenu();
});

chrome.runtime.onMessage.addListener((message, _sender, sendResponse) => {
  if (message.type === "QUOTI_SETTINGS_UPDATED") {
    void syncContextMenu();
  }

  if (message.type === "QUOTI_OPEN_POPUP") {
    void openQuotiSurface().finally(() => sendResponse({ status: "ready" }));
    return true;
  }

  if (message.type === "QUOTI_READ_OBSERVED_VIDEO_URLS") {
    void readObservedVideoUrlsFromSender(message.tabId ?? _sender.tab?.id).then((observedVideoUrls) =>
      sendResponse({ status: "video-urls", observedVideoUrls })
    );
    return true;
  }

  if (message.type === "QUOTI_HYDRATE_RELATED_POST") {
    void hydrateRelatedPost(message.post)
      .then((post) => sendResponse({ status: "success", post }))
      .catch(() => sendResponse({ status: "success", post: message.post }));
    return true;
  }

  if (message.type === "QUOTI_INLINE_POST_CAPTURED") {
    void handleInlinePostCaptured(message.post).finally(() => sendResponse({ status: "ready" }));
    return true;
  }

  return false;
});

chrome.contextMenus.onClicked.addListener((info, tab) => {
  if (info.menuItemId !== contextMenuId || !tab?.id) {
    return;
  }

  void captureContextPost(tab.id);
});

async function syncContextMenu(): Promise<void> {
  const settings = await readQuotiSettings();

  await chrome.contextMenus.removeAll();

  if (!settings.contextMenuEnabled) {
    return;
  }

  chrome.contextMenus.create({
    id: contextMenuId,
    title: "Create Quoti card",
    contexts: ["page", "selection", "link", "image", "video"],
    documentUrlPatterns: ["https://x.com/*", "https://twitter.com/*"]
  });
}

async function captureContextPost(tabId: number): Promise<void> {
  try {
    await ensureContentScript(tabId);
    const response = await requestContextPost(tabId);

    if (response.status === "success") {
      await chrome.storage.session.set({
        [latestPostStorageKey]: response.post
      });
      await openQuotiSurface();
    }
  } catch {
    await openQuotiSurface();
  }
}

async function requestContextPost(tabId: number): Promise<QuotiMessageResponse> {
  let latestResponse: QuotiMessageResponse | null = null;

  for (const delay of [0, 650, 1250]) {
    if (delay > 0) {
      await wait(delay);
    }

    const response = (await chrome.tabs.sendMessage(tabId, {
      type: "QUOTI_GET_CONTEXT_POST",
      observedVideoUrls: await readObservedVideoUrls(tabId)
    })) as QuotiMessageResponse;

    latestResponse = response;

    if (response.status !== "success" || !hasMissingVideoUrl(response.post)) {
      return response;
    }
  }

  return latestResponse ?? { status: "empty", reason: "Quoti could not read this post." };
}

function hasMissingVideoUrl(post: ExtractedPost): boolean {
  return getAllPostMedia(post).some((media) => media.type === "video" && !hasVideoMediaSource(media));
}

function hasVideoMediaSource(media: VideoPostMedia): boolean {
  return filterVideoSourceUrlsForMedia(media.posterUrl, [media.url, ...(media.variants ?? [])]).length > 0;
}

function getAllPostMedia(post: ExtractedPost): PostMedia[] {
  return [...post.media, ...(post.relatedPost?.media ?? [])];
}

function normalizeVideoSourceUrl(value: string | undefined): string | undefined {
  if (!value || value.startsWith("blob:") || value.startsWith("data:")) {
    return undefined;
  }

  try {
    const url = new URL(value.replace(/\\u0026/g, "&").replace(/&amp;/g, "&"));

    if (!["http:", "https:"].includes(url.protocol) || !url.hostname.endsWith("twimg.com") || isLikelyAudioOnlySourceUrl(url.toString())) {
      return undefined;
    }

    return url.toString();
  } catch {
    return undefined;
  }
}

function filterVideoSourceUrlsForMedia(posterUrl: string | undefined, sourceUrls: Array<string | undefined>): string[] {
  const mediaId = extractTwitterVideoMediaId(posterUrl);
  const urls = sourceUrls.map(normalizeVideoSourceUrl).filter((url): url is string => Boolean(url));

  if (!mediaId) {
    return [...new Set(urls)];
  }

  return [...new Set(urls.filter((url) => extractTwitterVideoSourceId(url) === mediaId))];
}

function extractTwitterVideoMediaId(value: string | undefined): string | undefined {
  if (!value) {
    return undefined;
  }

  try {
    const pathname = new URL(value).pathname;

    return /\/(?:ext_tw_video_thumb|amplify_video_thumb|tweet_video_thumb)\/([^/]+)\//.exec(pathname)?.[1];
  } catch {
    return undefined;
  }
}

function extractTwitterVideoSourceId(value: string): string | undefined {
  try {
    const pathname = new URL(value).pathname;

    return /\/(?:ext_tw_video|amplify_video|tweet_video)\/([^/]+)\//.exec(pathname)?.[1];
  } catch {
    return undefined;
  }
}

function isLikelyAudioOnlySourceUrl(value: string): boolean {
  try {
    const pathname = new URL(value).pathname.toLowerCase();

    return /(?:^|\/)(?:audio|aud|mp4a|aac)(?:[./_-]|\/|$)/.test(pathname);
  } catch {
    return false;
  }
}

async function handleInlinePostCaptured(post: unknown): Promise<void> {
  if (isExtractedPost(post)) {
    await chrome.storage.session.set({
      [latestPostStorageKey]: post
    });
  }

  await openQuotiSurface();
}

async function hydrateRelatedPost(post: ExtractedPost): Promise<ExtractedPost> {
  const relatedPost = post.relatedPost;
  const relatedSourceUrl = relatedPost?.sourceUrl;

  if (!relatedSourceUrl || !isSupportedXUrl(relatedSourceUrl) || !shouldHydrateRelatedPost(relatedPost.content)) {
    return post;
  }

  const cachedPost = await readHydratedRelatedPost(relatedSourceUrl);

  if (cachedPost) {
    return mergeHydratedRelatedPost(post, relatedPost, relatedSourceUrl, cachedPost);
  }

  const tab = await chrome.tabs.create({
    active: false,
    url: relatedSourceUrl
  });

  if (!tab.id) {
    return post;
  }

  try {
    await waitForTabReady(tab.id);
    await ensureContentScript(tab.id);

    const extractedPost = await extractPostFromHydrationTab(tab.id);

    if (!extractedPost) {
      return post;
    }

    await writeHydratedRelatedPost(relatedSourceUrl, extractedPost);

    return mergeHydratedRelatedPost(post, relatedPost, relatedSourceUrl, extractedPost);
  } finally {
    await chrome.tabs.remove(tab.id).catch(() => undefined);
  }
}

function mergeHydratedRelatedPost(
  post: ExtractedPost,
  relatedPost: NonNullable<ExtractedPost["relatedPost"]>,
  relatedSourceUrl: string,
  extractedPost: ExtractedPost
): ExtractedPost {
  return {
    ...post,
    relatedPost: {
      ...relatedPost,
      authorHandle: extractedPost.authorHandle || relatedPost.authorHandle,
      authorName: extractedPost.authorName || relatedPost.authorName,
      content: extractedPost.content || relatedPost.content,
      media: extractedPost.media.length > 0 ? extractedPost.media : relatedPost.media,
      sourceUrl: extractedPost.sourceUrl ?? relatedSourceUrl
    }
  };
}

function shouldHydrateRelatedPost(content: string): boolean {
  const trimmedContent = content.trim();

  if (!trimmedContent) {
    return false;
  }

  if (/[.\u2026]\s*$/.test(trimmedContent)) {
    return true;
  }

  return /[A-Za-z\u00c0-\u00d6\u00d8-\u00f6\u00f8-\u00ff0-9]$/.test(trimmedContent);
}

async function readHydratedRelatedPost(sourceUrl: string): Promise<ExtractedPost | null> {
  const memoryPost = hydratedRelatedPostsByUrl.get(sourceUrl);

  if (memoryPost) {
    return memoryPost;
  }

  const stored = await chrome.storage.session.get(hydratedRelatedPostsStorageKey);
  const cache = stored[hydratedRelatedPostsStorageKey] as Record<string, ExtractedPost> | undefined;
  const cachedPost = cache?.[sourceUrl];

  if (cachedPost && isExtractedPost(cachedPost)) {
    hydratedRelatedPostsByUrl.set(sourceUrl, cachedPost);
    return cachedPost;
  }

  return null;
}

async function writeHydratedRelatedPost(sourceUrl: string, post: ExtractedPost): Promise<void> {
  hydratedRelatedPostsByUrl.set(sourceUrl, post);

  const memoryEntries = [...hydratedRelatedPostsByUrl.entries()].slice(-maxHydratedRelatedPosts);
  hydratedRelatedPostsByUrl.clear();
  memoryEntries.forEach(([url, cachedPost]) => hydratedRelatedPostsByUrl.set(url, cachedPost));

  const stored = await chrome.storage.session.get(hydratedRelatedPostsStorageKey);
  const cache = (stored[hydratedRelatedPostsStorageKey] as Record<string, ExtractedPost> | undefined) ?? {};
  const nextCacheEntries = [...Object.entries(cache), [sourceUrl, post]].slice(-maxHydratedRelatedPosts);

  await chrome.storage.session.set({
    [hydratedRelatedPostsStorageKey]: Object.fromEntries(nextCacheEntries)
  });
}

async function extractPostFromHydrationTab(tabId: number): Promise<ExtractedPost | null> {
  let latestResponse: QuotiMessageResponse | null = null;

  for (const delay of [0, 850, 1600, 2600]) {
    if (delay > 0) {
      await wait(delay);
    }

    const response = (await chrome.tabs.sendMessage(tabId, {
      type: "QUOTI_GET_SELECTED_POST",
      observedVideoUrls: await readObservedVideoUrls(tabId)
    })) as QuotiMessageResponse;

    latestResponse = response;

    if (response.status === "success" && response.post.content) {
      return response.post;
    }
  }

  return latestResponse?.status === "success" ? latestResponse.post : null;
}

function waitForTabReady(tabId: number): Promise<void> {
  return new Promise((resolve) => {
    const timeout = setTimeout(() => {
      chrome.tabs.onUpdated.removeListener(handleUpdated);
      resolve();
    }, 8000);

    const handleUpdated = (updatedTabId: number, changeInfo: chrome.tabs.TabChangeInfo) => {
      if (updatedTabId !== tabId || changeInfo.status !== "complete") {
        return;
      }

      clearTimeout(timeout);
      chrome.tabs.onUpdated.removeListener(handleUpdated);
      resolve();
    };

    chrome.tabs.onUpdated.addListener(handleUpdated);
    void chrome.tabs.get(tabId).then((tab) => {
      if (tab.status === "complete") {
        clearTimeout(timeout);
        chrome.tabs.onUpdated.removeListener(handleUpdated);
        resolve();
      }
    });
  });
}

function isSupportedXUrl(url: string): boolean {
  try {
    const parsedUrl = new URL(url);

    return ["x.com", "twitter.com"].includes(parsedUrl.hostname) && /\/status\/\d+/.test(parsedUrl.pathname);
  } catch {
    return false;
  }
}

function isExtractedPost(value: unknown): value is ExtractedPost {
  return typeof value === "object" && value !== null && "content" in value && "authorName" in value;
}

async function openQuotiSurface(): Promise<void> {
  try {
    await chrome.action.openPopup();
  } catch {
    // Chrome can reject programmatic popup opening in some contexts.
    // The post remains stored for the next manual extension open.
  }
}

async function ensureContentScript(tabId: number): Promise<void> {
  try {
    await chrome.tabs.sendMessage(tabId, { type: "QUOTI_PING" });
    return;
  } catch {
    await chrome.scripting.insertCSS({
      target: { tabId },
      files: ["assets/content-script.css"]
    });
    await chrome.scripting.executeScript({
      target: { tabId },
      files: ["content-script.js"]
    });
  }
}

async function readObservedVideoUrls(tabId: number): Promise<string[]> {
  const cachedVideoUrls = await readCachedObservedVideoUrls(tabId);

  try {
    const [result] = await chrome.scripting.executeScript({
      target: { tabId },
      world: "MAIN",
      func: readMainWorldVideoUrls
    });

    const pageVideoUrls = Array.isArray(result?.result) ? result.result.filter((url): url is string => typeof url === "string") : [];
    const urls = [...new Set([...cachedVideoUrls, ...pageVideoUrls])].slice(-maxCachedVideoUrlsPerTab);

    if (urls.length > cachedVideoUrls.length) {
      observedVideoUrlsByTabId.set(tabId, urls);
      await writeStoredObservedVideoUrls(tabId, urls);
    }

    return urls;
  } catch {
    return cachedVideoUrls;
  }
}

function readMainWorldVideoUrls(): string[] {
  const urls = new Set<string>();
  const seen = new WeakSet<object>();

  const isVideoSegmentUrl = (value: string): boolean => {
    try {
      const pathname = new URL(value).pathname.toLowerCase();

      return (
        pathname.endsWith(".m4s") ||
        pathname.endsWith(".ts") ||
        pathname.includes("/seg/") ||
        /\/vid\/avc1\/0\/0\/\d{2,5}x\d{2,5}\//.test(pathname) ||
        (pathname.endsWith(".mp4") && pathname.includes("/pl/") && /(?:^|\/)(init|map)\.mp4$/.test(pathname))
      );
    } catch {
      return true;
    }
  };

  const normalizeVideoUrl = (value: string): string | null => {
    if (!value || value.startsWith("blob:") || value.startsWith("data:")) {
      return null;
    }

    try {
      const url = new URL(value.replace(/\\u0026/g, "&").replace(/&amp;/g, "&"));

      if (!["http:", "https:"].includes(url.protocol) || url.hostname !== "video.twimg.com" || isVideoSegmentUrl(url.toString())) {
        return null;
      }

      return url.toString();
    } catch {
      return null;
    }
  };

  const collectFromString = (value: string): void => {
    const matches = value.match(/https?:\/\/video\.twimg\.com\/[^"'\s\\<>]+/g) ?? [];

    matches.map(normalizeVideoUrl).forEach((url) => {
      if (url) {
        urls.add(url);
      }
    });
  };

  const collectFromValue = (value: unknown, depth: number): void => {
    if (depth > 10 || urls.size > 120 || value === null || value === undefined) {
      return;
    }

    if (typeof value === "string") {
      collectFromString(value);
      return;
    }

    if (typeof value !== "object" || seen.has(value)) {
      return;
    }

    seen.add(value);

    if ((typeof Node !== "undefined" && value instanceof Node) || (typeof Window !== "undefined" && value instanceof Window)) {
      return;
    }

    if (Array.isArray(value)) {
      value.slice(0, 80).forEach((item) => collectFromValue(item, depth + 1));
      return;
    }

    Object.entries(value as Record<string, unknown>)
      .slice(0, 160)
      .forEach(([, item]) => collectFromValue(item, depth + 1));
  };

  const scoreVideoUrl = (value: string): number => {
    try {
      const pathname = new URL(value).pathname.toLowerCase();
      const resolution = /\/(\d{2,5})x(\d{2,5})(?:\/|$)/.exec(pathname);
      const pixels = resolution ? Number(resolution[1]) * Number(resolution[2]) : 0;
      let score = pixels / 1000;

      if (pathname.endsWith(".m3u8")) {
        score += 160_000;
      }

      if (pathname.endsWith(".mp4") && !pathname.includes("/pl/")) {
        score += 120_000;
      }

      return score;
    } catch {
      return 0;
    }
  };

  try {
    performance.getEntriesByType("resource").forEach((entry) => collectFromString(entry.name));
  } catch {
    // Continue with DOM-attached data.
  }

  Array.from(document.querySelectorAll<HTMLElement>("article"))
    .slice(0, 40)
    .forEach((article) => {
      const nodes = [article, ...Array.from(article.querySelectorAll<HTMLElement>("*"))].slice(0, 120);

      nodes.forEach((node) => {
        Object.keys(node)
          .filter((key) => key.startsWith("__reactProps$") || key.startsWith("__reactFiber$"))
          .forEach((key) => collectFromValue((node as unknown as Record<string, unknown>)[key], 0));
      });
    });

  return [...urls].sort((a, b) => scoreVideoUrl(b) - scoreVideoUrl(a));
}

async function readObservedVideoUrlsFromSender(tabId: number | undefined): Promise<string[]> {
  if (!tabId) {
    return [];
  }

  return readObservedVideoUrls(tabId);
}

async function readCachedObservedVideoUrls(tabId: number): Promise<string[]> {
  const memoryUrls = observedVideoUrlsByTabId.get(tabId) ?? [];
  const storedUrls = await readStoredObservedVideoUrls(tabId);
  const urls = [...new Set([...storedUrls, ...memoryUrls])].slice(-maxCachedVideoUrlsPerTab);

  if (urls.length > 0) {
    observedVideoUrlsByTabId.set(tabId, urls);
  }

  return urls;
}

async function rememberObservedVideoUrl(tabId: number, url: string): Promise<void> {
  const cachedUrls = await readCachedObservedVideoUrls(tabId);

  if (cachedUrls.includes(url)) {
    return;
  }

  const urls = [...cachedUrls, url].slice(-maxCachedVideoUrlsPerTab);

  observedVideoUrlsByTabId.set(tabId, urls);
  await writeStoredObservedVideoUrls(tabId, urls);
}

async function readStoredObservedVideoUrls(tabId: number): Promise<string[]> {
  const stored = await chrome.storage.session.get(observedVideoUrlsStorageKey);
  const cache = stored[observedVideoUrlsStorageKey] as Record<string, string[]> | undefined;
  const urls = cache?.[String(tabId)];

  return Array.isArray(urls) ? urls.filter((url): url is string => typeof url === "string") : [];
}

async function writeStoredObservedVideoUrls(tabId: number, urls: string[]): Promise<void> {
  const stored = await chrome.storage.session.get(observedVideoUrlsStorageKey);
  const cache = (stored[observedVideoUrlsStorageKey] as Record<string, string[]> | undefined) ?? {};

  await chrome.storage.session.set({
    [observedVideoUrlsStorageKey]: {
      ...cache,
      [String(tabId)]: urls
    }
  });
}

async function removeStoredObservedVideoUrls(tabId: number): Promise<void> {
  const stored = await chrome.storage.session.get(observedVideoUrlsStorageKey);
  const cache = (stored[observedVideoUrlsStorageKey] as Record<string, string[]> | undefined) ?? {};

  if (!(String(tabId) in cache)) {
    return;
  }

  const nextCache = { ...cache };
  delete nextCache[String(tabId)];

  await chrome.storage.session.set({
    [observedVideoUrlsStorageKey]: nextCache
  });
}

function isTwitterVideoUrl(url: string): boolean {
  try {
    return new URL(url).hostname === "video.twimg.com";
  } catch {
    return false;
  }
}

function wait(duration: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, duration));
}
