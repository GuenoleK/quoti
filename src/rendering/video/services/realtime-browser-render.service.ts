import { exportPostVideoToWebmBlob } from "../../../shared/utils/video-export.util";
import type { VideoRenderRequest, VideoRenderResult } from "../video-render.types";
import { VideoRenderError } from "../video-render.types";

export async function renderRealtimeBrowserVideo(request: VideoRenderRequest, fallbackReason?: string): Promise<VideoRenderResult> {
  if (!request.browserVideo) {
    throw new VideoRenderError("VIDEO_ELEMENT_UNAVAILABLE", "Quoti could not find the video player.");
  }

  request.onProgress?.({
    stage: "fallback-rendering",
    message: "Rendering video",
    progress: 0
  });

  const blob = await exportPostVideoToWebmBlob({
    cardTheme: request.cardTheme,
    post: request.post,
    video: request.browserVideo
  });

  request.onProgress?.({
    stage: "ready",
    message: "Video ready",
    progress: 1
  });

  return {
    blob,
    fallbackReason,
    filenameExtension: "webm",
    mimeType: blob.type || "video/webm",
    renderer: "realtime-browser"
  };
}
