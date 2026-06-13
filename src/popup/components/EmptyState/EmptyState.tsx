import { Loader2, MousePointer2 } from "lucide-react";
import "./EmptyState.css";

type EmptyStateProps = {
  isLoading?: boolean;
  message: string;
  onRefresh: () => void;
};

export function EmptyState({ isLoading = false, message, onRefresh }: EmptyStateProps) {
  return (
    <section className="empty-state" aria-live="polite">
      <div className={`empty-state__icon${isLoading ? " empty-state__icon--loading" : ""}`}>
        {isLoading ? <Loader2 size={22} aria-hidden="true" /> : <MousePointer2 size={22} aria-hidden="true" />}
      </div>
      <div className="empty-state__copy">
        <h2 className="empty-state__title">{isLoading ? "Preparing Quoti" : "No post captured"}</h2>
        <p className="empty-state__message">{message}</p>
      </div>
      <button className="empty-state__button" disabled={isLoading} onClick={onRefresh} type="button">
        Try again
      </button>
    </section>
  );
}
