import type { ExtractedPost, PostExtractionResult } from "./post.types";

export type QuotiMessage =
  | {
      type: "QUOTI_GET_SELECTED_POST";
    }
  | {
      type: "QUOTI_GET_CONTEXT_POST";
    }
  | {
      type: "QUOTI_GET_INLINE_POST";
      postId: string;
    }
  | {
      type: "QUOTI_SETTINGS_UPDATED";
    }
  | {
      type: "QUOTI_OPEN_POPUP";
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
      status: "ready";
    };
