import type { VideoRenderMediaFile, VideoRenderMediaSource } from "../video-render.types";
import { VideoRenderError } from "../video-render.types";

type ResolveHlsOptions = {
  onFileLoaded?: (loaded: number, total: number) => void;
  signal?: AbortSignal;
};

type ResolvedHlsPlaylists = {
  audioPlaylistUrl?: string;
  videoPlaylistUrl: string;
};

type HlsVariant = {
  audioGroupId?: string;
  score: number;
  url: string;
};

type HlsMediaEntry = {
  groupId?: string;
  isDefault: boolean;
  type?: string;
  url?: string;
};

type DownloadPlan = {
  localName: string;
  url: string;
};

const textEncoder = new TextEncoder();

export function isHlsMediaUrl(source: string): boolean {
  try {
    return new URL(source).pathname.toLowerCase().endsWith(".m3u8");
  } catch {
    return false;
  }
}

export async function resolveHlsMediaSource(sourceUrl: string, options: ResolveHlsOptions = {}): Promise<VideoRenderMediaSource> {
  const masterPlaylist = await fetchText(sourceUrl, options.signal);
  const playlists = resolveHlsPlaylists(sourceUrl, masterPlaylist);
  const videoPlaylist =
    playlists.videoPlaylistUrl === sourceUrl ? masterPlaylist : await fetchText(playlists.videoPlaylistUrl, options.signal);
  const audioPlaylist =
    playlists.audioPlaylistUrl && playlists.audioPlaylistUrl !== playlists.videoPlaylistUrl
      ? await fetchText(playlists.audioPlaylistUrl, options.signal)
      : null;
  const files: VideoRenderMediaFile[] = [];
  const totalFiles = countMediaFiles(videoPlaylist) + (audioPlaylist ? countMediaFiles(audioPlaylist) : 0);
  let loadedFiles = 0;
  const handleFileLoaded = (): void => {
    loadedFiles += 1;
    options.onFileLoaded?.(loadedFiles, totalFiles);
  };
  const videoInputPath = await materializeMediaPlaylist({
    files,
    onFileLoaded: handleFileLoaded,
    playlist: videoPlaylist,
    playlistUrl: playlists.videoPlaylistUrl,
    prefix: "video",
    signal: options.signal
  });
  const audioInputPath = audioPlaylist
    ? await materializeMediaPlaylist({
        files,
        onFileLoaded: handleFileLoaded,
        playlist: audioPlaylist,
        playlistUrl: playlists.audioPlaylistUrl as string,
        prefix: "audio",
        signal: options.signal
      })
    : undefined;

  return {
    audioInputPath,
    files,
    kind: "hls",
    sourceUrl,
    videoInputPath
  };
}

function resolveHlsPlaylists(playlistUrl: string, playlist: string): ResolvedHlsPlaylists {
  if (isMediaPlaylist(playlist)) {
    return {
      videoPlaylistUrl: playlistUrl
    };
  }

  const lines = playlist.split("\n").map((line) => line.trim());
  const mediaEntries = lines
    .filter((line) => line.startsWith("#EXT-X-MEDIA:"))
    .map(parseMediaEntry)
    .filter((entry): entry is HlsMediaEntry => Boolean(entry));
  const variants = lines
    .map((line, index): HlsVariant | null => {
      if (!line || line.startsWith("#") || !isHlsPlaylistReference(line, playlistUrl)) {
        return null;
      }

      const streamInfo = lines[index - 1] ?? "";
      const attributes = streamInfo.startsWith("#EXT-X-STREAM-INF:") ? parseHlsAttributes(streamInfo) : {};

      return {
        audioGroupId: attributes.AUDIO,
        score: scoreHlsVariant(streamInfo),
        url: new URL(line, playlistUrl).toString()
      };
    })
    .filter((variant): variant is HlsVariant => Boolean(variant))
    .sort((a, b) => b.score - a.score);
  const selectedVariant = variants[0];

  if (!selectedVariant) {
    throw new VideoRenderError("MEDIA_SOURCE_UNAVAILABLE", "The HLS playlist does not contain a playable video variant.");
  }

  const audioEntry = selectAudioEntry(mediaEntries, selectedVariant.audioGroupId);

  return {
    audioPlaylistUrl: audioEntry?.url ? new URL(audioEntry.url, playlistUrl).toString() : undefined,
    videoPlaylistUrl: selectedVariant.url
  };
}

function parseMediaEntry(line: string): HlsMediaEntry | null {
  const attributes = parseHlsAttributes(line);

  if (attributes.TYPE?.toUpperCase() !== "AUDIO" || !attributes.URI) {
    return null;
  }

  return {
    groupId: attributes["GROUP-ID"],
    isDefault: attributes.DEFAULT?.toUpperCase() === "YES",
    type: attributes.TYPE,
    url: attributes.URI
  };
}

function selectAudioEntry(entries: HlsMediaEntry[], groupId: string | undefined): HlsMediaEntry | undefined {
  const matchingGroup = entries.filter((entry) => !groupId || entry.groupId === groupId);

  return matchingGroup.find((entry) => entry.isDefault) ?? matchingGroup[0] ?? entries.find((entry) => entry.isDefault) ?? entries[0];
}

function parseHlsAttributes(line: string): Record<string, string> {
  const attributes: Record<string, string> = {};
  const colonIndex = line.indexOf(":");
  const attributeText = colonIndex >= 0 ? line.slice(colonIndex + 1) : line;
  const matches = attributeText.matchAll(/([A-Z0-9-]+)=("[^"]*"|[^,]*)/gi);

  for (const match of matches) {
    attributes[match[1].toUpperCase()] = match[2].startsWith("\"") ? match[2].slice(1, -1) : match[2];
  }

  return attributes;
}

function isHlsPlaylistReference(line: string, playlistUrl: string): boolean {
  try {
    return new URL(line, playlistUrl).pathname.toLowerCase().endsWith(".m3u8");
  } catch {
    return false;
  }
}

function scoreHlsVariant(streamInfo: string): number {
  const resolution = /RESOLUTION=(\d+)x(\d+)/i.exec(streamInfo);
  const bandwidth = /(?:AVERAGE-)?BANDWIDTH=(\d+)/i.exec(streamInfo);

  return (resolution ? Number(resolution[1]) * Number(resolution[2]) : 0) + (bandwidth ? Number(bandwidth[1]) / 100 : 0);
}

function isMediaPlaylist(playlist: string): boolean {
  return /#EXTINF:/i.test(playlist) || /#EXT-X-MAP:/i.test(playlist);
}

function countMediaFiles(playlist: string): number {
  return playlist
    .split("\n")
    .map((line) => line.trim())
    .filter((line) => Boolean(line) && (!line.startsWith("#") || line.startsWith("#EXT-X-MAP:"))).length;
}

async function materializeMediaPlaylist({
  files,
  onFileLoaded,
  playlist,
  playlistUrl,
  prefix,
  signal
}: {
  files: VideoRenderMediaFile[];
  onFileLoaded: () => void;
  playlist: string;
  playlistUrl: string;
  prefix: string;
  signal?: AbortSignal;
}): Promise<string> {
  const plans: DownloadPlan[] = [];
  let segmentIndex = 0;
  const rewrittenLines = playlist.split("\n").map((rawLine) => {
    const line = rawLine.trim();

    if (!line) {
      return rawLine;
    }

    if (line.startsWith("#EXT-X-MAP:")) {
      const initUrl = /URI="([^"]+)"/.exec(line)?.[1];

      if (!initUrl) {
        return rawLine;
      }

      const localName = `${prefix}-init${getUrlExtension(initUrl, ".mp4")}`;
      plans.push({
        localName,
        url: new URL(initUrl, playlistUrl).toString()
      });

      return rawLine.replace(/URI="([^"]+)"/, `URI="${localName}"`);
    }

    if (line.startsWith("#")) {
      return rawLine;
    }

    const localName = `${prefix}-segment-${String(segmentIndex).padStart(5, "0")}${getUrlExtension(line, ".m4s")}`;
    segmentIndex += 1;
    plans.push({
      localName,
      url: new URL(line, playlistUrl).toString()
    });

    return rawLine.replace(line, localName);
  });
  const playlistPath = `${prefix}.m3u8`;

  for (const plan of plans) {
    files.push({
      data: await fetchBytes(plan.url, signal),
      path: plan.localName
    });
    onFileLoaded();
  }

  files.push({
    data: textEncoder.encode(rewrittenLines.join("\n")),
    path: playlistPath
  });

  return playlistPath;
}

async function fetchText(url: string, signal: AbortSignal | undefined): Promise<string> {
  const response = await fetch(url, {
    credentials: "omit",
    referrerPolicy: "no-referrer",
    signal
  });

  if (!response.ok) {
    throw new VideoRenderError("MEDIA_SOURCE_UNAVAILABLE", `HLS request failed with HTTP ${response.status}.`);
  }

  return response.text();
}

async function fetchBytes(url: string, signal: AbortSignal | undefined): Promise<Uint8Array> {
  const response = await fetch(url, {
    credentials: "omit",
    referrerPolicy: "no-referrer",
    signal
  });

  if (!response.ok) {
    throw new VideoRenderError("MEDIA_SOURCE_UNAVAILABLE", `HLS segment request failed with HTTP ${response.status}.`);
  }

  return new Uint8Array(await response.arrayBuffer());
}

function getUrlExtension(value: string, fallback: string): string {
  try {
    const extension = /\.[a-z0-9]+$/i.exec(new URL(value, "https://example.invalid").pathname)?.[0];

    return extension ?? fallback;
  } catch {
    return fallback;
  }
}
