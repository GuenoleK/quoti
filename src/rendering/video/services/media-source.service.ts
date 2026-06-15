import type { ExtractedPost, PostMedia, VideoPostMedia } from "../../../shared/types/post.types";
import type { VideoRenderMediaSource, VideoRenderProgress, VideoRenderSourceCandidate } from "../video-render.types";
import { VideoRenderError } from "../video-render.types";
import { isHlsMediaUrl, resolveHlsMediaSource } from "../adapters/hls-media.adapter";

type ResolveMediaSourceOptions = {
  onProgress?: (progress: VideoRenderProgress) => void;
  signal?: AbortSignal;
};

export async function resolveVideoMediaSource(post: ExtractedPost, options: ResolveMediaSourceOptions = {}): Promise<VideoRenderMediaSource> {
  const candidates = getVideoMediaSourceCandidates(post);
  const errors: unknown[] = [];

  for (const candidate of candidates) {
    try {
      return await resolveVideoMediaSourceCandidate(candidate, options);
    } catch (error) {
      if (isAbortError(error)) {
        throw new VideoRenderError("ABORTED", "Video rendering was interrupted.", error);
      }

      errors.push(error);
    }
  }

  throw new VideoRenderError("MEDIA_SOURCE_UNAVAILABLE", "Quoti could not resolve a playable video source.", errors);
}

export function getVideoMediaSourceCandidates(post: ExtractedPost): VideoRenderSourceCandidate[] {
  const media = getAllPostMedia(post).find((item): item is VideoPostMedia => item.type === "video");

  if (!media) {
    throw new VideoRenderError("MEDIA_SOURCE_UNAVAILABLE", "No video media was found in this post.");
  }

  const candidates = getVideoSourceCandidates(media);

  if (candidates.length === 0) {
    throw new VideoRenderError("MEDIA_SOURCE_UNAVAILABLE", "No playable video URL was found.");
  }

  return candidates;
}

function getAllPostMedia(post: ExtractedPost): PostMedia[] {
  return [...post.media, ...(post.relatedPost?.media ?? [])];
}

export async function resolveVideoMediaSourceCandidate(candidate: VideoRenderSourceCandidate, options: ResolveMediaSourceOptions = {}): Promise<VideoRenderMediaSource> {
  if (isHlsMediaUrl(candidate.url)) {
    options.onProgress?.({
      stage: "preparing-media",
      message: "Preparing HLS media",
      progress: 0.12
    });

    return resolveHlsMediaSource(candidate.url, {
      onFileLoaded: (loaded, total) => {
        options.onProgress?.({
          stage: "preparing-media",
          message: "Preparing HLS media",
          progress: total > 0 ? 0.12 + (loaded / total) * 0.56 : 0.5
        });
      },
      signal: options.signal
    });
  }

  try {
    options.onProgress?.({
      stage: "preparing-media",
      message: "Preparing source video",
      progress: 0.12
    });

    const data = await fetchVideoBytes(candidate.url, {
      onProgress: (progress) => {
        options.onProgress?.({
          stage: "preparing-media",
          message: "Preparing source video",
          progress: 0.12 + progress * 0.56
        });
      },
      signal: options.signal
    });

    const files = [
      {
        data,
        path: "source.mp4"
      }
    ];
    const audioInputPath = candidate.audioUrl ? `source-audio${getUrlExtension(candidate.audioUrl, ".mp4")}` : undefined;

    if (candidate.audioUrl && audioInputPath) {
      files.push({
        data: await fetchVideoBytes(candidate.audioUrl, {
          onProgress: (progress) => {
            options.onProgress?.({
              stage: "preparing-media",
              message: "Preparing source audio",
              progress: 0.68 + progress * 0.2
            });
          },
          signal: options.signal
        }),
        path: audioInputPath
      });
    }

    return {
      audioInputPath,
      files,
      kind: "mp4",
      sourceUrl: candidate.url,
      videoInputPath: "source.mp4"
    };
  } catch (error) {
    if (isAbortError(error)) {
      throw new VideoRenderError("ABORTED", "Video rendering was interrupted.", error);
    }

    throw error;
  }
}

function getVideoSourceCandidates(media: VideoPostMedia): VideoRenderSourceCandidate[] {
  const urls = [media.url, ...(media.variants ?? [])]
    .map(normalizeSourceUrl)
    .filter((url): url is string => typeof url === "string" && !isVideoSegmentUrl(url))
    .filter((url) => isMatchingPosterMediaSource(media.posterUrl, url));
  const audioUrls = [...new Set(urls.filter(isLikelyAudioOnlySourceUrl))];
  const videoUrls = [...new Set(urls.filter((url) => !isLikelyAudioOnlySourceUrl(url)))].sort((a, b) => scoreVideoSourceUrl(b) - scoreVideoSourceUrl(a));

  return videoUrls.map((url) => ({
    audioUrl: isHlsMediaUrl(url) ? undefined : audioUrls[0],
    url
  }));
}

function normalizeSourceUrl(source: string | undefined): string | undefined {
  if (!source) {
    return undefined;
  }

  try {
    const url = new URL(source.replace(/\\u0026/g, "&").replace(/&amp;/g, "&"));

    return ["http:", "https:"].includes(url.protocol) ? url.toString() : undefined;
  } catch {
    return undefined;
  }
}

function scoreVideoSourceUrl(value: string): number {
  try {
    const pathname = new URL(value).pathname.toLowerCase();
    const resolution = /\/(\d{2,5})x(\d{2,5})(?:\/|$)/.exec(pathname);
    const pixels = resolution ? Number(resolution[1]) * Number(resolution[2]) : 0;
    let score = pixels / 1000;

    if (pathname.endsWith(".m3u8")) {
      score += 260_000;
    }

    if (pathname.endsWith(".mp4") && !pathname.includes("/pl/")) {
      score += 120_000;
    }

    return score;
  } catch {
    return 0;
  }
}

function isVideoSegmentUrl(value: string): boolean {
  try {
    const pathname = new URL(value).pathname.toLowerCase();

    return (
      pathname.endsWith(".m4s") ||
      pathname.endsWith(".ts") ||
      pathname.includes("/seg/") ||
      /\/vid\/avc1\/0\/0\/\d{2,5}x\d{2,5}\//.test(pathname) ||
      (pathname.endsWith(".mp4") && pathname.includes("/pl/") && /(?:^|\/)(init|map)\.mp4$/.test(pathname))
    );
  } catch {
    return true;
  }
}

function getUrlExtension(value: string, fallback: string): string {
  try {
    const extension = /\.[a-z0-9]+$/i.exec(new URL(value).pathname)?.[0];

    return extension ?? fallback;
  } catch {
    return fallback;
  }
}

function isLikelyAudioOnlySourceUrl(value: string): boolean {
  try {
    const pathname = new URL(value).pathname.toLowerCase();

    return /(?:^|\/)(?:audio|aud|mp4a|aac)(?:[./_-]|\/|$)/.test(pathname);
  } catch {
    return false;
  }
}

function isMatchingPosterMediaSource(posterUrl: string | undefined, sourceUrl: string): boolean {
  const mediaId = extractTwitterVideoMediaId(posterUrl);

  return !mediaId || extractTwitterVideoSourceId(sourceUrl) === mediaId;
}

function extractTwitterVideoMediaId(value: string | undefined): string | undefined {
  if (!value) {
    return undefined;
  }

  try {
    const pathname = new URL(value).pathname;

    return /\/(?:ext_tw_video_thumb|amplify_video_thumb|tweet_video_thumb)\/([^/]+)\//.exec(pathname)?.[1];
  } catch {
    return undefined;
  }
}

function extractTwitterVideoSourceId(value: string): string | undefined {
  try {
    const pathname = new URL(value).pathname;

    return /\/(?:ext_tw_video|amplify_video|tweet_video)\/([^/]+)\//.exec(pathname)?.[1];
  } catch {
    return undefined;
  }
}

async function fetchVideoBytes(
  url: string,
  options: {
    onProgress?: (progress: number) => void;
    signal?: AbortSignal;
  }
): Promise<Uint8Array> {
  const response = await fetch(url, {
    credentials: "omit",
    referrerPolicy: "no-referrer",
    signal: options.signal
  });

  if (!response.ok) {
    throw new VideoRenderError("MEDIA_SOURCE_UNAVAILABLE", `Video request failed with HTTP ${response.status}.`);
  }

  if (!response.body) {
    options.onProgress?.(1);
    return new Uint8Array(await response.arrayBuffer());
  }

  const contentLength = Number(response.headers.get("content-length") ?? 0);
  const reader = response.body.getReader();
  const chunks: Uint8Array[] = [];
  let received = 0;

  while (true) {
    const { done, value } = await reader.read();

    if (done) {
      break;
    }

    chunks.push(value);
    received += value.length;

    if (contentLength > 0) {
      options.onProgress?.(Math.min(1, received / contentLength));
    }
  }

  const data = new Uint8Array(received);
  let offset = 0;

  for (const chunk of chunks) {
    data.set(chunk, offset);
    offset += chunk.length;
  }

  options.onProgress?.(1);

  return data;
}

function isAbortError(error: unknown): boolean {
  return error instanceof DOMException && error.name === "AbortError";
}
