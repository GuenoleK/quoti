import { Copy, Download, ExternalLink, ImageDown, RefreshCw } from "lucide-react";
import "./PostCardActions.css";

type PostCardActionsProps = {
  actionFeedback: {
    action: string;
    status: "success" | "error";
  } | null;
  busyAction: string | null;
  canOpenSource: boolean;
  isBusy: boolean;
  onCopyImage: () => void;
  onCopyText: () => void;
  onDownload: () => void;
  onOpenSource: () => void;
  onRefresh: () => void;
};

export function PostCardActions({
  actionFeedback,
  busyAction,
  canOpenSource,
  isBusy,
  onCopyImage,
  onCopyText,
  onDownload,
  onOpenSource,
  onRefresh
}: PostCardActionsProps) {
  const copyImageLabel = getActionLabel("copy-image", busyAction, actionFeedback, "Copy image");
  const downloadLabel = getActionLabel("download", busyAction, actionFeedback, "Download JPG");
  const copyTextLabel = getActionLabel("copy-text", busyAction, actionFeedback, "Copy text");

  return (
    <div className="post-card-actions" aria-label="Context card actions">
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

      <button
        className="post-card-actions__button post-card-actions__button--split"
        disabled={isBusy}
        onClick={onDownload}
        type="button"
      >
        <Download size={16} aria-hidden="true" />
        <span>{downloadLabel}</span>
      </button>

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
          className="post-card-actions__button post-card-actions__button--source"
          disabled={isBusy || !canOpenSource}
          onClick={onOpenSource}
          type="button"
          title="Open source"
        >
          <ExternalLink size={16} aria-hidden="true" />
          <span>Source</span>
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
  fallback: string
): string {
  if (busyAction === action) {
    return action === "copy-image" ? "Copying..." : action === "download" ? "Preparing..." : "Copying...";
  }

  if (feedback?.action === action && feedback.status === "success") {
    return action === "copy-image" ? "Image copied" : action === "download" ? "JPG ready" : "Text copied";
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
