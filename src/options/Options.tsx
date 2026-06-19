import { useEffect, useState } from "react";
import { defaultQuotiSettings, readQuotiSettings, writeQuotiSettings, type QuotiSettings } from "../shared/settings/quoti-settings";
import { SettingsToggle } from "./components/SettingsToggle/SettingsToggle";
import "./Options.css";

export function Options() {
  const [settings, setSettings] = useState<QuotiSettings>(defaultQuotiSettings);
  const [status, setStatus] = useState("Saved automatically.");

  useEffect(() => {
    void readQuotiSettings().then(setSettings);
  }, []);

  const updateSetting = <Key extends keyof QuotiSettings>(key: Key, value: QuotiSettings[Key]) => {
    const nextSettings = {
      ...settings,
      [key]: value
    };

    setSettings(nextSettings);
    setStatus("Saving...");

    void writeQuotiSettings(nextSettings).then(() => {
      chrome.runtime.sendMessage({ type: "QUOTI_SETTINGS_UPDATED" });
      setStatus("Saved automatically.");
    });
  };

  return (
    <main className="options-page">
      <section className="options-page__panel">
        <header className="options-page__header">
          <img className="options-page__logo" src="/icons/quoti-icon.svg" alt="" aria-hidden="true" draggable={false} />
          <div>
            <h1 className="options-page__title">Quoti Options</h1>
            <p className="options-page__subtitle">Choose how Quoti appears while you browse social posts.</p>
          </div>
        </header>

        <div className="options-page__settings">
          <SettingsToggle
            checked={settings.hoverCaptureEnabled}
            description="Quoti remembers the post under your cursor before you open the popup."
            label="Remember hovered post"
            onChange={(checked) => updateSetting("hoverCaptureEnabled", checked)}
          />
          <SettingsToggle
            checked={settings.contextMenuEnabled}
            description="Right-click a post and choose Create Quoti card."
            label="Add right-click action"
            onChange={(checked) => updateSetting("contextMenuEnabled", checked)}
          />
          <SettingsToggle
            checked={settings.inlineButtonEnabled}
            description="Show a subtle Quoti button inside supported posts."
            label="Show Quoti button in posts"
            onChange={(checked) => updateSetting("inlineButtonEnabled", checked)}
          />
          <div className="options-page__field">
            <span className="options-page__field-label">Video exporter</span>
            <div className="options-page__segmented" data-selected-index={getVideoRendererIndex(settings.videoRenderer)} role="group" aria-label="Video exporter">
              <span className="options-page__segmented-indicator" aria-hidden="true" />
              {[
                ["auto", "Auto"],
                ["native", "Local"],
                ["wasm-ffmpeg", "Extension"]
              ].map(([value, label]) => (
                <button
                  className={settings.videoRenderer === value ? "options-page__segment options-page__segment--active" : "options-page__segment"}
                  key={value}
                  onClick={() => updateSetting("videoRenderer", value as QuotiSettings["videoRenderer"])}
                  type="button"
                >
                  {label}
                </button>
              ))}
            </div>
          </div>
          <div className="options-page__field">
            <span className="options-page__field-label">Video quality</span>
            <div className="options-page__segmented" data-selected-index={getVideoQualityIndex(settings.videoQuality)} role="group" aria-label="Video quality">
              <span className="options-page__segmented-indicator" aria-hidden="true" />
              {[
                ["fast", "Fast"],
                ["balanced", "Balanced"],
                ["high", "High"]
              ].map(([value, label]) => (
                <button
                  className={settings.videoQuality === value ? "options-page__segment options-page__segment--active" : "options-page__segment"}
                  key={value}
                  onClick={() => updateSetting("videoQuality", value as QuotiSettings["videoQuality"])}
                  type="button"
                >
                  {label}
                </button>
              ))}
            </div>
          </div>
        </div>

        <p className="options-page__status" aria-live="polite">{status}</p>
      </section>
    </main>
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
