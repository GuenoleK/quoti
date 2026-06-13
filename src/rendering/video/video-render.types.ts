import type { CardTheme, ExtractedPost } from "../../shared/types/post.types";

export type VideoRenderQuality = "fast" | "balanced" | "high";

export type VideoRendererKind = "wasm-ffmpeg" | "realtime-browser";

export type VideoRenderProgress =
  | {
      stage: "preparing-media";
      message: string;
      progress?: number;
    }
  | {
      stage: "loading-renderer";
      message: string;
      progress?: number;
    }
  | {
      stage: "rendering";
      message: string;
      progress: number;
    }
  | {
      stage: "finalizing";
      message: string;
      progress?: number;
    }
  | {
      stage: "fallback-rendering";
      message: string;
      progress?: number;
    }
  | {
      stage: "ready";
      message: string;
      progress: 1;
    };

export type VideoRenderRequest = {
  browserVideo?: HTMLVideoElement | null;
  cardTheme: CardTheme;
  onProgress?: (progress: VideoRenderProgress) => void;
  post: ExtractedPost;
  preferredRenderer?: "auto" | VideoRendererKind;
  quality?: VideoRenderQuality;
  signal?: AbortSignal;
  templateNode: HTMLElement;
};

export type VideoRenderResult = {
  blob: Blob;
  fallbackReason?: string;
  filenameExtension: "mp4" | "webm";
  mimeType: string;
  renderer: VideoRendererKind;
};

export type VideoRenderMediaFile = {
  data: Uint8Array;
  path: string;
};

export type VideoRenderMediaSource = {
  audioInputPath?: string;
  files: VideoRenderMediaFile[];
  kind: "mp4" | "hls";
  sourceUrl: string;
  videoInputPath: string;
};

export type VideoTemplateAsset = {
  data: Uint8Array;
  height: number;
  mediaRect: {
    height: number;
    radius: number;
    width: number;
    x: number;
    y: number;
  };
  path: string;
  width: number;
};

export type VideoRenderErrorCode =
  | "ABORTED"
  | "FFMPEG_FAILED"
  | "MEDIA_SOURCE_UNAVAILABLE"
  | "RENDERER_UNAVAILABLE"
  | "TEMPLATE_RENDER_FAILED"
  | "VIDEO_ELEMENT_UNAVAILABLE";

export class VideoRenderError extends Error {
  readonly cause?: unknown;
  readonly code: VideoRenderErrorCode;

  constructor(code: VideoRenderErrorCode, message: string, cause?: unknown) {
    super(message);
    this.name = "VideoRenderError";
    this.code = code;
    this.cause = cause;
  }
}
