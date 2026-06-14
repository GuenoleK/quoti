import type { VideoRenderProgress, VideoRenderQuality } from "./video-render.types";

export const nativeRendererHostName = "com.quoti.renderer";

export type NativeVideoRenderPayload = {
  candidates: string[];
  quality: VideoRenderQuality;
  template: {
    dataBase64: string;
    height: number;
    mediaRect: {
      height: number;
      radius: number;
      width: number;
      x: number;
      y: number;
    };
    width: number;
  };
};

export type NativeVideoRenderRequest = {
  payload: NativeVideoRenderPayload;
  requestId: string;
  type: "render.video";
};

export type NativeVideoReleaseRequest = {
  requestId: string;
  type: "render.release";
};

export type NativeVideoCancelRequest = {
  requestId: string;
  type: "render.cancel";
};

export type NativeRendererPingRequest = {
  requestId: string;
  type: "renderer.ping";
};

export type NativeVideoRenderClientMessage =
  | NativeRendererPingRequest
  | NativeVideoCancelRequest
  | NativeVideoReleaseRequest
  | NativeVideoRenderRequest;

export type NativeRendererPongResponse = {
  requestId: string;
  type: "renderer.pong";
};

export type NativeVideoProgressResponse = {
  message: string;
  progress?: number;
  requestId: string;
  stage: VideoRenderProgress["stage"];
  type: "render.progress";
};

export type NativeVideoReadyResponse = {
  requestId: string;
  result: {
    downloadUrl: string;
    filenameExtension: "mp4";
    mimeType: "video/mp4";
  };
  type: "render.ready";
};

export type NativeVideoErrorResponse = {
  error: {
    code?: string;
    message: string;
  };
  requestId: string;
  type: "render.error";
};

export type NativeVideoRenderHostMessage =
  | NativeVideoErrorResponse
  | NativeRendererPongResponse
  | NativeVideoProgressResponse
  | NativeVideoReadyResponse;
