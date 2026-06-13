import { resolveVideoMediaSource } from "./services/media-source.service";
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

  try {
    reportProgress(normalizedRequest, {
      stage: "preparing-media",
      message: "Preparing media",
      progress: 0
    });

    const [mediaSource, template] = await Promise.all([
      resolveVideoMediaSource(normalizedRequest.post, {
        onProgress: normalizedRequest.onProgress,
        signal: normalizedRequest.signal
      }),
      renderVideoTemplateAsset(normalizedRequest.templateNode)
    ]);

    reportProgress(normalizedRequest, {
      stage: "loading-renderer",
      message: "Loading renderer",
      progress: 0
    });

    await loadWasmFfmpegRenderer(normalizedRequest.signal);

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
    if (normalizedRequest.preferredRenderer === "wasm-ffmpeg") {
      throw toVideoRenderError(error);
    }

    if (!normalizedRequest.browserVideo) {
      throw toVideoRenderError(error);
    }

    return renderRealtimeBrowserVideo(normalizedRequest, getFallbackReason(error));
  }
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

function getFallbackReason(error: unknown): string {
  if (error instanceof Error) {
    return error.message;
  }

  return "The FFmpeg renderer could not complete this export.";
}
