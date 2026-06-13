import type { ExtractedPost, VideoPostMedia } from "../../../shared/types/post.types";
import type { VideoRenderMediaSource, VideoRenderProgress } from "../video-render.types";
import { VideoRenderError } from "../video-render.types";
import { isHlsMediaUrl, resolveHlsMediaSource } from "../adapters/hls-media.adapter";

type ResolveMediaSourceOptions = {
  onProgress?: (progress: VideoRenderProgress) => void;
  signal?: AbortSignal;
};

export async function resolveVideoMediaSource(post: ExtractedPost, options: ResolveMediaSourceOptions = {}): Promise<VideoRenderMediaSource> {
  const media = post.media.find((item): item is VideoPostMedia => item.type === "video");

  if (!media) {
    throw new VideoRenderError("MEDIA_SOURCE_UNAVAILABLE", "No video media was found in this post.");
  }

  const candidates = getVideoSourceCandidates(media);

  if (candidates.length === 0) {
    throw new VideoRenderError("MEDIA_SOURCE_UNAVAILABLE", "No playable video URL was found.");
  }

  const errors: unknown[] = [];

  for (const candidate of candidates) {
    try {
      if (isHlsMediaUrl(candidate)) {
        options.onProgress?.({
          stage: "preparing-media",
          message: "Preparing HLS media",
          progress: 0.12
        });

        return await resolveHlsMediaSource(candidate, {
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

      options.onProgress?.({
        stage: "preparing-media",
        message: "Preparing source video",
        progress: 0.12
      });

      const data = await fetchVideoBytes(candidate, {
        onProgress: (progress) => {
          options.onProgress?.({
            stage: "preparing-media",
            message: "Preparing source video",
            progress: 0.12 + progress * 0.56
          });
        },
        signal: options.signal
      });

      return {
        files: [
          {
            data,
            path: "source.mp4"
          }
        ],
        kind: "mp4",
        sourceUrl: candidate,
        videoInputPath: "source.mp4"
      };
    } catch (error) {
      if (isAbortError(error)) {
        throw new VideoRenderError("ABORTED", "Video rendering was interrupted.", error);
      }

      errors.push(error);
    }
  }

  throw new VideoRenderError("MEDIA_SOURCE_UNAVAILABLE", "Quoti could not resolve a playable video source.", errors);
}

function getVideoSourceCandidates(media: VideoPostMedia): string[] {
  const candidates = [media.url, ...(media.variants ?? [])]
    .map(normalizeSourceUrl)
    .filter((url): url is string => typeof url === "string" && !isVideoSegmentUrl(url));

  return [...new Set(candidates)].sort((a, b) => scoreVideoSourceUrl(b) - scoreVideoSourceUrl(a));
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

    if (pathname.endsWith(".mp4") && !pathname.includes("/pl/")) {
      score += 220_000;
    }

    if (pathname.endsWith(".m3u8")) {
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
