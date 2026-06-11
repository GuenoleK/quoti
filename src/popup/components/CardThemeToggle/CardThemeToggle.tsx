import type { CardTheme } from "../../../shared/types/post.types";
import "./CardThemeToggle.css";

type CardThemeToggleProps = {
  value: CardTheme;
  onChange: (theme: CardTheme) => void;
};

export function CardThemeToggle({ value, onChange }: CardThemeToggleProps) {
  return (
    <div className="card-theme-toggle" data-card-theme-value={value} aria-label="Card theme">
      <span className="card-theme-toggle__indicator" aria-hidden="true" />
      <button
        className={`card-theme-toggle__button${value === "light" ? " card-theme-toggle__button--active" : ""}`}
        aria-pressed={value === "light"}
        onClick={() => onChange("light")}
        type="button"
      >
        Light
      </button>
      <button
        className={`card-theme-toggle__button${value === "dark" ? " card-theme-toggle__button--active" : ""}`}
        aria-pressed={value === "dark"}
        onClick={() => onChange("dark")}
        type="button"
      >
        Dark
      </button>
    </div>
  );
}
