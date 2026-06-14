import type { VideoRenderMediaSource, VideoRenderQuality, VideoRenderResult, VideoTemplateAsset } from "../video-render.types";
import { VideoRenderError } from "../video-render.types";
import { ffmpegWasmAdapter } from "../adapters/ffmpeg-wasm.adapter";

type RenderWasmVideoOptions = {
  mediaSource: VideoRenderMediaSource;
  onProgress?: (progress: number) => void;
  quality: VideoRenderQuality;
  signal?: AbortSignal;
  template: VideoTemplateAsset;
};

type QualityPreset = {
  crf: number;
  preset: string;
};

type AudioMode = "aac" | "copy";

const outputPath = "quoti-output.mp4";

export async function renderWasmFfmpegVideo({
  mediaSource,
  onProgress,
  quality,
  signal,
  template
}: RenderWasmVideoOptions): Promise<VideoRenderResult> {
  const files = [
    ...mediaSource.files,
    {
      data: template.data,
      path: template.path
    }
  ];
  const cleanupPaths = [...files.map((file) => file.path), outputPath];

  await ffmpegWasmAdapter.writeFiles(files, signal);

  try {
    await executeWithFallbacks(
      [
        buildH264Command(mediaSource, template, quality, "copy"),
        buildH264Command(mediaSource, template, quality, "aac"),
        buildMpeg4Command(mediaSource, template)
      ],
      {
        onProgress,
        signal
      }
    );

    const data = await ffmpegWasmAdapter.readFile(outputPath, signal);
    const outputData = new Uint8Array(data.byteLength);
    outputData.set(data);
    const blob = new Blob([outputData.buffer], { type: "video/mp4" });

    return {
      blob,
      filenameExtension: "mp4",
      mimeType: "video/mp4",
      renderer: "wasm-ffmpeg"
    };
  } finally {
    await ffmpegWasmAdapter.deleteFiles(cleanupPaths);
  }
}

export async function loadWasmFfmpegRenderer(signal: AbortSignal | undefined): Promise<void> {
  await ffmpegWasmAdapter.load(signal);
}

async function executeWithFallbacks(
  commands: string[][],
  options: {
    onProgress?: (progress: number) => void;
    signal?: AbortSignal;
  }
): Promise<void> {
  let lastError: unknown;

  for (const command of commands) {
    try {
      await ffmpegWasmAdapter.execute(command, options);
      return;
    } catch (error) {
      if (!(error instanceof VideoRenderError) || error.code !== "FFMPEG_FAILED") {
        throw error;
      }

      lastError = error;
    }
  }

  throw lastError;
}

function buildH264Command(
  mediaSource: VideoRenderMediaSource,
  template: VideoTemplateAsset,
  quality: VideoRenderQuality,
  audioMode: AudioMode
): string[] {
  const preset = getQualityPreset(quality);

  return [
    ...buildInputArgs(mediaSource, template),
    "-filter_complex",
    buildVideoFilter(mediaSource, template),
    "-map",
    "[v]",
    "-map",
    `${getAudioInputIndex(mediaSource)}:a?`,
    "-c:v",
    "libx264",
    "-preset",
    preset.preset,
    "-crf",
    String(preset.crf),
    ...buildAudioArgs(audioMode),
    "-movflags",
    "+faststart",
    "-shortest",
    outputPath
  ];
}

function buildMpeg4Command(mediaSource: VideoRenderMediaSource, template: VideoTemplateAsset): string[] {
  return [
    ...buildInputArgs(mediaSource, template),
    "-filter_complex",
    buildVideoFilter(mediaSource, template),
    "-map",
    "[v]",
    "-map",
    `${getAudioInputIndex(mediaSource)}:a?`,
    "-c:v",
    "mpeg4",
    "-q:v",
    "4",
    ...buildAudioArgs("aac"),
    "-movflags",
    "+faststart",
    "-shortest",
    outputPath
  ];
}

function buildAudioArgs(audioMode: AudioMode): string[] {
  if (audioMode === "copy") {
    return ["-c:a", "copy"];
  }

  return ["-c:a", "aac", "-b:a", "128k"];
}

function buildInputArgs(mediaSource: VideoRenderMediaSource, template: VideoTemplateAsset): string[] {
  const args = ["-i", mediaSource.videoInputPath];

  if (mediaSource.audioInputPath) {
    args.push("-i", mediaSource.audioInputPath);
  }

  args.push("-loop", "1", "-i", template.path);

  return args;
}

function buildVideoFilter(mediaSource: VideoRenderMediaSource, template: VideoTemplateAsset): string {
  const { height, width, x, y } = template.mediaRect;
  const templateInputIndex = getTemplateInputIndex(mediaSource);
  const sourceFilter = template.sourceCrop ? `crop=${buildSourceCropExpression(template.sourceCrop)},` : "";

  return [
    `[0:v]${sourceFilter}scale=${width}:${height}:force_original_aspect_ratio=increase,crop=${width}:${height},setsar=1[media]`,
    `color=c=black@0:s=${template.width}x${template.height},format=rgba[base]`,
    `[base][media]overlay=${x}:${y}:shortest=1[media_canvas]`,
    `[media_canvas][${templateInputIndex}:v]overlay=0:0:shortest=1,format=yuv420p[v]`
  ].join(";");
}

function buildSourceCropExpression(crop: NonNullable<VideoTemplateAsset["sourceCrop"]>): string {
  return [
    `w=trunc(iw*${formatCropValue(crop.width)}/2)*2`,
    `h=trunc(ih*${formatCropValue(crop.height)}/2)*2`,
    `x=trunc(iw*${formatCropValue(crop.x)}/2)*2`,
    `y=trunc(ih*${formatCropValue(crop.y)}/2)*2`
  ].join(":");
}

function formatCropValue(value: number): string {
  return Math.max(0, Math.min(1, value)).toFixed(6);
}

function getTemplateInputIndex(mediaSource: VideoRenderMediaSource): number {
  return mediaSource.audioInputPath ? 2 : 1;
}

function getAudioInputIndex(mediaSource: VideoRenderMediaSource): number {
  return mediaSource.audioInputPath ? 1 : 0;
}

function getQualityPreset(quality: VideoRenderQuality): QualityPreset {
  if (quality === "fast") {
    return {
      crf: 23,
      preset: "ultrafast"
    };
  }

  if (quality === "high") {
    return {
      crf: 18,
      preset: "superfast"
    };
  }

  return {
    crf: 19,
    preset: "ultrafast"
  };
}
