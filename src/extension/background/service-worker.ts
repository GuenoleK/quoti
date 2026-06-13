import type { QuotiMessageResponse } from "../../shared/types/extension-message.types";
import type { ExtractedPost } from "../../shared/types/post.types";
import { latestPostStorageKey, readQuotiSettings } from "../../shared/settings/quoti-settings";

const contextMenuId = "quoti-create-card";
const observedVideoUrlsStorageKey = "quoti-observed-video-urls";
const maxCachedVideoUrlsPerTab = 80;
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
  const response = (await chrome.tabs.sendMessage(tabId, {
    type: "QUOTI_GET_CONTEXT_POST",
    observedVideoUrls: await readObservedVideoUrls(tabId)
  })) as QuotiMessageResponse;

  if (response.status !== "success" || !hasMissingVideoUrl(response.post)) {
    return response;
  }

  await wait(650);

  return chrome.tabs.sendMessage(tabId, {
    type: "QUOTI_GET_CONTEXT_POST",
    observedVideoUrls: await readObservedVideoUrls(tabId)
  }) as Promise<QuotiMessageResponse>;
}

function hasMissingVideoUrl(post: ExtractedPost): boolean {
  return post.media.some((media) => media.type === "video" && !media.url);
}

async function handleInlinePostCaptured(post: unknown): Promise<void> {
  if (isExtractedPost(post)) {
    await chrome.storage.session.set({
      [latestPostStorageKey]: post
    });
  }

  await openQuotiSurface();
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
