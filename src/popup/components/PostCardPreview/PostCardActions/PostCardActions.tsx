import { Copy, Download, ExternalLink, ImageDown, RefreshCw, Video } from "lucide-react";
import "./PostCardActions.css";

type PostCardActionsProps = {
  actionFeedback: {
    action: string;
    status: "success" | "error";
  } | null;
  busyAction: string | null;
  canOpenSource: boolean;
  downloadProgressLabel?: string;
  downloadMode: "image" | "video";
  isBusy: boolean;
  onCopyImage: () => void;
  onCopySource: () => void;
  onCopyText: () => void;
  onDownload: () => void;
  onOpenSource: () => void;
  onRefresh: () => void;
};

export function PostCardActions({
  actionFeedback,
  busyAction,
  canOpenSource,
  downloadProgressLabel,
  downloadMode,
  isBusy,
  onCopyImage,
  onCopySource,
  onCopyText,
  onDownload,
  onOpenSource,
  onRefresh
}: PostCardActionsProps) {
  const copyImageLabel = getActionLabel("copy-image", busyAction, actionFeedback, "Copy image");
  const copySourceLabel = getActionLabel("copy-source", busyAction, actionFeedback, "Copy source");
  const downloadLabel = getActionLabel(
    "download",
    busyAction,
    actionFeedback,
    downloadMode === "video" ? "Download video" : "Download JPG",
    downloadMode,
    downloadProgressLabel
  );
  const copyTextLabel = getActionLabel("copy-text", busyAction, actionFeedback, "Copy text");
  const isVideoDownload = downloadMode === "video";

  return (
    <div className="post-card-actions" aria-label="Context card actions">
      {isVideoDownload ? (
        <button
          className={`post-card-actions__button post-card-actions__button--primary${getActionClassName("download", actionFeedback)}`}
          disabled={isBusy}
          onClick={onDownload}
          type="button"
          title="Download video"
        >
          <Video size={16} aria-hidden="true" />
          <span>{downloadLabel}</span>
        </button>
      ) : (
        <button
          className={`post-card-actions__button post-card-actions__button--primary${getActionClassName("copy-image", actionFeedback)}`}
          disabled={isBusy}
          onClick={onCopyImage}
          type="button"
          title="Copy image"
        >
          <ImageDown size={16} aria-hidden="true" />
          <span>{copyImageLabel}</span>
        </button>
      )}

      {isVideoDownload ? (
        <button
          className={`post-card-actions__button post-card-actions__button--split${getActionClassName("copy-image", actionFeedback)}`}
          disabled={isBusy}
          onClick={onCopyImage}
          type="button"
          title="Copy image"
        >
          <ImageDown size={16} aria-hidden="true" />
          <span>{copyImageLabel}</span>
        </button>
      ) : (
        <button
          className="post-card-actions__button post-card-actions__button--split"
          disabled={isBusy}
          onClick={onDownload}
          type="button"
        >
          <Download size={16} aria-hidden="true" />
          <span>{downloadLabel}</span>
        </button>
      )}

      <button
        className="post-card-actions__button post-card-actions__button--split"
        disabled={isBusy}
        onClick={onCopyText}
        type="button"
        title="Copy text"
      >
        <Copy size={16} aria-hidden="true" />
        <span>{copyTextLabel}</span>
      </button>

      <div className="post-card-actions__source-row">
        <button
          className={`post-card-actions__button post-card-actions__button--source${getActionClassName("copy-source", actionFeedback)}`}
          disabled={isBusy || !canOpenSource}
          onClick={onCopySource}
          type="button"
          title="Copy source link"
        >
          <Copy size={16} aria-hidden="true" />
          <span>{copySourceLabel}</span>
        </button>

        <button
          className="post-card-actions__icon-button"
          disabled={isBusy || !canOpenSource}
          onClick={onOpenSource}
          type="button"
          title="Open source"
          aria-label="Open source"
        >
          <ExternalLink size={16} aria-hidden="true" />
        </button>

        <button
          className="post-card-actions__icon-button"
          disabled={isBusy}
          onClick={onRefresh}
          type="button"
          title="Capture visible post again"
          aria-label="Refresh capture"
        >
          <RefreshCw size={16} aria-hidden="true" />
        </button>
      </div>
    </div>
  );
}

function getActionLabel(
  action: string,
  busyAction: string | null,
  feedback: PostCardActionsProps["actionFeedback"],
  fallback: string,
  downloadMode: PostCardActionsProps["downloadMode"] = "image",
  progressLabel?: string
): string {
  if (busyAction === action) {
    return action === "download" ? progressLabel ?? "Preparing..." : "Copying...";
  }

  if (feedback?.action === action && feedback.status === "success") {
    if (action === "copy-image") {
      return "Image copied";
    }

    if (action === "copy-source") {
      return "Source copied";
    }

    return action === "download" ? (downloadMode === "video" ? "Video ready" : "JPG ready") : "Text copied";
  }

  if (feedback?.action === action && feedback.status === "error") {
    return action === "copy-image" ? "Copy failed" : action === "download" ? "Download failed" : "Copy failed";
  }

  return fallback;
}

function getActionClassName(action: string, feedback: PostCardActionsProps["actionFeedback"]): string {
  if (feedback?.action !== action) {
    return "";
  }

  return feedback.status === "success" ? " post-card-actions__button--success" : " post-card-actions__button--error";
}
