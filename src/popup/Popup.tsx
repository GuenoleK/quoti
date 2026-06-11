import { useCallback, useEffect, useRef, useState } from "react";
import type { QuotiMessageResponse } from "../shared/types/extension-message.types";
import type { CardTheme, ExtractedPost } from "../shared/types/post.types";
import { copyBlobToClipboard, copyTextToClipboard } from "../shared/utils/clipboard.util";
import { createPostFilename, formatPostAsText } from "../shared/utils/post-format.util";
import { downloadDataUrl, exportNodeToJpegDataUrl, exportNodeToPngBlob } from "../shared/utils/image-export.util";
import { EmptyState } from "./components/EmptyState/EmptyState";
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
  const [capture, setCapture] = useState<CaptureState>({
    post: null,
    status: "idle",
    message: "Looking for the visible post."
  });
  const [busyAction, setBusyAction] = useState<string | null>(null);
  const [cardTheme, setCardTheme] = useState<CardTheme>("light");
  const [notice, setNotice] = useState<string>("");

  const capturePost = useCallback(async () => {
    setCapture((current) => ({
      ...current,
      status: "loading",
      message: "Capturing the visible post."
    }));
    setNotice("");

    try {
      if (!isChromeExtensionRuntime()) {
        setCapture({
          post: createPreviewPost(),
          status: "ready",
          message: "Preview post loaded."
        });
        return;
      }

      const tab = await getActiveTab();

      if (!tab.id || !isSupportedUrl(tab.url)) {
        setCapture({
          post: null,
          status: "empty",
          message: unsupportedPageMessage
        });
        return;
      }

      const response = await sendTabMessage(tab.id);

      if (response.status === "success") {
        setCapture({
          post: response.post,
          status: "ready",
          message: "Post captured."
        });
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
    setNotice("");

    try {
      await action();
    } catch (error) {
      setNotice(error instanceof Error ? error.message : "The action could not be completed.");
    } finally {
      setBusyAction(null);
    }
  };

  const handleDownloadJpg = () => {
    if (!capture.post || !exportRef.current) {
      return;
    }

    void runAction("download", async () => {
      const dataUrl = await exportNodeToJpegDataUrl(exportRef.current as HTMLElement);
      downloadDataUrl(dataUrl, createPostFilename(capture.post as ExtractedPost, "jpg"));
      setNotice("JPG downloaded.");
    });
  };

  const handleCopyImage = () => {
    if (!exportRef.current) {
      return;
    }

    void runAction("copy-image", async () => {
      const blob = await exportNodeToPngBlob(exportRef.current as HTMLElement);
      await copyBlobToClipboard(blob);
      setNotice("Image copied.");
    });
  };

  const handleCopyText = () => {
    if (!capture.post) {
      return;
    }

    void runAction("copy-text", async () => {
      await copyTextToClipboard(formatPostAsText(capture.post as ExtractedPost));
      setNotice("Text copied.");
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
      </header>

      {capture.post ? (
        <div className="popup__content">
          <PostCardPreview post={capture.post} cardTheme={cardTheme} exportRef={exportRef} />
          <CardThemeToggle value={cardTheme} onChange={setCardTheme} />
          <PostCardActions
            canOpenSource={Boolean(capture.post.sourceUrl)}
            isBusy={isBusy}
            onCopyImage={handleCopyImage}
            onCopyText={handleCopyText}
            onDownload={handleDownloadJpg}
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

async function getActiveTab(): Promise<chrome.tabs.Tab> {
  const [tab] = await chrome.tabs.query({ active: true, currentWindow: true });

  if (!tab) {
    throw new Error("No active tab found.");
  }

  return tab;
}

async function sendTabMessage(tabId: number): Promise<QuotiMessageResponse> {
  try {
    return await chrome.tabs.sendMessage(tabId, { type: "QUOTI_GET_SELECTED_POST" });
  } catch {
    throw new Error(unsupportedPageMessage);
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

function createPreviewPost(): ExtractedPost {
  return {
    id: "preview-post",
    platform: "x",
    authorName: "Quoti",
    authorHandle: "@quoti",
    content: "Les conversations meritent de voyager avec leur contexte.",
    publishedAt: new Date().toISOString(),
    sourceUrl: "https://x.com/",
    capturedAt: new Date().toISOString()
  };
}
