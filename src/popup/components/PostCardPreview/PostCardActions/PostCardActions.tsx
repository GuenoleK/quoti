import { Copy, Download, ExternalLink, ImageDown, RefreshCw } from "lucide-react";
import "./PostCardActions.css";

type PostCardActionsProps = {
  canOpenSource: boolean;
  isBusy: boolean;
  onCopyImage: () => void;
  onCopyText: () => void;
  onDownload: () => void;
  onOpenSource: () => void;
  onRefresh: () => void;
};

export function PostCardActions({
  canOpenSource,
  isBusy,
  onCopyImage,
  onCopyText,
  onDownload,
  onOpenSource,
  onRefresh
}: PostCardActionsProps) {
  return (
    <div className="post-card-actions" aria-label="Context card actions">
      <button className="post-card-actions__button post-card-actions__button--primary" disabled={isBusy} onClick={onCopyImage} type="button" title="Copy image">
        <ImageDown size={16} aria-hidden="true" />
        <span>Copy image</span>
      </button>

      <button
        className="post-card-actions__button post-card-actions__button--split"
        disabled={isBusy}
        onClick={onDownload}
        type="button"
      >
        <Download size={16} aria-hidden="true" />
        <span>Download JPG</span>
      </button>

      <button
        className="post-card-actions__button post-card-actions__button--split"
        disabled={isBusy}
        onClick={onCopyText}
        type="button"
        title="Copy text"
      >
        <Copy size={16} aria-hidden="true" />
        <span>Copy text</span>
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
