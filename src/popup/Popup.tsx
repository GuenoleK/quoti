import { useCallback, useEffect, useRef, useState } from "react";
import { Settings } from "lucide-react";
import type { QuotiMessageResponse } from "../shared/types/extension-message.types";
import type { CardContentMode, CardTheme, ExtractedPost, PostMedia, VideoPostMedia } from "../shared/types/post.types";
import { latestPostStorageKey } from "../shared/settings/quoti-settings";
import { copyBlobToClipboard, copyImageHtmlToClipboard, copyTextToClipboard } from "../shared/utils/clipboard.util";
import { createPostFilename, formatPostAsText } from "../shared/utils/post-format.util";
import { downloadDataUrl, exportNodeToJpegDataUrl, exportNodeToPngBlob, exportNodeToPngDataUrl } from "../shared/utils/image-export.util";
import { downloadBlob } from "../shared/utils/video-export.util";
import type { VideoRenderProgress } from "../rendering/video/video-render.types";
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

type VideoWarmupStatus = "idle" | "loading" | "ready" | "error";
type MediaRecoveryStatus = "idle" | "loading";
type VideoRenderController = typeof import("../rendering/video/video-render.controller");

const unsupportedPageMessage = "Open x.com or twitter.com, hover a post, then open Quoti again.";
let videoRenderControllerPromise: Promise<VideoRenderController> | null = null;

export function Popup() {
  const exportRef = useRef<HTMLDivElement>(null);
  const isMountedRef = useRef(true);
  const mediaRecoveryPostKeyRef = useRef<string | null>(null);
  const postMediaCacheRef = useRef<Map<string, PostMedia[]>>(new Map());
  const videoWarmupPromiseRef = useRef<Promise<void> | null>(null);
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
  const [mediaRecoveryStatus, setMediaRecoveryStatus] = useState<MediaRecoveryStatus>("idle");
  const [videoWarmupStatus, setVideoWarmupStatus] = useState<VideoWarmupStatus>("idle");
  const [videoRenderProgress, setVideoRenderProgress] = useState<VideoRenderProgress | null>(null);

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

  const warmVideoRenderer = useCallback((): Promise<void> => {
    if (videoWarmupPromiseRef.current) {
      return videoWarmupPromiseRef.current;
    }

    setVideoWarmupStatus("loading");

    const warmupPromise = loadVideoRenderController()
      .then((controller) => controller.preloadVideoRenderer())
      .then(() => {
        if (isMountedRef.current) {
          setVideoWarmupStatus("ready");
        }
      })
      .catch((error) => {
        if (isMountedRef.current) {
          setVideoWarmupStatus("error");
        }

        throw error;
      })
      .finally(() => {
        videoWarmupPromiseRef.current = null;
      });

    videoWarmupPromiseRef.current = warmupPromise;

    return warmupPromise;
  }, []);

  const recoverMissingVideoMedia = useCallback(
    async (post: ExtractedPost) => {
      if (!hasMissingVideoUrl(post)) {
        if (hasVideoMedia(post)) {
          void warmVideoRenderer().catch(() => undefined);
        }

        return;
      }

      const postKey = getPostCacheKey(post);

      if (!postKey || mediaRecoveryPostKeyRef.current === postKey) {
        return;
      }

      mediaRecoveryPostKeyRef.current = postKey;
      setMediaRecoveryStatus("loading");
      setNotice("Video source is still loading. Keeping the post context visible.");

      try {
        const warmupPromise = warmVideoRenderer().catch(() => undefined);

        if (!isChromeExtensionRuntime()) {
          await warmupPromise;
          return;
        }

        const tab = await getActiveTab();

        if (!tab.id || !isSupportedUrl(tab.url)) {
          await warmupPromise;
          return;
        }

        await warmupPromise;

        const recoveredPost = await recoverPostVideoMedia(tab.id, post);

        if (!isMountedRef.current) {
          return;
        }

        if (hasMissingVideoUrl(recoveredPost)) {
          setNotice("Video source is still warming up. Refresh capture in a few seconds if needed.");
          return;
        }

        const hydratedPost = preserveSessionMedia(postMediaCacheRef.current, recoveredPost);

        setCapture((current) => {
          if (!current.post || getPostCacheKey(current.post) !== postKey) {
            return current;
          }

          return {
            post: hydratedPost,
            status: "ready",
            message: "Post captured."
          };
        });
        setNotice("");
      } finally {
        if (isMountedRef.current) {
          setMediaRecoveryStatus("idle");
        }
      }
    },
    [warmVideoRenderer]
  );

  const capturePost = useCallback(async () => {
    mediaRecoveryPostKeyRef.current = null;
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
        const post = preserveSessionMedia(postMediaCacheRef.current, pendingPost);

        setCapture({
          post,
          status: "ready",
          message: "Post captured."
        });
        setContentMode(post.media.length > 0 ? "with-media" : "text-only");
        void recoverMissingVideoMedia(post);
        return;
      }

      if (!isChromeExtensionRuntime()) {
        const previewPost = preserveSessionMedia(postMediaCacheRef.current, createPreviewPost());

        setCapture({
          post: previewPost,
          status: "ready",
          message: "Preview post loaded."
        });
        setContentMode(previewPost.media.length > 0 ? "with-media" : "text-only");
        void recoverMissingVideoMedia(previewPost);
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
        const post = preserveSessionMedia(postMediaCacheRef.current, response.post);

        setCapture({
          post,
          status: "ready",
          message: "Post captured."
        });
        setContentMode(post.media.length > 0 ? "with-media" : "text-only");
        void recoverMissingVideoMedia(post);
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
  }, [recoverMissingVideoMedia]);

  useEffect(() => {
    if (!capture.post || !hasVideoMedia(capture.post)) {
      return;
    }

    void warmVideoRenderer().catch(() => undefined);
  }, [capture.post, warmVideoRenderer]);

  useEffect(() => {
    void capturePost();
  }, [capturePost]);

  const runAction = async (actionName: string, action: () => Promise<void>) => {
    setBusyAction(actionName);
    setActionFeedback(null);
    setNotice("");

    if (actionName === "download") {
      setVideoRenderProgress(null);
    }

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
        const { renderPostVideo } = await loadVideoRenderController();
        const video = exportRef.current?.querySelector<HTMLVideoElement>(".context-card__video");
        const result = await renderPostVideo({
          browserVideo: video,
          cardTheme,
          post,
          quality: "balanced",
          onProgress: setVideoRenderProgress,
          templateNode: exportRef.current as HTMLElement
        });

        downloadBlob(result.blob, createPostFilename(post, result.filenameExtension));
        return;
      }

      const dataUrl = await exportNodeToJpegDataUrl(exportRef.current as HTMLElement);
      downloadDataUrl(dataUrl, createPostFilename(post, "jpg"));
      setVideoRenderProgress(null);
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

  const isRecoveringVideoMedia = mediaRecoveryStatus === "loading";
  const isBusy = Boolean(busyAction) || capture.status === "loading" || isRecoveringVideoMedia;
  const statusNotice =
    notice ||
    (isRecoveringVideoMedia
      ? "Video source is still loading. Keeping the post context visible."
      : videoWarmupStatus === "loading" && capture.post && hasVideoMedia(capture.post)
        ? "Video renderer is warming up for the first export."
        : "");

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
            downloadProgressLabel={getVideoRenderProgressLabel(videoRenderProgress)}
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
        <EmptyState isLoading={capture.status === "loading"} message={capture.message} onRefresh={capturePost} />
      )}

      {statusNotice ? <p className="popup__notice" aria-live="polite">{statusNotice}</p> : null}
    </main>
  );
}

function loadVideoRenderController(): Promise<VideoRenderController> {
  videoRenderControllerPromise ??= import("../rendering/video/video-render.controller").catch((error) => {
    videoRenderControllerPromise = null;
    throw error;
  });

  return videoRenderControllerPromise;
}

function getVideoRenderProgressLabel(progress: VideoRenderProgress | null): string | undefined {
  if (!progress) {
    return undefined;
  }

  if (progress.stage === "rendering" && Number.isFinite(progress.progress)) {
    return `${progress.message} ${Math.round(progress.progress * 100)}%`;
  }

  return progress.message;
}

function getPrimaryVideo(post: ExtractedPost) {
  return post.media.find((media) => media.type === "video");
}

function hasVideoMedia(post: ExtractedPost): boolean {
  return post.media.some((media) => media.type === "video");
}

function hasVideoMediaSource(media: VideoPostMedia): boolean {
  return Boolean(media.url || media.variants?.some((url) => Boolean(normalizeVideoSourceUrl(url))));
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
  return requestSelectedPostWithVideoRetries(tabId, [0, 650, 1250]);
}

async function requestSelectedPostWithVideoRetries(tabId: number, delays: number[]): Promise<QuotiMessageResponse> {
  let latestResponse: QuotiMessageResponse | null = null;

  for (const delay of delays) {
    if (delay > 0) {
      await wait(delay);
    }

    const response = await requestSelectedPost(tabId, await readObservedVideoUrls(tabId));
    latestResponse = response;

    if (response.status !== "success" || !hasMissingVideoUrl(response.post)) {
      return response;
    }
  }

  return latestResponse ?? { status: "empty", reason: "Quoti could not read a post from this page." };
}

async function recoverPostVideoMedia(tabId: number, post: ExtractedPost): Promise<ExtractedPost> {
  let recoveredPost = hydratePostVideoUrls(post, await readObservedVideoUrls(tabId));

  if (!hasMissingVideoUrl(recoveredPost)) {
    return recoveredPost;
  }

  for (const delay of [500, 900, 1400]) {
    await wait(delay);

    const observedVideoUrls = await readObservedVideoUrls(tabId);
    recoveredPost = hydratePostVideoUrls(recoveredPost, observedVideoUrls);

    if (!hasMissingVideoUrl(recoveredPost)) {
      return recoveredPost;
    }

    const response = await requestSelectedPost(tabId, observedVideoUrls);

    if (response.status === "success") {
      recoveredPost = mergeRecoveredPostMedia(recoveredPost, response.post);
    }

    if (!hasMissingVideoUrl(recoveredPost)) {
      return recoveredPost;
    }
  }

  return recoveredPost;
}

function mergeRecoveredPostMedia(basePost: ExtractedPost, recoveredPost: ExtractedPost): ExtractedPost {
  if (!isSamePost(basePost, recoveredPost)) {
    return basePost;
  }

  return {
    ...basePost,
    media: mergePostMedia(recoveredPost.media, basePost.media)
  };
}

function isSamePost(left: ExtractedPost, right: ExtractedPost): boolean {
  const leftKey = getPostCacheKey(left);
  const rightKey = getPostCacheKey(right);

  if (leftKey && rightKey) {
    return leftKey === rightKey;
  }

  return left.authorHandle === right.authorHandle && left.content === right.content;
}

function hydratePostVideoUrls(post: ExtractedPost, observedVideoUrls: string[]): ExtractedPost {
  if (observedVideoUrls.length === 0) {
    return post;
  }

  let changed = false;
  const media = post.media.map((item) => {
    if (item.type !== "video") {
      return item;
    }

    const observedUrls = readObservedVideoUrlsForMedia(item.posterUrl, observedVideoUrls);
    const variants = mergeUrlList([item.url, ...(item.variants ?? []), ...observedUrls]);

    if (variants.length === 0) {
      return item;
    }

    const nextItem = {
      ...item,
      url: item.url ?? variants[0],
      variants
    };

    changed = changed || nextItem.url !== item.url || variants.length !== (item.variants?.length ?? 0);

    return nextItem;
  });

  return changed ? { ...post, media } : post;
}

function readObservedVideoUrlsForMedia(posterUrl: string | undefined, observedVideoUrls: string[]): string[] {
  const mediaId = extractTwitterVideoMediaId(posterUrl);

  if (!mediaId) {
    return [];
  }

  return [
    ...new Set(
      observedVideoUrls
        .map(normalizeVideoSourceUrl)
        .filter((url): url is string => Boolean(url))
        .filter((url) => url.includes(`/${mediaId}/`) && !isVideoSegmentUrl(url))
    )
  ].sort((a, b) => scoreVideoSourceUrl(b) - scoreVideoSourceUrl(a));
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
  return post.media.some((media) => media.type === "video" && !hasVideoMediaSource(media));
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

function extractTwitterVideoMediaId(value: string | undefined): string | undefined {
  if (!value) {
    return undefined;
  }

  try {
    const url = new URL(value);

    return /\/(?:ext_tw_video_thumb|amplify_video_thumb|tweet_video_thumb)\/([^/]+)\//.exec(url.pathname)?.[1];
  } catch {
    return undefined;
  }
}

function normalizeVideoSourceUrl(value: string | undefined): string | undefined {
  if (!value || value.startsWith("blob:") || value.startsWith("data:")) {
    return undefined;
  }

  try {
    const url = new URL(value.replace(/\\u0026/g, "&").replace(/&amp;/g, "&"));

    if (!["http:", "https:"].includes(url.protocol) || !url.hostname.endsWith("twimg.com")) {
      return undefined;
    }

    return url.toString();
  } catch {
    return undefined;
  }
}

function isVideoSegmentUrl(value: string): boolean {
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
}

function scoreVideoSourceUrl(value: string): number {
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

function preserveSessionMedia(cache: Map<string, PostMedia[]>, post: ExtractedPost): ExtractedPost {
  const cacheKey = getPostCacheKey(post);

  if (!cacheKey) {
    return post;
  }

  const cachedMedia = cache.get(cacheKey);
  const hydratedPost = cachedMedia
    ? {
        ...post,
        media: mergePostMedia(post.media, cachedMedia)
      }
    : post;

  cache.set(cacheKey, hydratedPost.media);

  return hydratedPost;
}

function getPostCacheKey(post: ExtractedPost): string {
  return post.sourceUrl || post.id;
}

function mergePostMedia(currentMedia: PostMedia[], cachedMedia: PostMedia[]): PostMedia[] {
  if (currentMedia.length === 0 && cachedMedia.length > 0) {
    return cachedMedia;
  }

  return currentMedia.map((media, index) => {
    const cached = findCachedMedia(media, cachedMedia, index);

    if (!cached || cached.type !== media.type) {
      return media;
    }

    if (media.type === "image" && cached.type === "image") {
      return {
        ...media,
        alt: media.alt ?? cached.alt,
        url: media.url || cached.url
      };
    }

    if (media.type === "video" && cached.type === "video") {
      const variants = mergeUrlList([media.url, ...(media.variants ?? []), cached.url, ...(cached.variants ?? [])]);

      return {
        ...media,
        alt: media.alt ?? cached.alt,
        duration: media.duration ?? cached.duration,
        posterUrl: media.posterUrl ?? cached.posterUrl,
        url: media.url ?? cached.url ?? variants[0],
        variants
      };
    }

    return media;
  });
}

function findCachedMedia(media: PostMedia, cachedMedia: PostMedia[], index: number): PostMedia | undefined {
  const exactMatch = cachedMedia.find((cached) => {
    if (cached.type !== media.type) {
      return false;
    }

    if (media.type === "image" && cached.type === "image") {
      return cached.url === media.url;
    }

    if (media.type === "video" && cached.type === "video") {
      return Boolean(
        (media.url && cached.url === media.url) ||
          (media.posterUrl && cached.posterUrl === media.posterUrl) ||
          (media.posterUrl && cached.variants?.includes(media.posterUrl))
      );
    }

    return false;
  });

  return exactMatch ?? (cachedMedia[index]?.type === media.type ? cachedMedia[index] : undefined);
}

function mergeUrlList(urls: Array<string | undefined>): string[] {
  return [...new Set(urls.filter((url): url is string => typeof url === "string" && url.length > 0))];
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
