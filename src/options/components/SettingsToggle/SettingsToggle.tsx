import "./SettingsToggle.css";

type SettingsToggleProps = {
  checked: boolean;
  description: string;
  label: string;
  onChange: (checked: boolean) => void;
};

export function SettingsToggle({ checked, description, label, onChange }: SettingsToggleProps) {
  return (
    <label className="settings-toggle">
      <span className="settings-toggle__copy">
        <span className="settings-toggle__label">{label}</span>
        <span className="settings-toggle__description">{description}</span>
      </span>
      <input className="settings-toggle__input" checked={checked} onChange={(event) => onChange(event.target.checked)} type="checkbox" />
      <span className="settings-toggle__control" aria-hidden="true">
        <span className="settings-toggle__thumb" />
      </span>
    </label>
  );
}
