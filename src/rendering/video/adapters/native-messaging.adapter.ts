import {
  nativeRendererHostName,
  type NativeVideoRenderClientMessage,
  type NativeVideoRenderHostMessage,
  type NativeVideoRenderPayload
} from "../native-render.protocol";
import { VideoRenderError, type VideoRenderProgress, type VideoRenderResult } from "../video-render.types";

type RenderNativeHostOptions = {
  onProgress?: (progress: VideoRenderProgress) => void;
  payload: NativeVideoRenderPayload;
  signal?: AbortSignal;
};

let nativeAvailabilityPromise: Promise<boolean> | null = null;

export function isNativeHostAvailable(signal?: AbortSignal): Promise<boolean> {
  nativeAvailabilityPromise ??= probeNativeHost(signal).finally(() => {
    nativeAvailabilityPromise = null;
  });

  return nativeAvailabilityPromise;
}

export async function renderWithNativeHost({
  onProgress,
  payload,
  signal
}: RenderNativeHostOptions): Promise<VideoRenderResult> {
  if (!canConnectNative()) {
    throw new VideoRenderError("RENDERER_UNAVAILABLE", "The Quoti native renderer is not available.");
  }

  const requestId = createRequestId();

  return new Promise<VideoRenderResult>((resolve, reject) => {
    let settled = false;
    let port: chrome.runtime.Port | null = null;

    const rejectOnce = (error: unknown) => {
      if (settled) {
        return;
      }

      settled = true;
      cleanup();
      reject(toVideoRenderError(error));
    };

    const resolveOnce = (result: VideoRenderResult) => {
      if (settled) {
        return;
      }

      settled = true;
      cleanup();
      resolve(result);
    };

    const onAbort = () => {
      if (port) {
        postNativeMessage(port, {
          requestId,
          type: "render.cancel"
        });
      }

      rejectOnce(new VideoRenderError("ABORTED", "Video rendering was interrupted."));
    };

    const onDisconnect = () => {
      if (settled) {
        return;
      }

      const message = chrome.runtime.lastError?.message ?? "The Quoti native renderer disconnected unexpectedly.";
      rejectOnce(new VideoRenderError("RENDERER_UNAVAILABLE", message));
    };

    const onMessage = (message: NativeVideoRenderHostMessage) => {
      if (!isNativeHostMessageForRequest(message, requestId)) {
        return;
      }

      if (!port) {
        return;
      }

      void handleNativeMessage(message, {
        onProgress,
        port,
        rejectOnce,
        requestId,
        resolveOnce,
        signal
      });
    };

    const cleanup = () => {
      signal?.removeEventListener("abort", onAbort);

      if (!port) {
        return;
      }

      try {
        port.onMessage.removeListener(onMessage);
        port.onDisconnect.removeListener(onDisconnect);
        port.disconnect();
      } catch {
        // The port may already be gone if Chrome failed to start the host.
      }
    };

    if (signal?.aborted) {
      rejectOnce(new VideoRenderError("ABORTED", "Video rendering was interrupted."));
      return;
    }

    try {
      port = chrome.runtime.connectNative(nativeRendererHostName);
      port.onMessage.addListener(onMessage);
      port.onDisconnect.addListener(onDisconnect);
      signal?.addEventListener("abort", onAbort, { once: true });

      postNativeMessage(port, {
        payload,
        requestId,
        type: "render.video"
      });
    } catch (error) {
      rejectOnce(new VideoRenderError("RENDERER_UNAVAILABLE", "The Quoti native renderer could not be started.", error));
    }
  });
}

async function probeNativeHost(signal: AbortSignal | undefined): Promise<boolean> {
  if (!canConnectNative()) {
    return false;
  }

  const requestId = createRequestId();

  return new Promise<boolean>((resolve) => {
    let port: chrome.runtime.Port | null = null;
    let settled = false;

    const settle = (available: boolean) => {
      if (settled) {
        return;
      }

      settled = true;
      cleanup();
      resolve(available);
    };

    const onAbort = () => {
      settle(false);
    };

    const onDisconnect = () => {
      settle(false);
    };

    const onMessage = (message: NativeVideoRenderHostMessage) => {
      if (!isNativeHostMessageForRequest(message, requestId)) {
        return;
      }

      settle(message.type === "renderer.pong");
    };

    const cleanup = () => {
      window.clearTimeout(timeout);
      signal?.removeEventListener("abort", onAbort);

      if (!port) {
        return;
      }

      try {
        port.onMessage.removeListener(onMessage);
        port.onDisconnect.removeListener(onDisconnect);
        port.disconnect();
      } catch {
        // Chrome may already have closed the probe port.
      }
    };

    const timeout = window.setTimeout(() => {
      settle(false);
    }, 1600);

    if (signal?.aborted) {
      settle(false);
      return;
    }

    try {
      port = chrome.runtime.connectNative(nativeRendererHostName);
      port.onMessage.addListener(onMessage);
      port.onDisconnect.addListener(onDisconnect);
      signal?.addEventListener("abort", onAbort, { once: true });
      postNativeMessage(port, {
        requestId,
        type: "renderer.ping"
      });
    } catch {
      settle(false);
    }
  });
}

async function handleNativeMessage(
  message: NativeVideoRenderHostMessage,
  options: {
    onProgress?: (progress: VideoRenderProgress) => void;
    port: chrome.runtime.Port;
    rejectOnce: (error: unknown) => void;
    requestId: string;
    resolveOnce: (result: VideoRenderResult) => void;
    signal?: AbortSignal;
  }
): Promise<void> {
  if (message.type === "render.progress") {
    options.onProgress?.({
      stage: message.stage,
      message: message.message,
      progress: message.progress
    } as VideoRenderProgress);
    return;
  }

  if (message.type === "render.error") {
    options.rejectOnce(toNativeVideoRenderError(message.error.code, message.error.message));
    return;
  }

  if (message.type !== "render.ready") {
    return;
  }

  try {
    options.onProgress?.({
      stage: "finalizing",
      message: "Downloading native render",
      progress: 0.98
    });

    const blob = await fetchNativeRenderBlob(message.result.downloadUrl, options.signal);

    postNativeMessage(options.port, {
      requestId: options.requestId,
      type: "render.release"
    });

    options.resolveOnce({
      blob,
      filenameExtension: message.result.filenameExtension,
      mimeType: message.result.mimeType,
      renderer: "native"
    });
  } catch (error) {
    postNativeMessage(options.port, {
      requestId: options.requestId,
      type: "render.release"
    });

    options.rejectOnce(error);
  }
}

async function fetchNativeRenderBlob(downloadUrl: string, signal: AbortSignal | undefined): Promise<Blob> {
  const response = await fetch(downloadUrl, {
    cache: "no-store",
    signal
  });

  if (!response.ok) {
    throw new VideoRenderError("FFMPEG_FAILED", `Native render download failed with HTTP ${response.status}.`);
  }

  return response.blob();
}

function canConnectNative(): boolean {
  return typeof chrome !== "undefined" && Boolean(chrome.runtime?.connectNative);
}

function createRequestId(): string {
  return typeof crypto !== "undefined" && "randomUUID" in crypto
    ? crypto.randomUUID()
    : `quoti-${Date.now().toString(36)}-${Math.random().toString(36).slice(2)}`;
}

function isNativeHostMessageForRequest(message: unknown, requestId: string): message is NativeVideoRenderHostMessage {
  if (!message || typeof message !== "object") {
    return false;
  }

  const candidate = message as NativeVideoRenderHostMessage;

  return candidate.requestId === requestId;
}

function postNativeMessage(port: chrome.runtime.Port, message: NativeVideoRenderClientMessage): void {
  try {
    port.postMessage(message);
  } catch {
    // Disconnect and startup errors are surfaced through the promise path.
  }
}

function toNativeVideoRenderError(code: string | undefined, message: string): VideoRenderError {
  if (code === "ABORTED") {
    return new VideoRenderError("ABORTED", message);
  }

  if (code === "RENDERER_UNAVAILABLE" || code === "FFMPEG_NOT_BUNDLED") {
    return new VideoRenderError("RENDERER_UNAVAILABLE", message);
  }

  if (code === "MEDIA_SOURCE_UNAVAILABLE") {
    return new VideoRenderError("MEDIA_SOURCE_UNAVAILABLE", message);
  }

  return new VideoRenderError("FFMPEG_FAILED", message);
}

function toVideoRenderError(error: unknown): VideoRenderError {
  if (error instanceof VideoRenderError) {
    return error;
  }

  if (error instanceof DOMException && error.name === "AbortError") {
    return new VideoRenderError("ABORTED", "Video rendering was interrupted.", error);
  }

  return new VideoRenderError("FFMPEG_FAILED", "The Quoti native renderer could not complete this export.", error);
}
