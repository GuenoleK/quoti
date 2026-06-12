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

  const updateSetting = (key: keyof QuotiSettings, value: boolean) => {
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
          <span className="options-page__logo" aria-hidden="true">Q</span>
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
        </div>

        <p className="options-page__status" aria-live="polite">{status}</p>
      </section>
    </main>
  );
}
