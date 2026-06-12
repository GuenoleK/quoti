export type QuotiSettings = {
  hoverCaptureEnabled: boolean;
  contextMenuEnabled: boolean;
  inlineButtonEnabled: boolean;
};

export const quotiSettingsStorageKey = "quoti-settings";
export const latestPostStorageKey = "quoti-latest-post";

export const defaultQuotiSettings: QuotiSettings = {
  hoverCaptureEnabled: true,
  contextMenuEnabled: true,
  inlineButtonEnabled: false
};

export async function readQuotiSettings(): Promise<QuotiSettings> {
  const stored = await chrome.storage.sync.get(quotiSettingsStorageKey);
  const settings = stored[quotiSettingsStorageKey] as Partial<QuotiSettings> | undefined;

  return {
    ...defaultQuotiSettings,
    ...settings
  };
}

export async function writeQuotiSettings(settings: QuotiSettings): Promise<void> {
  await chrome.storage.sync.set({
    [quotiSettingsStorageKey]: settings
  });
}
