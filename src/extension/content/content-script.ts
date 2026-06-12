import type { QuotiMessage, QuotiMessageResponse } from "../../shared/types/extension-message.types";
import { disposeXPostExtractor, extractContextXPost, extractInlineXPost, extractSelectedXPost, initializeXPostExtractor } from "./x-post-extractor";
import "./content-script.css";

declare global {
  interface Window {
    __quotiContentScriptLoaded?: boolean;
  }
}

if (!window.__quotiContentScriptLoaded) {
  window.__quotiContentScriptLoaded = true;

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

    if (message.type === "QUOTI_GET_CONTEXT_POST") {
      sendResponse(extractContextXPost());
      return false;
    }

    if (message.type === "QUOTI_GET_INLINE_POST") {
      sendResponse(extractInlineXPost(message.postId));
      return false;
    }

    return false;
  }
  );

  initializeXPostExtractor();

  window.addEventListener("pagehide", disposeXPostExtractor, { once: true });
}
