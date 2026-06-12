import type { QuotiMessageResponse } from "../../shared/types/extension-message.types";
import type { ExtractedPost } from "../../shared/types/post.types";
import { latestPostStorageKey, readQuotiSettings } from "../../shared/settings/quoti-settings";

const contextMenuId = "quoti-create-card";

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
    contexts: ["page", "selection", "link", "image"],
    documentUrlPatterns: ["https://x.com/*", "https://twitter.com/*"]
  });
}

async function captureContextPost(tabId: number): Promise<void> {
  try {
    await ensureContentScript(tabId);
    const response = (await chrome.tabs.sendMessage(tabId, {
      type: "QUOTI_GET_CONTEXT_POST"
    })) as QuotiMessageResponse;

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
