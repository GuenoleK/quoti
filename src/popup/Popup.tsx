import { useCallback, useEffect, useRef, useState, type Dispatch, type SetStateAction } from "react";
import { ArrowLeft, Images, Settings } from "lucide-react";
import type { QuotiMessageResponse } from "../shared/types/extension-message.types";
import type { CardContentMode, CardTheme, ExtractedPost, PostMedia, VideoPostMedia } from "../shared/types/post.types";
import {
  defaultQuotiSettings,
  latestPostStorageKey,
  readQuotiSettings,
  writeQuotiSettings,
  type QuotiSettings
} from "../shared/settings/quoti-settings";
import { copyBlobToClipboard, copyImageHtmlToClipboard, copyTextToClipboard } from "../shared/utils/clipboard.util";
import { createPostFilename, formatPostAsText } from "../shared/utils/post-format.util";
import { exportNodeToPngBlob, exportNodeToPngDataUrl } from "../shared/utils/image-export.util";
import { downloadBlob } from "../shared/utils/video-export.util";
import type { VideoRenderProgress } from "../rendering/video/video-render.types";
import { EmptyState } from "./components/EmptyState/EmptyState";
import { GalleryView } from "./components/GalleryView/GalleryView";
import { CardContentToggle } from "./components/CardContentToggle/CardContentToggle";
import { CardThemeToggle } from "./components/CardThemeToggle/CardThemeToggle";
import { PostCardActions } from "./components/PostCardPreview/PostCardActions/PostCardActions";
import { PostCardPreview } from "./components/PostCardPreview/PostCardPreview";
import { deleteGalleryCards, mergeGalleryCard, readGalleryCards, upsertGalleryCard, type GalleryCard } from "./gallery-storage";
import "./Popup.css";

type CaptureState = {
  post: ExtractedPost | null;
  status: "idle" | "loading" | "ready" | "empty" | "error";
  message: string;
};

type VideoWarmupStatus = "idle" | "loading" | "ready" | "error";
type MediaRecoveryStatus = "idle" | "loading";
type PopupView = "capture" | "gallery" | "settings";
type VideoRenderController = typeof import("../rendering/video/video-render.controller");
type CachedPostMedia = {
  media: PostMedia[];
  relatedMedia?: PostMedia[];
};

const unsupportedPageMessage = "Open X, Threads, LinkedIn, or Facebook, hover a post, then open Quoti again.";
let videoRenderControllerPromise: Promise<VideoRenderController> | null = null;

export function Popup() {
  const exportRef = useRef<HTMLDivElement>(null);
  const contentModeOverrideRef = useRef<CardContentMode | null>(null);
  const isMountedRef = useRef(true);
  const mediaRecoveryPostKeyRef = useRef<string | null>(null);
  const postMediaCacheRef = useRef<Map<string, CachedPostMedia>>(new Map());
  const settingsRef = useRef<QuotiSettings>(defaultQuotiSettings);
  const skipNextGallerySaveRef = useRef(false);
  const videoExportRef = useRef<HTMLDivElement>(null);
  const videoWarmupPromiseRef = useRef<Promise<void> | null>(null);
  const [capture, setCapture] = useState<CaptureState>({
    post: null,
    status: "idle",
    message: "Looking for the visible post."
  });
  const [loadingTitle, setLoadingTitle] = useState<string | undefined>();
  const [busyAction, setBusyAction] = useState<string | null>(null);
  const [actionFeedback, setActionFeedback] = useState<{ action: string; status: "success" | "error" } | null>(null);
  const [contentMode, setContentMode] = useState<CardContentMode>(defaultQuotiSettings.cardContentMode);
  const [cardTheme, setCardTheme] = useState<CardTheme>(defaultQuotiSettings.cardTheme);
  const [settings, setSettings] = useState<QuotiSettings>(defaultQuotiSettings);
  const [settingsStatus, setSettingsStatus] = useState("Saved automatically.");
  const [activeView, setActiveView] = useState<PopupView>("capture");
  const [galleryCards, setGalleryCards] = useState<GalleryCard[]>([]);
  const [galleryStatus, setGalleryStatus] = useState<"idle" | "loading" | "ready" | "error">("idle");
  const [galleryQuery, setGalleryQuery] = useState("");
  const [galleryNotice, setGalleryNotice] = useState("");
  const [selectedGalleryIds, setSelectedGalleryIds] = useState<Set<string>>(() => new Set());
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

  useEffect(() => {
    void readQuotiSettings().then((storedSettings) => {
      if (isMountedRef.current) {
        settingsRef.current = storedSettings;
        setSettings(storedSettings);
        setCardTheme(storedSettings.cardTheme);
      }
    });
  }, []);

  const loadGalleryCards = useCallback(async () => {
    setGalleryStatus("loading");

    try {
      const cards = await readGalleryCards();

      if (!isMountedRef.current) {
        return;
      }

      setGalleryCards(cards);
      setGalleryStatus("ready");
    } catch (error) {
      if (!isMountedRef.current) {
        return;
      }

      console.error("[Quoti] gallery read failed", error);
      setGalleryStatus("error");
      setGalleryNotice("Gallery could not be loaded.");
    }
  }, []);

  useEffect(() => {
    void loadGalleryCards();
  }, [loadGalleryCards]);

  useEffect(() => {
    const preferredMode = contentModeOverrideRef.current ?? settings.cardContentMode;
    contentModeOverrideRef.current = null;
    setContentMode(resolveCardContentModePreference(capture.post, preferredMode));
  }, [capture.post, settings.cardContentMode]);

  useEffect(() => {
    if (!capture.post || capture.status !== "ready") {
      return;
    }

    if (skipNextGallerySaveRef.current) {
      skipNextGallerySaveRef.current = false;
      return;
    }

    let cancelled = false;

    void upsertGalleryCard(capture.post, { cardTheme, contentMode })
      .then((savedCard) => {
        if (!isMountedRef.current || cancelled) {
          return;
        }

        setGalleryCards((cards) => mergeGalleryCard(cards, savedCard));
      })
      .catch((error) => {
        console.error("[Quoti] gallery save failed", error);
        if (isMountedRef.current) {
          setNotice("Quoti could not save this card to the gallery.");
        }
      });

    return () => {
      cancelled = true;
    };
  }, [capture.post, capture.status, cardTheme, contentMode]);

  useEffect(() => {
    setSelectedGalleryIds((currentSelection) => {
      const availableIds = new Set(galleryCards.map((card) => card.id));
      const nextSelection = new Set([...currentSelection].filter((id) => availableIds.has(id)));

      return nextSelection.size === currentSelection.size ? currentSelection : nextSelection;
    });
  }, [galleryCards]);

  const updateSetting = <Key extends keyof QuotiSettings>(key: Key, value: QuotiSettings[Key]) => {
    const nextSettings = {
      ...settingsRef.current,
      [key]: value
    };

    settingsRef.current = nextSettings;
    setSettings(nextSettings);
    setCardTheme(nextSettings.cardTheme);
    setContentMode(resolveCardContentModePreference(capture.post, nextSettings.cardContentMode));

    void writeQuotiSettings(nextSettings).then(() => {
      if (!isMountedRef.current) {
        return;
      }

      setSettingsStatus("Saved automatically.");

      if (typeof chrome !== "undefined" && chrome.runtime?.sendMessage) {
        void chrome.runtime.sendMessage({ type: "QUOTI_SETTINGS_UPDATED" }).catch(() => undefined);
      }
    });
  };

  const handleCardThemeChange = (theme: CardTheme) => {
    updateSetting("cardTheme", theme);
  };

  const handleCardContentModeChange = (mode: CardContentMode) => {
    updateSetting("cardContentMode", mode);
  };

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
      setNotice("Looking for the video. Keep the post visible.");

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
          setNotice("The video is still loading. Refresh capture in a few seconds if needed.");
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
    setLoadingTitle(undefined);
    setNotice("");

    try {
      const pendingPost = await readPendingPost();

      if (!isMountedRef.current) {
        return;
      }

      if (pendingPost) {
        if (showRelatedPostHydrationLoader(pendingPost, setCapture, setLoadingTitle)) {
          await waitForUiFrame();
        }

        const hydratedPendingPost = await hydrateRelatedPost(pendingPost);

        if (!isMountedRef.current) {
          return;
        }

        const post = preserveSessionMedia(postMediaCacheRef.current, hydratedPendingPost);

        setCapture({
          post,
          status: "ready",
          message: "Post captured."
        });
        setContentMode(resolveCardContentModePreference(post, settingsRef.current.cardContentMode));
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
        setContentMode(resolveCardContentModePreference(previewPost, settingsRef.current.cardContentMode));
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
        if (showRelatedPostHydrationLoader(response.post, setCapture, setLoadingTitle)) {
          await waitForUiFrame();
        }

        const hydratedPost = await hydrateRelatedPost(response.post);

        if (!isMountedRef.current) {
          return;
        }

        const post = preserveSessionMedia(postMediaCacheRef.current, hydratedPost);

        setCapture({
          post,
          status: "ready",
          message: "Post captured."
        });
        setContentMode(resolveCardContentModePreference(post, settingsRef.current.cardContentMode));
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

      console.error(`[Quoti] ${actionName} failed`, error);
      setActionFeedback({ action: actionName, status: "error" });
      setNotice(getActionErrorMessage(actionName, error));
    } finally {
      if (isMountedRef.current) {
        setBusyAction(null);
      }
    }
  };

  const handleDownload = () => {
    if (!capture.post) {
      return;
    }

    void runAction("download", async () => {
      const post = capture.post as ExtractedPost;
      const videoMedia = getPrimaryVideo(post);

      if (videoMedia) {
        const templateNode = videoExportRef.current ?? exportRef.current;

        if (!templateNode) {
          throw new Error("Video export template is not ready yet. Refresh capture and retry.");
        }

        const { renderPostVideo } = await loadVideoRenderController();
        const result = await renderPostVideo({
          browserVideo: getExportVideoElement(templateNode),
          cardTheme,
          post,
          preferredRenderer: settings.videoRenderer,
          quality: settings.videoQuality,
          onProgress: setVideoRenderProgress,
          templateNode
        });

        downloadBlob(result.blob, createPostFilename(post, result.filenameExtension));
        return;
      }

      if (!exportRef.current) {
        throw new Error("Image export template is not ready yet. Refresh capture and retry.");
      }

      const blob = await exportNodeToPngBlob(exportRef.current as HTMLElement);
      downloadBlob(blob, createPostFilename(post, "png"));
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
          throw new Error("Chrome refused image clipboard access. Try Download PNG for now.");
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
    setActiveView("settings");
    setGalleryNotice("");
  };

  const handleOpenGallery = () => {
    setActiveView("gallery");
    setNotice("");
    setGalleryNotice("");
    void loadGalleryCards();
  };

  const handleBackToCapture = () => {
    setActiveView("capture");
    setGalleryNotice("");
    setSelectedGalleryIds(new Set());
  };

  const handleOpenGalleryCard = (card: GalleryCard) => {
    const post = preserveSessionMedia(postMediaCacheRef.current, card.post);

    contentModeOverrideRef.current = card.contentMode;
    mediaRecoveryPostKeyRef.current = null;
    skipNextGallerySaveRef.current = true;
    setCapture({
      post,
      status: "ready",
      message: "Carte chargee depuis la bibliotheque."
    });
    setCardTheme(card.cardTheme);
    setContentMode(resolveCardContentModePreference(post, card.contentMode));
    setActiveView("capture");
    setSelectedGalleryIds(new Set());
    setNotice("Carte chargee depuis la bibliotheque.");
    void recoverMissingVideoMedia(post);
  };

  const handleToggleGallerySelection = (cardId: string) => {
    setSelectedGalleryIds((currentSelection) => {
      const nextSelection = new Set(currentSelection);

      if (nextSelection.has(cardId)) {
        nextSelection.delete(cardId);
      } else {
        nextSelection.add(cardId);
      }

      return nextSelection;
    });
  };

  const handleDeleteSelectedGalleryCards = () => {
    const selectedIds = [...selectedGalleryIds];

    if (selectedIds.length === 0) {
      return;
    }

    const confirmed = window.confirm(`Supprimer ${selectedIds.length} carte${selectedIds.length > 1 ? "s" : ""} sauvegardee${selectedIds.length > 1 ? "s" : ""} ?`);

    if (!confirmed) {
      return;
    }

    void deleteGalleryCards(selectedIds)
      .then((cards) => {
        if (!isMountedRef.current) {
          return;
        }

        setGalleryCards(cards);
        setSelectedGalleryIds(new Set());
        setGalleryNotice(`${selectedIds.length} carte${selectedIds.length > 1 ? "s" : ""} supprimee${selectedIds.length > 1 ? "s" : ""}.`);
      })
      .catch((error) => {
        console.error("[Quoti] gallery delete failed", error);
        if (isMountedRef.current) {
          setGalleryNotice("Les cartes selectionnees n'ont pas pu etre supprimees.");
        }
      });
  };

  const isRecoveringVideoMedia = mediaRecoveryStatus === "loading";
  const isBusy = Boolean(busyAction) || capture.status === "loading" || isRecoveringVideoMedia;
  const isCaptureView = activeView === "capture";
  const isGalleryView = activeView === "gallery";
  const isSettingsView = activeView === "settings";
  const headerTitle = isSettingsView ? "Settings" : isGalleryView ? "Bibliotheque" : "Quoti";
  const headerSubtitle = isSettingsView
    ? "Tune capture and export."
    : isGalleryView
      ? `${galleryCards.length} carte${galleryCards.length === 1 ? "" : "s"} sauvegardee${galleryCards.length === 1 ? "" : "s"}`
      : "Capture the post. Keep the context.";
  const statusNotice =
    notice ||
    (isRecoveringVideoMedia
      ? "Looking for the video. Keep the post visible."
      : videoWarmupStatus === "loading" && capture.post && hasVideoMedia(capture.post)
        ? "Preparing the first video export."
        : "");

  return (
    <main className="popup">
      <header className="popup__header">
        {!isCaptureView ? (
          <div className="popup__brand">
            <button className="popup__settings-button" onClick={handleBackToCapture} type="button" title="Back" aria-label="Back">
              <ArrowLeft size={17} aria-hidden="true" />
            </button>
            <div>
              <h1 className="popup__title">{headerTitle}</h1>
              <p className="popup__subtitle">{headerSubtitle}</p>
            </div>
          </div>
        ) : (
          <div className="popup__brand">
            <img className="popup__logo" src="/icons/quoti-icon.svg" alt="" aria-hidden="true" draggable={false} />
            <div>
              <h1 className="popup__title">{headerTitle}</h1>
              <p className="popup__subtitle">{headerSubtitle}</p>
            </div>
          </div>
        )}
        {isCaptureView ? (
          <div className="popup__header-actions">
            <button className="popup__settings-button" onClick={handleOpenGallery} type="button" title="Open gallery" aria-label="Open gallery">
              <Images size={17} aria-hidden="true" />
            </button>
            <button className="popup__settings-button" onClick={handleOpenOptions} type="button" title="Open settings" aria-label="Open settings">
              <Settings size={17} aria-hidden="true" />
            </button>
          </div>
        ) : null}
      </header>

      {isSettingsView ? (
        <PopupSettings settings={settings} status={settingsStatus} onChange={updateSetting} />
      ) : isGalleryView ? (
        <GalleryView
          cards={galleryCards}
          isLoading={galleryStatus === "loading"}
          notice={galleryNotice}
          onClearSelection={() => setSelectedGalleryIds(new Set())}
          onDeleteSelected={handleDeleteSelectedGalleryCards}
          onOpenCard={handleOpenGalleryCard}
          onQueryChange={setGalleryQuery}
          onToggleSelection={handleToggleGallerySelection}
          query={galleryQuery}
          selectedIds={selectedGalleryIds}
        />
      ) : capture.post ? (
        <div className="popup__content">
          <PostCardPreview post={capture.post} contentMode={contentMode} cardTheme={cardTheme} exportRef={exportRef} />
          <div className="popup__toggles">
            <CardThemeToggle value={cardTheme} onChange={handleCardThemeChange} />
            <CardContentToggle
              disabled={!hasAnyMedia(capture.post)}
              value={contentMode}
              onChange={handleCardContentModeChange}
            />
          </div>
          <PostCardActions
            actionFeedback={actionFeedback}
            busyAction={busyAction}
            canOpenSource={Boolean(capture.post.sourceUrl)}
            downloadProgress={getVideoRenderProgressValue(videoRenderProgress)}
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
        <EmptyState
          isLoading={capture.status === "loading"}
          message={capture.message}
          onRefresh={capturePost}
          title={capture.status === "loading" ? loadingTitle : undefined}
        />
      )}

      {isCaptureView && statusNotice ? <p className="popup__notice" aria-live="polite">{statusNotice}</p> : null}
      {isCaptureView && capture.post && getPrimaryVideo(capture.post) ? (
        <div className="popup__video-export-host" aria-hidden="true">
          <PostCardPreview post={capture.post} contentMode="with-media" cardTheme={cardTheme} exportRef={videoExportRef} />
        </div>
      ) : null}
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

  const label = getFriendlyVideoProgressLabel(progress.stage);
  const value = getVideoRenderProgressValue(progress);

  if (typeof value === "number" && progress.stage !== "ready") {
    return `${label} ${Math.round(value * 100)}%`;
  }

  return label;
}

function getVideoRenderProgressValue(progress: VideoRenderProgress | null): number | undefined {
  if (!progress) {
    return undefined;
  }

  if (Number.isFinite(progress.progress)) {
    return Math.max(0, Math.min(1, progress.progress ?? 0));
  }

  if (progress.stage === "preparing-media") {
    return 0.08;
  }

  if (progress.stage === "loading-renderer") {
    return 0.16;
  }

  if (progress.stage === "finalizing") {
    return 0.96;
  }

  return progress.stage === "ready" ? 1 : undefined;
}

function getFriendlyVideoProgressLabel(stage: VideoRenderProgress["stage"]): string {
  if (stage === "preparing-media") {
    return "Preparing video";
  }

  if (stage === "loading-renderer") {
    return "Starting export";
  }

  if (stage === "rendering" || stage === "fallback-rendering") {
    return "Rendering video";
  }

  if (stage === "finalizing") {
    return "Finishing video";
  }

  return "Video ready";
}

function PopupSettings({
  onChange,
  settings,
  status
}: {
  onChange: <Key extends keyof QuotiSettings>(key: Key, value: QuotiSettings[Key]) => void;
  settings: QuotiSettings;
  status: string;
}) {
  return (
    <section className="popup-settings" aria-label="Quoti settings">
      <div className="popup-settings__group">
        <h2 className="popup-settings__heading">Capture</h2>
        <label className="popup-settings__switch-row">
          <span>
            <span className="popup-settings__label">Remember hovered post</span>
            <span className="popup-settings__description">Use the post under your cursor when Quoti opens.</span>
          </span>
          <input
            className="popup-settings__switch-input"
            checked={settings.hoverCaptureEnabled}
            onChange={(event) => onChange("hoverCaptureEnabled", event.target.checked)}
            type="checkbox"
          />
          <span className="popup-settings__switch-control" aria-hidden="true">
            <span className="popup-settings__switch-thumb" />
          </span>
        </label>
        <label className="popup-settings__switch-row">
          <span>
            <span className="popup-settings__label">Right-click action</span>
            <span className="popup-settings__description">Add a Create Quoti card action to supported posts.</span>
          </span>
          <input
            className="popup-settings__switch-input"
            checked={settings.contextMenuEnabled}
            onChange={(event) => onChange("contextMenuEnabled", event.target.checked)}
            type="checkbox"
          />
          <span className="popup-settings__switch-control" aria-hidden="true">
            <span className="popup-settings__switch-thumb" />
          </span>
        </label>
        <label className="popup-settings__switch-row">
          <span>
            <span className="popup-settings__label">Quoti button in posts</span>
            <span className="popup-settings__description">Show a subtle Quoti button inside supported posts.</span>
          </span>
          <input
            className="popup-settings__switch-input"
            checked={settings.inlineButtonEnabled}
            onChange={(event) => onChange("inlineButtonEnabled", event.target.checked)}
            type="checkbox"
          />
          <span className="popup-settings__switch-control" aria-hidden="true">
            <span className="popup-settings__switch-thumb" />
          </span>
        </label>
      </div>

      <div className="popup-settings__group">
        <h2 className="popup-settings__heading">Video Export</h2>
        <div className="popup-settings__field">
          <span className="popup-settings__label">Exporter</span>
          <div className="popup-settings__segmented" data-selected-index={getVideoRendererIndex(settings.videoRenderer)} role="group" aria-label="Video exporter">
            <span className="popup-settings__segmented-indicator" aria-hidden="true" />
            {[
              ["auto", "Auto"],
              ["native", "Local"],
              ["wasm-ffmpeg", "Extension"]
            ].map(([value, label]) => (
              <button
                className={settings.videoRenderer === value ? "popup-settings__segment popup-settings__segment--active" : "popup-settings__segment"}
                key={value}
                onClick={() => onChange("videoRenderer", value as QuotiSettings["videoRenderer"])}
                type="button"
              >
                {label}
              </button>
            ))}
          </div>
          <ul className="popup-settings__choice-help">
            <li className={settings.videoRenderer === "auto" ? "popup-settings__choice-help-item popup-settings__choice-help-item--active" : "popup-settings__choice-help-item"}>
              <strong>Auto</strong> chooses Local when available, then falls back by itself.
            </li>
            <li className={settings.videoRenderer === "native" ? "popup-settings__choice-help-item popup-settings__choice-help-item--active" : "popup-settings__choice-help-item"}>
              <strong>Local</strong> uses the installed helper on this computer.
            </li>
            <li className={settings.videoRenderer === "wasm-ffmpeg" ? "popup-settings__choice-help-item popup-settings__choice-help-item--active" : "popup-settings__choice-help-item"}>
              <strong>Extension</strong> uses the renderer included with Quoti.
            </li>
          </ul>
        </div>
        <div className="popup-settings__field">
          <span className="popup-settings__label">Quality</span>
          <div className="popup-settings__segmented" data-selected-index={getVideoQualityIndex(settings.videoQuality)} role="group" aria-label="Video quality">
            <span className="popup-settings__segmented-indicator" aria-hidden="true" />
            {[
              ["fast", "Fast"],
              ["balanced", "Balanced"],
              ["high", "High"]
            ].map(([value, label]) => (
              <button
                className={settings.videoQuality === value ? "popup-settings__segment popup-settings__segment--active" : "popup-settings__segment"}
                key={value}
                onClick={() => onChange("videoQuality", value as QuotiSettings["videoQuality"])}
                type="button"
              >
                {label}
              </button>
            ))}
          </div>
          <ul className="popup-settings__choice-help">
            <li className={settings.videoQuality === "fast" ? "popup-settings__choice-help-item popup-settings__choice-help-item--active" : "popup-settings__choice-help-item"}>
              <strong>Fast</strong> exports sooner with a lighter file.
            </li>
            <li className={settings.videoQuality === "balanced" ? "popup-settings__choice-help-item popup-settings__choice-help-item--active" : "popup-settings__choice-help-item"}>
              <strong>Balanced</strong> is the recommended everyday choice.
            </li>
            <li className={settings.videoQuality === "high" ? "popup-settings__choice-help-item popup-settings__choice-help-item--active" : "popup-settings__choice-help-item"}>
              <strong>High</strong> keeps more detail and can take longer.
            </li>
          </ul>
        </div>
      </div>

      <p className="popup-settings__status" aria-live="polite">{status}</p>
    </section>
  );
}

function getVideoRendererIndex(value: QuotiSettings["videoRenderer"]): number {
  if (value === "native") {
    return 1;
  }

  if (value === "wasm-ffmpeg") {
    return 2;
  }

  return 0;
}

function getVideoQualityIndex(value: QuotiSettings["videoQuality"]): number {
  if (value === "balanced") {
    return 1;
  }

  if (value === "high") {
    return 2;
  }

  return 0;
}

function resolveCardContentModePreference(post: ExtractedPost | null, preference: CardContentMode): CardContentMode {
  if (!post) {
    return preference;
  }

  return preference === "with-media" && !hasAnyMedia(post) ? "text-only" : preference;
}

function getPrimaryVideo(post: ExtractedPost) {
  return getAllPostMedia(post).find((media) => media.type === "video");
}

function getExportVideoElement(templateNode: HTMLElement): HTMLVideoElement | null {
  return templateNode.querySelector<HTMLVideoElement>(".context-card__video");
}

function getAllPostMedia(post: ExtractedPost): PostMedia[] {
  return [...post.media, ...(post.relatedPost?.media ?? [])];
}

function hasAnyMedia(post: ExtractedPost): boolean {
  return getAllPostMedia(post).length > 0;
}

function hasVideoMedia(post: ExtractedPost): boolean {
  return getAllPostMedia(post).some((media) => media.type === "video");
}

function hasVideoMediaSource(media: VideoPostMedia): boolean {
  return filterVideoSourceUrlsForMedia(media.posterUrl, [media.url, ...(media.variants ?? [])]).length > 0;
}

function getActionErrorMessage(actionName: string, error: unknown): string {
  const detail = error instanceof Error ? error.message : "";

  if (actionName === "copy-image") {
    return detail || "Quoti could not copy the image. Try Download PNG.";
  }

  if (actionName === "download") {
    return formatNoticeDetail(detail || "Quoti could not prepare the download.");
  }

  if (actionName === "copy-text") {
    return detail || "Quoti could not copy the text.";
  }

  if (actionName === "copy-source") {
    return detail || "Quoti could not copy the source link.";
  }

  return detail || "The action could not be completed.";
}

function formatNoticeDetail(message: string): string {
  const normalized = message.replace(/\s+/g, " ").trim();

  if (/ffmpeg|hls|native|renderer|media source|manifest|playlist/i.test(normalized)) {
    return "Video export failed. Refresh capture and try again, or use Copy image for this post.";
  }

  return normalized.length > 220 ? `${normalized.slice(0, 217)}...` : normalized;
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
    authorAvatarUrl: recoveredPost.authorAvatarUrl ?? basePost.authorAvatarUrl,
    media: mergePostMedia(recoveredPost.media, basePost.media),
    relatedPost: mergeRelatedPostMedia(recoveredPost.relatedPost, basePost.relatedPost)
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
  let changed = false;
  const hydrateMedia = (media: PostMedia[]): PostMedia[] => media.map((item) => {
    if (item.type !== "video") {
      return item;
    }

    const currentUrls = filterVideoSourceUrlsForMedia(item.posterUrl, [item.url, ...(item.variants ?? [])]);
    const observedUrls = filterVideoSourceUrlsForMedia(item.posterUrl, observedVideoUrls);
    const variants = mergeUrlList([...currentUrls, ...observedUrls]);

    if (variants.length === 0) {
      if (!item.url && (!item.variants || item.variants.length === 0)) {
        return item;
      }

      changed = true;

      return {
        ...item,
        url: undefined,
        variants: []
      };
    }

    const nextItem = {
      ...item,
      url: variants[0],
      variants
    };

    changed = changed || nextItem.url !== item.url || variants.length !== (item.variants?.length ?? 0);

    return nextItem;
  });
  const media = hydrateMedia(post.media);
  const relatedMedia = post.relatedPost?.media ? hydrateMedia(post.relatedPost.media) : undefined;

  return changed
    ? {
        ...post,
        media,
        relatedPost: post.relatedPost
          ? {
              ...post.relatedPost,
              media: relatedMedia
            }
          : undefined
      }
    : post;
}

function mergeRelatedPostMedia(recoveredPost: ExtractedPost["relatedPost"], basePost: ExtractedPost["relatedPost"]): ExtractedPost["relatedPost"] {
  if (!recoveredPost && !basePost) {
    return undefined;
  }

  const relatedPost = recoveredPost ?? basePost;

  if (!relatedPost) {
    return undefined;
  }

  return {
    ...relatedPost,
    authorAvatarUrl: recoveredPost?.authorAvatarUrl ?? basePost?.authorAvatarUrl,
    media: mergePostMedia(recoveredPost?.media ?? [], basePost?.media ?? [])
  };
}

function readObservedVideoUrlsForMedia(posterUrl: string | undefined, observedVideoUrls: string[]): string[] {
  return filterVideoSourceUrlsForMedia(posterUrl, observedVideoUrls);
}

function filterVideoSourceUrlsForMedia(posterUrl: string | undefined, sourceUrls: Array<string | undefined>): string[] {
  const mediaId = extractTwitterVideoMediaId(posterUrl);
  const urls = sourceUrls
    .map(normalizeVideoSourceUrl)
    .filter((url): url is string => Boolean(url))
    .filter((url) => !isVideoSegmentUrl(url));

  if (!mediaId) {
    return [...new Set(urls)].sort((a, b) => scoreVideoSourceUrl(b) - scoreVideoSourceUrl(a));
  }

  return [...new Set(urls.filter((url) => isMatchingTwitterVideoSourceId(url, mediaId)))].sort((a, b) => scoreVideoSourceUrl(b) - scoreVideoSourceUrl(a));
}

async function requestSelectedPost(tabId: number, observedVideoUrls: string[]): Promise<QuotiMessageResponse> {
  try {
    return await chrome.tabs.sendMessage(tabId, { type: "QUOTI_GET_SELECTED_POST", observedVideoUrls });
  } catch {
    await ensureContentScript(tabId);
    return chrome.tabs.sendMessage(tabId, { type: "QUOTI_GET_SELECTED_POST", observedVideoUrls });
  }
}

async function hydrateRelatedPost(post: ExtractedPost): Promise<ExtractedPost> {
  if (!post.relatedPost?.sourceUrl || !isChromeExtensionRuntime()) {
    return post;
  }

  try {
    const response = (await chrome.runtime.sendMessage({
      type: "QUOTI_HYDRATE_RELATED_POST",
      post
    })) as QuotiMessageResponse;

    return response.status === "success" ? response.post : post;
  } catch {
    return post;
  }
}

function showRelatedPostHydrationLoader(
  post: ExtractedPost,
  setCapture: Dispatch<SetStateAction<CaptureState>>,
  setLoadingTitle: Dispatch<SetStateAction<string | undefined>>
): boolean {
  if (!post.relatedPost?.sourceUrl || !shouldHydrateRelatedPost(post.relatedPost.content)) {
    return false;
  }

  setLoadingTitle("Loading quoted tweet");
  setCapture((current) => ({
    ...current,
    post: null,
    status: "loading",
    message: "The quoted tweet may be shortened by X. Quoti is opening it in the background to recover the full text before generating the card."
  }));

  return true;
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

function waitForUiFrame(): Promise<void> {
  return new Promise((resolve) => {
    window.requestAnimationFrame(() => {
      window.requestAnimationFrame(() => resolve());
    });
  });
}

function hasMissingVideoUrl(post: ExtractedPost): boolean {
  return getAllPostMedia(post).some((media) => media.type === "video" && !hasVideoMediaSource(media));
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

    if (isLikelyAudioOnlySourceUrl(value)) {
      return -1;
    }

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

function isLikelyAudioOnlySourceUrl(value: string): boolean {
  try {
    const pathname = new URL(value).pathname.toLowerCase();

    return /(?:^|\/)(?:audio|aud|mp4a|aac)(?:[./_-]|\/|$)/.test(pathname);
  } catch {
    return false;
  }
}

function isSupportedUrl(url: string | undefined): boolean {
  if (!url) {
    return false;
  }

  try {
    const hostname = new URL(url).hostname.toLowerCase();

    return isSupportedSocialHostname(hostname);
  } catch {
    return false;
  }
}

function isSupportedSocialHostname(hostname: string): boolean {
  return (
    hostname === "x.com" ||
    hostname.endsWith(".x.com") ||
    hostname === "twitter.com" ||
    hostname.endsWith(".twitter.com") ||
    hostname === "threads.net" ||
    hostname.endsWith(".threads.net") ||
    hostname === "threads.com" ||
    hostname.endsWith(".threads.com") ||
    hostname === "linkedin.com" ||
    hostname.endsWith(".linkedin.com") ||
    hostname === "facebook.com" ||
    hostname.endsWith(".facebook.com") ||
    hostname === "fb.watch"
  );
}

function isChromeExtensionRuntime(): boolean {
  return typeof chrome !== "undefined" && Boolean(chrome.tabs?.query);
}

function wait(duration: number): Promise<void> {
  return new Promise((resolve) => window.setTimeout(resolve, duration));
}

function preserveSessionMedia(cache: Map<string, CachedPostMedia>, post: ExtractedPost): ExtractedPost {
  const cacheKey = getPostCacheKey(post);

  if (!cacheKey) {
    return post;
  }

  const cached = cache.get(cacheKey);
  const hydratedPost = cached
    ? {
        ...post,
        media: mergePostMedia(post.media, cached.media),
        relatedPost: post.relatedPost
          ? {
              ...post.relatedPost,
              media: mergePostMedia(post.relatedPost.media ?? [], cached.relatedMedia ?? [])
            }
          : post.relatedPost
      }
    : post;

  cache.set(cacheKey, {
    media: hydratedPost.media,
    relatedMedia: hydratedPost.relatedPost?.media
  });

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
      const posterUrl = media.posterUrl ?? cached.posterUrl;
      const variants = filterVideoSourceUrlsForMedia(posterUrl, [media.url, ...(media.variants ?? []), cached.url, ...(cached.variants ?? [])]);

      return {
        ...media,
        alt: media.alt ?? cached.alt,
        duration: media.duration ?? cached.duration,
        posterUrl,
        url: variants[0],
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
          (media.posterUrl && cached.posterUrl === media.posterUrl)
      );
    }

    return false;
  });

  if (media.type === "video") {
    return exactMatch;
  }

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
