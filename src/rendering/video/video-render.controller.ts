import { getVideoMediaSourceCandidates, resolveVideoMediaSourceCandidate } from "./services/media-source.service";
import { renderNativeFfmpegVideo } from "./services/native-render.service";
import { renderRealtimeBrowserVideo } from "./services/realtime-browser-render.service";
import { renderVideoTemplateAsset } from "./services/template-render.service";
import { loadWasmFfmpegRenderer, renderWasmFfmpegVideo } from "./services/wasm-ffmpeg-render.service";
import type { VideoRenderProgress, VideoRenderRequest, VideoRenderResult } from "./video-render.types";
import { VideoRenderError } from "./video-render.types";

export async function renderPostVideo(request: VideoRenderRequest): Promise<VideoRenderResult> {
  const normalizedRequest = {
    ...request,
    preferredRenderer: request.preferredRenderer ?? "auto",
    quality: request.quality ?? "balanced"
  } satisfies VideoRenderRequest;

  if (normalizedRequest.preferredRenderer === "realtime-browser") {
    return renderRealtimeBrowserVideo(normalizedRequest);
  }

  if (normalizedRequest.preferredRenderer === "auto" || normalizedRequest.preferredRenderer === "native") {
    try {
      return await renderNativeFfmpegVideo(normalizedRequest);
    } catch (error) {
      const nativeError = toVideoRenderError(error);

      if (normalizedRequest.preferredRenderer === "native" || nativeError.code === "ABORTED") {
        throw nativeError;
      }

      reportProgress(normalizedRequest, {
        stage: "loading-renderer",
        message: "Native renderer unavailable. Using bundled renderer",
        progress: 0
      });
    }
  }

  try {
    reportProgress(normalizedRequest, {
      stage: "preparing-media",
      message: "Preparing media",
      progress: 0
    });

    const candidates = getVideoMediaSourceCandidates(normalizedRequest.post);
    const template = await renderVideoTemplateAsset(normalizedRequest.templateNode);
    let rendererLoaded = false;
    let lastCandidateError: VideoRenderError | null = null;

    for (let index = 0; index < candidates.length; index += 1) {
      const candidate = candidates[index];

      try {
        reportProgress(normalizedRequest, {
          stage: "preparing-media",
          message: candidates.length > 1 ? `Preparing media source ${index + 1}/${candidates.length}` : "Preparing media",
          progress: 0
        });

        const mediaSource = await resolveVideoMediaSourceCandidate(candidate, {
          onProgress: normalizedRequest.onProgress,
          signal: normalizedRequest.signal
        });

        if (!rendererLoaded) {
          reportProgress(normalizedRequest, {
            stage: "loading-renderer",
            message: "Loading renderer",
            progress: 0
          });

          await loadWasmFfmpegRenderer(normalizedRequest.signal);
          rendererLoaded = true;
        }

        reportProgress(normalizedRequest, {
          stage: "rendering",
          message: "Rendering video",
          progress: 0
        });

        const result = await renderWasmFfmpegVideo({
          mediaSource,
          onProgress: (progress) => {
            reportProgress(normalizedRequest, {
              stage: "rendering",
              message: "Rendering video",
              progress
            });
          },
          quality: normalizedRequest.quality,
          signal: normalizedRequest.signal,
          template
        });

        reportProgress(normalizedRequest, {
          stage: "finalizing",
          message: "Finalizing MP4",
          progress: 0.98
        });
        reportProgress(normalizedRequest, {
          stage: "ready",
          message: "Video ready",
          progress: 1
        });

        return result;
      } catch (error) {
        const videoError = toVideoRenderError(error);

        if (!shouldTryNextMediaSource(videoError) || index === candidates.length - 1) {
          throw videoError;
        }

        lastCandidateError = videoError;
      }
    }

    throw lastCandidateError ?? new VideoRenderError("MEDIA_SOURCE_UNAVAILABLE", "Quoti could not resolve a playable video source.");
  } catch (error) {
    throw toVideoRenderError(error);
  }
}

function shouldTryNextMediaSource(error: VideoRenderError): boolean {
  return error.code === "FFMPEG_FAILED" || error.code === "MEDIA_SOURCE_UNAVAILABLE";
}

export async function preloadVideoRenderer(signal?: AbortSignal): Promise<void> {
  await loadWasmFfmpegRenderer(signal);
}

export function getVideoRenderProgressLabel(progress: VideoRenderProgress | null): string | undefined {
  if (!progress) {
    return undefined;
  }

  if (progress.stage === "rendering" && Number.isFinite(progress.progress)) {
    return `${progress.message} ${Math.round(progress.progress * 100)}%`;
  }

  return progress.message;
}

function reportProgress(request: VideoRenderRequest, progress: VideoRenderProgress): void {
  request.onProgress?.(progress);
}

function toVideoRenderError(error: unknown): VideoRenderError {
  if (error instanceof VideoRenderError) {
    return error;
  }

  if (error instanceof DOMException && error.name === "AbortError") {
    return new VideoRenderError("ABORTED", "Video rendering was interrupted.", error);
  }

  return new VideoRenderError("FFMPEG_FAILED", "Quoti could not render this video.", error);
}

