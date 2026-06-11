import type { PostExtractionResult } from "./post.types";

export type QuotiMessage =
  | {
      type: "QUOTI_GET_SELECTED_POST";
    }
  | {
      type: "QUOTI_PING";
    };

export type QuotiMessageResponse =
  | PostExtractionResult
  | {
      status: "ready";
    };
