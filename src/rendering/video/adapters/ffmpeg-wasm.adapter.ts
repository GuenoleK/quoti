import { FFmpeg } from "@ffmpeg/ffmpeg";
import type { LogEvent, ProgressEvent as FfmpegProgressEvent } from "@ffmpeg/ffmpeg";
import type { VideoRenderMediaFile } from "../video-render.types";
import { VideoRenderError } from "../video-render.types";

type ExecuteOptions = {
  onProgress?: (progress: number) => void;
  signal?: AbortSignal;
};

class FfmpegWasmAdapter {
  private ffmpeg: FFmpeg | null = null;
  private loadPromise: Promise<void> | null = null;

  async load(signal: AbortSignal | undefined): Promise<void> {
    if (this.ffmpeg?.loaded) {
      return;
    }

    if (this.loadPromise) {
      return this.loadPromise;
    }

    const ffmpeg = this.ffmpeg ?? new FFmpeg();
    this.ffmpeg = ffmpeg;

    this.loadPromise = ffmpeg
      .load(
        {
          coreURL: getFfmpegAssetUrl("ffmpeg-core.js"),
          wasmURL: getFfmpegAssetUrl("ffmpeg-core.wasm")
        },
        { signal }
      )
      .then(() => undefined)
      .catch((error) => {
        this.ffmpeg = null;
        throw new VideoRenderError("RENDERER_UNAVAILABLE", "Quoti could not load the local FFmpeg renderer.", error);
      })
      .finally(() => {
        this.loadPromise = null;
      });

    return this.loadPromise;
  }

  async writeFiles(files: VideoRenderMediaFile[], signal: AbortSignal | undefined): Promise<void> {
    const ffmpeg = this.requireFfmpeg();

    for (const file of files) {
      await ffmpeg.writeFile(file.path, file.data, { signal });
    }
  }

  async execute(args: string[], options: ExecuteOptions = {}): Promise<void> {
    const ffmpeg = this.requireFfmpeg();
    const logs: string[] = [];
    const handleLog = (event: LogEvent): void => {
      logs.push(event.message);

      if (logs.length > 40) {
        logs.shift();
      }
    };
    const handleProgress = (event: FfmpegProgressEvent): void => {
      if (Number.isFinite(event.progress)) {
        options.onProgress?.(Math.min(0.98, Math.max(0, event.progress)));
      }
    };

    ffmpeg.on("log", handleLog);
    ffmpeg.on("progress", handleProgress);

    try {
      const exitCode = await ffmpeg.exec(args, -1, { signal: options.signal });

      if (exitCode !== 0) {
        throw new VideoRenderError(
          "FFMPEG_FAILED",
          `FFmpeg exited with code ${exitCode}. ${logs.slice(-3).join(" ").trim()}`.trim()
        );
      }
    } finally {
      ffmpeg.off("log", handleLog);
      ffmpeg.off("progress", handleProgress);
    }
  }

  async readFile(path: string, signal: AbortSignal | undefined): Promise<Uint8Array> {
    const file = await this.requireFfmpeg().readFile(path, "binary", { signal });

    if (typeof file === "string") {
      throw new VideoRenderError("FFMPEG_FAILED", "FFmpeg returned text where video bytes were expected.");
    }

    return file;
  }

  async deleteFiles(paths: string[]): Promise<void> {
    const ffmpeg = this.requireFfmpeg();

    await Promise.all(
      paths.map(async (path) => {
        try {
          await ffmpeg.deleteFile(path);
        } catch {
          // Best-effort cleanup. Missing files should not hide the render result.
        }
      })
    );
  }

  private requireFfmpeg(): FFmpeg {
    if (!this.ffmpeg?.loaded) {
      throw new VideoRenderError("RENDERER_UNAVAILABLE", "The FFmpeg renderer is not loaded.");
    }

    return this.ffmpeg;
  }
}

export const ffmpegWasmAdapter = new FfmpegWasmAdapter();

function getFfmpegAssetUrl(filename: "ffmpeg-core.js" | "ffmpeg-core.wasm"): string {
  const extensionPath = `assets/ffmpeg/${filename}`;

  if (typeof chrome !== "undefined" && chrome.runtime?.getURL) {
    try {
      return chrome.runtime.getURL(extensionPath);
    } catch {
      // Fall through to the Vite development URL.
    }
  }

  return new URL(`/node_modules/@ffmpeg/core/dist/esm/${filename}`, window.location.origin).toString();
}
