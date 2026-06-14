import type { ExtractedPost, PostExtractionResult } from "./post.types";

export type QuotiMessage =
  | {
      type: "QUOTI_GET_SELECTED_POST";
      observedVideoUrls?: string[];
    }
  | {
      type: "QUOTI_GET_CONTEXT_POST";
      observedVideoUrls?: string[];
    }
  | {
      type: "QUOTI_GET_INLINE_POST";
      postId: string;
      observedVideoUrls?: string[];
    }
  | {
      type: "QUOTI_SETTINGS_UPDATED";
    }
  | {
      type: "QUOTI_OPEN_POPUP";
    }
  | {
      type: "QUOTI_READ_OBSERVED_VIDEO_URLS";
      tabId?: number;
    }
  | {
      type: "QUOTI_HYDRATE_RELATED_POST";
      post: ExtractedPost;
    }
  | {
      type: "QUOTI_INLINE_POST_CAPTURED";
      post?: ExtractedPost;
    }
  | {
      type: "QUOTI_PING";
    };

export type QuotiMessageResponse =
  | PostExtractionResult
  | {
      status: "video-urls";
      observedVideoUrls: string[];
    }
  | {
      status: "ready";
    };
