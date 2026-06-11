import type { QuotiMessage, QuotiMessageResponse } from "../../shared/types/extension-message.types";
import { extractSelectedXPost } from "./x-post-extractor";

chrome.runtime.onMessage.addListener(
  (message: QuotiMessage, _sender, sendResponse: (response: QuotiMessageResponse) => void) => {
    if (message.type === "QUOTI_PING") {
      sendResponse({ status: "ready" });
      return false;
    }

    if (message.type === "QUOTI_GET_SELECTED_POST") {
      sendResponse(extractSelectedXPost());
      return false;
    }

    return false;
  }
);
