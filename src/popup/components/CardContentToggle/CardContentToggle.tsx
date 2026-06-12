import type { CardContentMode } from "../../../shared/types/post.types";
import "./CardContentToggle.css";

type CardContentToggleProps = {
  disabled?: boolean;
  value: CardContentMode;
  onChange: (mode: CardContentMode) => void;
};

export function CardContentToggle({ disabled = false, value, onChange }: CardContentToggleProps) {
  return (
    <div className="card-content-toggle" data-card-content-value={value} aria-label="Card content">
      <span className="card-content-toggle__indicator" aria-hidden="true" />
      <button
        aria-pressed={value === "text-only"}
        className={`card-content-toggle__button${value === "text-only" ? " card-content-toggle__button--active" : ""}`}
        onClick={() => onChange("text-only")}
        type="button"
      >
        Text only
      </button>
      <button
        aria-pressed={value === "with-media"}
        className={`card-content-toggle__button${value === "with-media" ? " card-content-toggle__button--active" : ""}`}
        disabled={disabled}
        onClick={() => onChange("with-media")}
        type="button"
      >
        With image
      </button>
    </div>
  );
}
