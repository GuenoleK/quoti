import { isNativeHostAvailable, renderWithNativeHost } from "../adapters/native-messaging.adapter";
import { getVideoMediaSourceCandidates } from "./media-source.service";
import { renderVideoTemplateAsset } from "./template-render.service";
import { VideoRenderError, type VideoRenderRequest, type VideoRenderResult, type VideoTemplateAsset } from "../video-render.types";

export async function renderNativeFfmpegVideo(request: VideoRenderRequest): Promise<VideoRenderResult> {
  request.onProgress?.({
    stage: "loading-renderer",
    message: "Checking native renderer",
    progress: 0
  });

  const isAvailable = await isNativeHostAvailable(request.signal);

  if (!isAvailable) {
    throw new VideoRenderError("RENDERER_UNAVAILABLE", "The Quoti native renderer is not installed or FFmpeg is not bundled.");
  }

  request.onProgress?.({
    stage: "preparing-media",
    message: "Preparing native render",
    progress: 0
  });

  const candidates = getVideoMediaSourceCandidates(request.post);
  const template = await renderVideoTemplateAsset(request.templateNode);

  request.onProgress?.({
    stage: "loading-renderer",
    message: "Starting native renderer",
    progress: 0
  });

  return renderWithNativeHost({
    onProgress: request.onProgress,
    payload: {
      candidates,
      quality: request.quality ?? "balanced",
      requireAudio: Boolean(request.browserVideo),
      template: toNativeTemplatePayload(template)
    },
    signal: request.signal
  });
}

function toNativeTemplatePayload(template: VideoTemplateAsset) {
  return {
    dataBase64: bytesToBase64(template.data),
    height: template.height,
    mediaRect: template.mediaRect,
    sourceCrop: template.sourceCrop,
    width: template.width
  };
}

function bytesToBase64(data: Uint8Array): string {
  let binary = "";
  const chunkSize = 0x8000;

  for (let offset = 0; offset < data.length; offset += chunkSize) {
    const chunk = data.subarray(offset, offset + chunkSize);
    binary += String.fromCharCode(...chunk);
  }

  return btoa(binary);
}
