import { MousePointer2 } from "lucide-react";
import "./EmptyState.css";

type EmptyStateProps = {
  message: string;
  onRefresh: () => void;
};

export function EmptyState({ message, onRefresh }: EmptyStateProps) {
  return (
    <section className="empty-state" aria-live="polite">
      <div className="empty-state__icon">
        <MousePointer2 size={22} aria-hidden="true" />
      </div>
      <div className="empty-state__copy">
        <h2 className="empty-state__title">No post captured</h2>
        <p className="empty-state__message">{message}</p>
      </div>
      <button className="empty-state__button" onClick={onRefresh} type="button">
        Try again
      </button>
    </section>
  );
}
