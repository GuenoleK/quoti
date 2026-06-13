import { useCallback, useEffect, useRef, useState } from "react";
import { Settings } from "lucide-react";
import type { QuotiMessageResponse } from "../shared/types/extension-message.types";
import type { CardContentMode, CardTheme, ExtractedPost } from "../shared/types/post.types";
import { latestPostStorageKey } from "../shared/settings/quoti-settings";
import { copyBlobToClipboard, copyImageHtmlToClipboard, copyTextToClipboard } from "../shared/utils/clipboard.util";
import { createPostFilename, formatPostAsText } from "../shared/utils/post-format.util";
import { downloadDataUrl, exportNodeToJpegDataUrl, exportNodeToPngBlob, exportNodeToPngDataUrl } from "../shared/utils/image-export.util";
import { downloadBlob, exportPostVideoToWebmBlob } from "../shared/utils/video-export.util";
import { EmptyState } from "./components/EmptyState/EmptyState";
import { CardContentToggle } from "./components/CardContentToggle/CardContentToggle";
import { CardThemeToggle } from "./components/CardThemeToggle/CardThemeToggle";
import { PostCardActions } from "./components/PostCardPreview/PostCardActions/PostCardActions";
import { PostCardPreview } from "./components/PostCardPreview/PostCardPreview";
import "./Popup.css";

type CaptureState = {
  post: ExtractedPost | null;
  status: "idle" | "loading" | "ready" | "empty" | "error";
  message: string;
};

const unsupportedPageMessage = "Open x.com or twitter.com, hover a post, then open Quoti again.";

export function Popup() {
  const exportRef = useRef<HTMLDivElement>(null);
  const isMountedRef = useRef(true);
  const [capture, setCapture] = useState<CaptureState>({
    post: null,
    status: "idle",
    message: "Looking for the visible post."
  });
  const [busyAction, setBusyAction] = useState<string | null>(null);
  const [actionFeedback, setActionFeedback] = useState<{ action: string; status: "success" | "error" } | null>(null);
  const [contentMode, setContentMode] = useState<CardContentMode>("text-only");
  const [cardTheme, setCardTheme] = useState<CardTheme>("light");
  const [notice, setNotice] = useState<string>("");

  useEffect(() => {
    isMountedRef.current = true;

    return () => {
      isMountedRef.current = false;
    };
  }, []);

  useEffect(() => {
    if (!actionFeedback) {
      return;
    }

    const timeout = window.setTimeout(() => {
      setActionFeedback(null);
    }, 1800);

    return () => window.clearTimeout(timeout);
  }, [actionFeedback]);

  const capturePost = useCallback(async () => {
    setCapture((current) => ({
      ...current,
      status: "loading",
      message: "Capturing the visible post."
    }));
    setNotice("");

    try {
      const pendingPost = await readPendingPost();

      if (!isMountedRef.current) {
        return;
      }

      if (pendingPost) {
        setCapture({
          post: pendingPost,
          status: "ready",
          message: "Post captured."
        });
        setContentMode(pendingPost.media.length > 0 ? "with-media" : "text-only");
        return;
      }

      if (!isChromeExtensionRuntime()) {
        const previewPost = createPreviewPost();

        setCapture({
          post: previewPost,
          status: "ready",
          message: "Preview post loaded."
        });
        setContentMode(previewPost.media.length > 0 ? "with-media" : "text-only");
        return;
      }

      const tab = await getActiveTab();

      if (!isMountedRef.current) {
        return;
      }

      if (!tab.id || !isSupportedUrl(tab.url)) {
        setCapture({
          post: null,
          status: "empty",
          message: unsupportedPageMessage
        });
        return;
      }

      const response = await sendTabMessage(tab.id);

      if (!isMountedRef.current) {
        return;
      }

      if (response.status === "success") {
        setCapture({
          post: response.post,
          status: "ready",
          message: "Post captured."
        });
        setContentMode(response.post.media.length > 0 ? "with-media" : "text-only");
        return;
      }

      if (response.status === "empty") {
        setCapture({
          post: null,
          status: "empty",
          message: response.reason
        });
        return;
      }

      setCapture({
        post: null,
        status: "empty",
        message: "Quoti could not read a post from this page."
      });
    } catch (error) {
      if (!isMountedRef.current) {
        return;
      }

      setCapture({
        post: null,
        status: "error",
        message: error instanceof Error ? error.message : "Quoti could not capture this post."
      });
    }
  }, []);

  useEffect(() => {
    void capturePost();
  }, [capturePost]);

  const runAction = async (actionName: string, action: () => Promise<void>) => {
    setBusyAction(actionName);
    setActionFeedback(null);
    setNotice("");

    try {
      await action();
      if (!isMountedRef.current) {
        return;
      }

      setActionFeedback({ action: actionName, status: "success" });
    } catch (error) {
      if (!isMountedRef.current) {
        return;
      }

      setActionFeedback({ action: actionName, status: "error" });
      setNotice(getActionErrorMessage(actionName, error));
    } finally {
      if (isMountedRef.current) {
        setBusyAction(null);
      }
    }
  };

  const handleDownload = () => {
    if (!capture.post || !exportRef.current) {
      return;
    }

    void runAction("download", async () => {
      const post = capture.post as ExtractedPost;
      const videoMedia = getPrimaryVideo(post);

      if (videoMedia) {
        const video = exportRef.current?.querySelector<HTMLVideoElement>(".context-card__video");

        if (!video) {
          throw new Error("Quoti could not find the video player.");
        }

        const blob = await exportPostVideoToWebmBlob({
          cardTheme,
          post,
          video
        });

        downloadBlob(blob, createPostFilename(post, "webm"));
        return;
      }

      const dataUrl = await exportNodeToJpegDataUrl(exportRef.current as HTMLElement);
      downloadDataUrl(dataUrl, createPostFilename(post, "jpg"));
    });
  };

  const handleCopyImage = () => {
    if (!exportRef.current || !capture.post) {
      return;
    }

    void runAction("copy-image", async () => {
      const blob = await exportNodeToPngBlob(exportRef.current as HTMLElement);

      try {
        await copyBlobToClipboard(blob);
      } catch {
        const dataUrl = await exportNodeToPngDataUrl(exportRef.current as HTMLElement);
        const copied = copyImageHtmlToClipboard(dataUrl, "Quoti card");

        if (!copied) {
          throw new Error("Chrome refused image clipboard access. Try Download JPG for now.");
        }
      }
    });
  };

  const handleCopyText = () => {
    if (!capture.post) {
      return;
    }

    void runAction("copy-text", async () => {
      await copyTextToClipboard(formatPostAsText(capture.post as ExtractedPost));
    });
  };

  const handleCopySource = () => {
    const sourceUrl = capture.post?.sourceUrl;

    if (!sourceUrl) {
      return;
    }

    void runAction("copy-source", async () => {
      await copyTextToClipboard(sourceUrl);
    });
  };

  const handleOpenSource = () => {
    if (!capture.post?.sourceUrl) {
      return;
    }

    if (typeof chrome !== "undefined" && chrome.tabs?.create) {
      void chrome.tabs.create({ url: capture.post.sourceUrl });
      return;
    }

    window.open(capture.post.sourceUrl, "_blank", "noopener,noreferrer");
  };

  const handleOpenOptions = () => {
    if (typeof chrome !== "undefined" && chrome.runtime?.openOptionsPage) {
      void chrome.runtime.openOptionsPage();
      return;
    }

    window.open("/options.html", "_blank", "noopener,noreferrer");
  };

  const isBusy = Boolean(busyAction) || capture.status === "loading";

  return (
    <main className="popup">
      <header className="popup__header">
        <div className="popup__brand">
          <span className="popup__logo" aria-hidden="true">Q</span>
          <div>
            <h1 className="popup__title">Quoti</h1>
            <p className="popup__subtitle">Capture the post. Keep the context.</p>
          </div>
        </div>
        <button className="popup__settings-button" onClick={handleOpenOptions} type="button" title="Open options" aria-label="Open options">
          <Settings size={17} aria-hidden="true" />
        </button>
      </header>

      {capture.post ? (
        <div className="popup__content">
          <PostCardPreview post={capture.post} contentMode={contentMode} cardTheme={cardTheme} exportRef={exportRef} />
          <div className="popup__toggles">
            <CardThemeToggle value={cardTheme} onChange={setCardTheme} />
            <CardContentToggle
              disabled={capture.post.media.length === 0}
              value={contentMode}
              onChange={(mode) => setContentMode(capture.post?.media.length ? mode : "text-only")}
            />
          </div>
          <PostCardActions
            actionFeedback={actionFeedback}
            busyAction={busyAction}
            canOpenSource={Boolean(capture.post.sourceUrl)}
            downloadMode={getPrimaryVideo(capture.post) ? "video" : "image"}
            isBusy={isBusy}
            onCopyImage={handleCopyImage}
            onCopySource={handleCopySource}
            onCopyText={handleCopyText}
            onDownload={handleDownload}
            onOpenSource={handleOpenSource}
            onRefresh={capturePost}
          />
        </div>
      ) : (
        <EmptyState message={capture.message} onRefresh={capturePost} />
      )}

      {notice ? <p className="popup__notice" aria-live="polite">{notice}</p> : null}
    </main>
  );
}

function getPrimaryVideo(post: ExtractedPost) {
  return post.media.find((media) => media.type === "video");
}

function getActionErrorMessage(actionName: string, error: unknown): string {
  const detail = error instanceof Error ? error.message : "";

  if (actionName === "copy-image") {
    return detail || "Quoti could not copy the image. Try Download JPG.";
  }

  if (actionName === "download") {
    return detail || "Quoti could not prepare the download.";
  }

  if (actionName === "copy-text") {
    return detail || "Quoti could not copy the text.";
  }

  if (actionName === "copy-source") {
    return detail || "Quoti could not copy the source link.";
  }

  return detail || "The action could not be completed.";
}

async function getActiveTab(): Promise<chrome.tabs.Tab> {
  const [tab] = await chrome.tabs.query({ active: true, currentWindow: true });

  if (!tab) {
    throw new Error("No active tab found.");
  }

  return tab;
}

async function sendTabMessage(tabId: number): Promise<QuotiMessageResponse> {
  const observedVideoUrls = await readObservedVideoUrls(tabId);
  const response = await requestSelectedPost(tabId, observedVideoUrls);

  if (response.status !== "success" || !hasMissingVideoUrl(response.post)) {
    return response;
  }

  await wait(650);

  return requestSelectedPost(tabId, await readObservedVideoUrls(tabId));
}

async function requestSelectedPost(tabId: number, observedVideoUrls: string[]): Promise<QuotiMessageResponse> {
  try {
    return await chrome.tabs.sendMessage(tabId, { type: "QUOTI_GET_SELECTED_POST", observedVideoUrls });
  } catch {
    await ensureContentScript(tabId);
    return chrome.tabs.sendMessage(tabId, { type: "QUOTI_GET_SELECTED_POST", observedVideoUrls });
  }
}

function hasMissingVideoUrl(post: ExtractedPost): boolean {
  return post.media.some((media) => media.type === "video" && !media.url);
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
  try {
    const response = await chrome.runtime.sendMessage({ type: "QUOTI_READ_OBSERVED_VIDEO_URLS", tabId });

    if (response?.status === "video-urls" && Array.isArray(response.observedVideoUrls)) {
      return response.observedVideoUrls.filter((url: unknown): url is string => typeof url === "string");
    }
  } catch {
    // Fall back to reading the page performance entries directly.
  }

  try {
    const [result] = await chrome.scripting.executeScript({
      target: { tabId },
      world: "MAIN",
      func: () => performance.getEntriesByType("resource").map((entry) => entry.name)
    });

    return Array.isArray(result?.result) ? result.result.filter((url): url is string => typeof url === "string") : [];
  } catch {
    return [];
  }
}

function isSupportedUrl(url: string | undefined): boolean {
  if (!url) {
    return false;
  }

  return url.startsWith("https://x.com/") || url.startsWith("https://twitter.com/");
}

function isChromeExtensionRuntime(): boolean {
  return typeof chrome !== "undefined" && Boolean(chrome.tabs?.query);
}

function wait(duration: number): Promise<void> {
  return new Promise((resolve) => window.setTimeout(resolve, duration));
}

async function readPendingPost(): Promise<ExtractedPost | null> {
  if (typeof chrome === "undefined" || !chrome.storage?.session) {
    return null;
  }

  const stored = await chrome.storage.session.get(latestPostStorageKey);
  const post = stored[latestPostStorageKey] as ExtractedPost | undefined;

  if (post) {
    await chrome.storage.session.remove(latestPostStorageKey);
  }

  return post ?? null;
}

function createPreviewPost(): ExtractedPost {
  return {
    id: "preview-post",
    platform: "x",
    authorName: "Quoti",
    authorHandle: "@quoti",
    content: "Les conversations meritent de voyager avec leur contexte.",
    publishedAt: new Date().toISOString(),
    sourceUrl: "https://x.com/",
    media: [
      {
        type: "image",
        url: "https://images.unsplash.com/photo-1524995997946-a1c2e315a42f?auto=format&fit=crop&w=1200&q=80",
        alt: "Books on a shelf"
      }
    ],
    capturedAt: new Date().toISOString()
  };
}
