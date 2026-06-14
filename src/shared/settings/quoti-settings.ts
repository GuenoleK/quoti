import type { CardContentMode, CardTheme } from "../types/post.types";

export type VideoQualitySetting = "fast" | "balanced" | "high";
export type VideoRendererSetting = "auto" | "native" | "wasm-ffmpeg";

export type QuotiSettings = {
  cardContentMode: CardContentMode;
  cardTheme: CardTheme;
  hoverCaptureEnabled: boolean;
  contextMenuEnabled: boolean;
  inlineButtonEnabled: boolean;
  videoQuality: VideoQualitySetting;
  videoRenderer: VideoRendererSetting;
};

type StoredQuotiSettings = Partial<QuotiSettings> & {
  inlineButtonPreferenceVersion?: number;
};

export const quotiSettingsStorageKey = "quoti-settings";
export const latestPostStorageKey = "quoti-latest-post";
const inlineButtonPreferenceVersion = 1;

export const defaultQuotiSettings: QuotiSettings = {
  cardContentMode: "with-media",
  cardTheme: "light",
  hoverCaptureEnabled: true,
  contextMenuEnabled: true,
  inlineButtonEnabled: true,
  videoQuality: "balanced",
  videoRenderer: "auto"
};

export async function readQuotiSettings(): Promise<QuotiSettings> {
  if (typeof chrome === "undefined" || !chrome.storage?.sync) {
    return defaultQuotiSettings;
  }

  const stored = await chrome.storage.sync.get(quotiSettingsStorageKey);
  const settings = stored[quotiSettingsStorageKey] as StoredQuotiSettings | undefined;

  return normalizeQuotiSettings(
    {
      ...defaultQuotiSettings,
      ...settings
    },
    settings?.inlineButtonPreferenceVersion
  );
}

export async function writeQuotiSettings(settings: QuotiSettings): Promise<void> {
  if (typeof chrome === "undefined" || !chrome.storage?.sync) {
    return;
  }

  await chrome.storage.sync.set({
    [quotiSettingsStorageKey]: {
      ...normalizeQuotiSettings(settings, inlineButtonPreferenceVersion),
      inlineButtonPreferenceVersion
    }
  });
}

function normalizeQuotiSettings(settings: QuotiSettings, storedInlineButtonPreferenceVersion?: number): QuotiSettings {
  return {
    ...settings,
    cardContentMode: isCardContentMode(settings.cardContentMode) ? settings.cardContentMode : defaultQuotiSettings.cardContentMode,
    cardTheme: isCardTheme(settings.cardTheme) ? settings.cardTheme : defaultQuotiSettings.cardTheme,
    inlineButtonEnabled: storedInlineButtonPreferenceVersion === inlineButtonPreferenceVersion ? Boolean(settings.inlineButtonEnabled) : defaultQuotiSettings.inlineButtonEnabled,
    videoQuality: isVideoQualitySetting(settings.videoQuality) ? settings.videoQuality : defaultQuotiSettings.videoQuality,
    videoRenderer: isVideoRendererSetting(settings.videoRenderer) ? settings.videoRenderer : defaultQuotiSettings.videoRenderer
  };
}

function isCardContentMode(value: unknown): value is CardContentMode {
  return value === "text-only" || value === "with-media";
}

function isCardTheme(value: unknown): value is CardTheme {
  return value === "light" || value === "dark";
}

function isVideoQualitySetting(value: unknown): value is VideoQualitySetting {
  return value === "fast" || value === "balanced" || value === "high";
}

function isVideoRendererSetting(value: unknown): value is VideoRendererSetting {
  return value === "auto" || value === "native" || value === "wasm-ffmpeg";
}
