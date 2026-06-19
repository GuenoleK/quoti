import { formatPublishedDate } from "../../card-generator/card-generator";
import type { CardTheme, ExtractedPost } from "../types/post.types";

type VideoExportOptions = {
  cardTheme: CardTheme;
  post: ExtractedPost;
  video: HTMLVideoElement;
};

type CanvasTheme = {
  author: string;
  background: string;
  border: string;
  brand: string;
  muted: string;
  platformBackground: string;
  platformText: string;
  quote: string;
  surface: string;
};

const canvasWidth = 720;
const cardPadding = 48;
const frameRate = 18;
const mediaCornerRadius = 28;

type AudioCapture = {
  cleanup: () => void;
  stream: MediaStream;
};

type AudioSourceState = {
  context: AudioContext;
  outputConnected: boolean;
  source: MediaElementAudioSourceNode;
};

const audioSourceByVideo = new WeakMap<HTMLVideoElement, AudioSourceState>();

export async function exportPostVideoToWebmBlob({ cardTheme, post, video }: VideoExportOptions): Promise<Blob> {
  if (!Number.isFinite(video.duration) || video.duration <= 0) {
    throw new Error("The video is not ready yet.");
  }

  await document.fonts?.ready;
  await seekVideo(video, 0);

  const previousMuted = video.muted;
  const previousDefaultMuted = video.defaultMuted;
  const previousVolume = video.volume;
  let audioCapture: AudioCapture | null = null;

  video.muted = false;
  video.defaultMuted = false;

  if (video.volume === 0) {
    video.volume = 1;
  }

  try {
    const theme = getCanvasTheme(cardTheme);
    const authorAvatar = await loadCanvasImage(post.authorAvatarUrl);
    const metrics = measureCard(post, video, Boolean(authorAvatar));
    const canvas = document.createElement("canvas");
    canvas.width = canvasWidth;
    canvas.height = metrics.canvasHeight;

    const context = canvas.getContext("2d");

    if (!context) {
      throw new Error("Canvas export is not available.");
    }

    const stream = canvas.captureStream(frameRate);
    const captureSource = video as HTMLVideoElement & {
      captureStream?: () => MediaStream;
    };
    const videoStream = captureSource.captureStream?.();
    audioCapture = await createAudioCapture(video);

    if (audioCapture) {
      addAudioTracks(audioCapture.stream, stream);
    } else {
      await primeAudioTracks(video, videoStream);
      addAudioTracks(videoStream, stream);
    }

    await seekVideo(video, 0);

    const recorder = createRecorder(stream);
    const chunks: Blob[] = [];

    recorder.addEventListener("dataavailable", (event) => {
      if (event.data.size > 0) {
        chunks.push(event.data);
      }
    });

    const stopped = new Promise<void>((resolve, reject) => {
      recorder.addEventListener("stop", () => resolve(), { once: true });
      recorder.addEventListener("error", () => reject(new Error("Video export failed.")), { once: true });
    });

    renderVideoFrame(context, post, video, metrics, theme, authorAvatar);
    recorder.start(1000);
    await playVideo(video);

    const renderLoop = startRenderLoop(context, post, video, metrics, theme, authorAvatar);

    try {
      await waitForVideoEnd(video);
    } finally {
      renderLoop.stop();
      video.pause();

      if (recorder.state !== "inactive") {
        recorder.stop();
      }
    }

    await stopped;

    return new Blob(chunks, { type: recorder.mimeType || "video/webm" });
  } finally {
    audioCapture?.cleanup();
    video.pause();
    video.muted = previousMuted;
    video.defaultMuted = previousDefaultMuted;
    video.volume = previousVolume;

    await seekVideo(video, 0).catch(() => undefined);
  }
}

export function downloadBlob(blob: Blob, filename: string): void {
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = filename;
  link.click();

  window.setTimeout(() => URL.revokeObjectURL(url), 1000);
}

async function createAudioCapture(video: HTMLVideoElement): Promise<AudioCapture | null> {
  const AudioContextConstructor = window.AudioContext;

  if (!AudioContextConstructor) {
    return null;
  }

  try {
    const state = getAudioSourceState(video, AudioContextConstructor);

    if (state.context.state === "suspended") {
      await state.context.resume();
    }

    const destination = state.context.createMediaStreamDestination();
    state.source.connect(destination);

    if (!state.outputConnected) {
      state.source.connect(state.context.destination);
      state.outputConnected = true;
    }

    return {
      cleanup: () => {
        try {
          state.source.disconnect(destination);
        } catch {
          // The destination may already be disconnected by the browser.
        }
      },
      stream: destination.stream
    };
  } catch {
    return null;
  }
}

function getAudioSourceState(video: HTMLVideoElement, AudioContextConstructor: typeof AudioContext): AudioSourceState {
  const existing = audioSourceByVideo.get(video);

  if (existing && existing.context.state !== "closed") {
    return existing;
  }

  const context = new AudioContextConstructor();
  const state: AudioSourceState = {
    context,
    outputConnected: false,
    source: context.createMediaElementSource(video)
  };

  audioSourceByVideo.set(video, state);

  return state;
}

function addAudioTracks(source: MediaStream | undefined, target: MediaStream): void {
  if (!source) {
    return;
  }

  const addTrack = (track: MediaStreamTrack) => {
    if (track.kind !== "audio" || target.getAudioTracks().some((audioTrack) => audioTrack.id === track.id)) {
      return;
    }

    target.addTrack(track);
  };

  source.getAudioTracks().forEach(addTrack);
  source.addEventListener("addtrack", (event) => addTrack(event.track));
}

async function primeAudioTracks(video: HTMLVideoElement, stream: MediaStream | undefined): Promise<void> {
  if (!stream || stream.getAudioTracks().length > 0) {
    return;
  }

  const wasPaused = video.paused;

  try {
    await playVideo(video);
    await waitForAudioTrack(stream, 900);
  } catch {
    // Some X videos are genuinely video-only; export should still continue.
  } finally {
    if (wasPaused) {
      video.pause();
    }
  }
}

function waitForAudioTrack(stream: MediaStream, timeout: number): Promise<void> {
  if (stream.getAudioTracks().length > 0) {
    return Promise.resolve();
  }

  return new Promise((resolve) => {
    const timeoutId = window.setTimeout(() => {
      cleanup();
      resolve();
    }, timeout);
    const handleAddTrack = (event: MediaStreamTrackEvent) => {
      if (event.track.kind !== "audio") {
        return;
      }

      cleanup();
      resolve();
    };
    const cleanup = () => {
      window.clearTimeout(timeoutId);
      stream.removeEventListener("addtrack", handleAddTrack);
    };

    stream.addEventListener("addtrack", handleAddTrack);
  });
}

function loadCanvasImage(source: string | undefined): Promise<HTMLImageElement | null> {
  if (!source) {
    return Promise.resolve(null);
  }

  return new Promise((resolve) => {
    const image = new Image();

    image.crossOrigin = "anonymous";
    image.decoding = "async";
    image.referrerPolicy = "no-referrer";
    image.addEventListener("load", () => resolve(image), { once: true });
    image.addEventListener("error", () => resolve(null), { once: true });
    image.src = source;
  });
}

function createRecorder(stream: MediaStream): MediaRecorder {
  const mimeType = [
    "video/webm;codecs=vp9,opus",
    "video/webm;codecs=vp8,opus",
    "video/webm"
  ].find((candidate) => MediaRecorder.isTypeSupported(candidate));

  return new MediaRecorder(stream, mimeType ? { mimeType, videoBitsPerSecond: 3_500_000 } : undefined);
}

function startRenderLoop(
  context: CanvasRenderingContext2D,
  post: ExtractedPost,
  video: HTMLVideoElement,
  metrics: CardMetrics,
  theme: CanvasTheme,
  authorAvatar: HTMLImageElement | null
): { stop: () => void } {
  const interval = window.setInterval(() => {
    renderVideoFrame(context, post, video, metrics, theme, authorAvatar);
  }, 1000 / frameRate);

  renderVideoFrame(context, post, video, metrics, theme, authorAvatar);

  return {
    stop: () => {
      window.clearInterval(interval);
    }
  };
}

type CardMetrics = {
  authorHandleY: number;
  authorNameY: number;
  authorTextWidth: number;
  authorTextX: number;
  canvasHeight: number;
  cardHeight: number;
  cardWidth: number;
  contentX: number;
  contentWidth: number;
  dateY: number;
  footerY: number;
  mediaHeight: number;
  mediaWidth: number;
  mediaX: number;
  mediaY: number;
  quoteLines: string[];
  quoteY: number;
};

function measureCard(post: ExtractedPost, video: HTMLVideoElement, hasAuthorAvatar: boolean): CardMetrics {
  const cardWidth = canvasWidth;
  const contentX = cardPadding;
  const contentWidth = cardWidth - cardPadding * 2;
  const authorAvatarSlot = hasAuthorAvatar ? 56 : 0;
  const authorTextWidth = Math.max(180, Math.round(contentWidth * 0.56) - authorAvatarSlot);
  const authorTextX = contentX + authorAvatarSlot;
  const probe = document.createElement("canvas").getContext("2d") as CanvasRenderingContext2D;
  probe.font = "34px Georgia, serif";

  const quoteLines = wrapText(probe, post.content, contentWidth, 34).slice(0, 12);
  const quoteHeight = quoteLines.length * 42;
  const videoRatio = video.videoHeight && video.videoWidth ? video.videoHeight / video.videoWidth : 9 / 16;
  const maxMediaHeight = getVideoMediaMaxHeight(videoRatio) ?? Math.round(contentWidth * videoRatio);
  const mediaHeight = Math.min(Math.round(contentWidth * videoRatio), maxMediaHeight);
  const mediaWidth = Math.round(mediaHeight / videoRatio);
  const mediaX = contentX + Math.round((contentWidth - mediaWidth) / 2);
  const quoteY = cardPadding + 86;
  const mediaY = quoteY + quoteHeight + 34;
  const footerY = mediaY + mediaHeight + 58;
  const cardHeight = footerY + 92;

  return {
    authorHandleY: cardPadding + 50,
    authorNameY: cardPadding + 26,
    authorTextWidth,
    authorTextX,
    canvasHeight: cardHeight,
    cardHeight,
    cardWidth,
    contentX,
    contentWidth,
    dateY: cardPadding + 24,
    footerY,
    mediaHeight,
    mediaWidth,
    mediaX,
    mediaY,
    quoteLines,
    quoteY
  };
}

function renderVideoFrame(
  context: CanvasRenderingContext2D,
  post: ExtractedPost,
  video: HTMLVideoElement,
  metrics: CardMetrics,
  theme: CanvasTheme,
  authorAvatar: HTMLImageElement | null
): void {
  context.clearRect(0, 0, canvasWidth, metrics.canvasHeight);

  drawRoundRect(context, 0, 0, metrics.cardWidth, metrics.cardHeight, 32, theme.surface);
  strokeRoundRect(context, 0, 0, metrics.cardWidth, metrics.cardHeight, 32, theme.border);

  if (authorAvatar) {
    drawCircularImage(context, authorAvatar, metrics.contentX, cardPadding + 6, 44);
  }

  context.fillStyle = theme.muted;
  context.font = "500 15px Arial, sans-serif";
  context.textAlign = "right";
  context.fillText(formatPublishedDate(post.publishedAt), canvasWidth - cardPadding, metrics.dateY);

  context.textAlign = "left";
  context.fillStyle = theme.author;
  context.font = "700 18px Arial, sans-serif";
  drawEllipsizedText(context, post.authorName, metrics.authorTextX, metrics.authorNameY, metrics.authorTextWidth);

  if (post.authorHandle) {
    context.fillStyle = theme.muted;
    context.font = "15px Arial, sans-serif";
    drawEllipsizedText(context, post.authorHandle, metrics.authorTextX, metrics.authorHandleY, metrics.authorTextWidth);
  }

  context.fillStyle = theme.quote;
  context.font = "34px Georgia, serif";
  metrics.quoteLines.forEach((line, index) => {
    context.fillText(line, metrics.contentX, metrics.quoteY + index * 42);
  });

  drawRoundRect(context, metrics.mediaX, metrics.mediaY, metrics.mediaWidth, metrics.mediaHeight, mediaCornerRadius, "#000");
  context.save();
  roundedClip(context, metrics.mediaX, metrics.mediaY, metrics.mediaWidth, metrics.mediaHeight, mediaCornerRadius);
  context.drawImage(video, metrics.mediaX, metrics.mediaY, metrics.mediaWidth, metrics.mediaHeight);
  context.restore();

  context.strokeStyle = theme.border;
  context.lineWidth = 1;
  context.beginPath();
  context.moveTo(metrics.contentX, metrics.footerY);
  context.lineTo(metrics.contentX + metrics.contentWidth, metrics.footerY);
  context.stroke();

  drawPlatformMark(context, metrics.contentX, metrics.footerY + 20);

  context.fillStyle = theme.brand;
  context.font = "700 24px Georgia, serif";
  context.textAlign = "right";
  context.fillText("Quoti", metrics.contentX + metrics.contentWidth, metrics.footerY + 47);
  context.textAlign = "left";
}

function getVideoMediaMaxHeight(mediaRatio: number): number | undefined {
  if (mediaRatio >= 1.45) {
    return 560;
  }

  if (mediaRatio >= 1.18) {
    return 520;
  }

  return undefined;
}

function drawPlatformMark(context: CanvasRenderingContext2D, x: number, y: number): void {
  context.fillStyle = "#1f1a16";
  context.beginPath();
  context.arc(x + 18, y + 18, 18, 0, Math.PI * 2);
  context.fill();

  context.strokeStyle = "#fff";
  context.lineWidth = 1.5;
  context.beginPath();
  context.moveTo(x + 11, y + 10);
  context.lineTo(x + 25, y + 26);
  context.moveTo(x + 25, y + 10);
  context.lineTo(x + 11, y + 26);
  context.stroke();
}

function drawCircularImage(context: CanvasRenderingContext2D, image: HTMLImageElement, x: number, y: number, size: number): void {
  context.save();
  context.beginPath();
  context.arc(x + size / 2, y + size / 2, size / 2, 0, Math.PI * 2);
  context.clip();
  drawImageCover(context, image, x, y, size, size);
  context.restore();
}

function drawImageCover(context: CanvasRenderingContext2D, image: HTMLImageElement, x: number, y: number, width: number, height: number): void {
  const scale = Math.max(width / image.naturalWidth, height / image.naturalHeight);
  const scaledWidth = image.naturalWidth * scale;
  const scaledHeight = image.naturalHeight * scale;
  const left = x + (width - scaledWidth) / 2;
  const top = y + (height - scaledHeight) / 2;

  context.drawImage(image, left, top, scaledWidth, scaledHeight);
}

function drawEllipsizedText(context: CanvasRenderingContext2D, text: string, x: number, y: number, maxWidth: number): void {
  if (context.measureText(text).width <= maxWidth) {
    context.fillText(text, x, y);
    return;
  }

  const ellipsis = "...";
  let end = text.length;

  while (end > 0 && context.measureText(`${text.slice(0, end)}${ellipsis}`).width > maxWidth) {
    end -= 1;
  }

  context.fillText(`${text.slice(0, end)}${ellipsis}`, x, y);
}

function wrapText(context: CanvasRenderingContext2D, text: string, maxWidth: number, maxWordsPerLine: number): string[] {
  const words = text.split(/\s+/).filter(Boolean);
  const lines: string[] = [];
  let line = "";

  words.forEach((word) => {
    const candidate = line ? `${line} ${word}` : word;

    if (context.measureText(candidate).width <= maxWidth && candidate.split(/\s+/).length <= maxWordsPerLine) {
      line = candidate;
      return;
    }

    if (line) {
      lines.push(line);
    }

    line = word;
  });

  if (line) {
    lines.push(line);
  }

  return lines;
}

function drawRoundRect(context: CanvasRenderingContext2D, x: number, y: number, width: number, height: number, radius: number, fill: string): void {
  roundedPath(context, x, y, width, height, radius);
  context.fillStyle = fill;
  context.fill();
}

function strokeRoundRect(context: CanvasRenderingContext2D, x: number, y: number, width: number, height: number, radius: number, stroke: string): void {
  roundedPath(context, x, y, width, height, radius);
  context.strokeStyle = stroke;
  context.lineWidth = 1;
  context.stroke();
}

function roundedClip(context: CanvasRenderingContext2D, x: number, y: number, width: number, height: number, radius: number): void {
  roundedPath(context, x, y, width, height, radius);
  context.clip();
}

function roundedPath(context: CanvasRenderingContext2D, x: number, y: number, width: number, height: number, radius: number): void {
  context.beginPath();
  context.moveTo(x + radius, y);
  context.arcTo(x + width, y, x + width, y + height, radius);
  context.arcTo(x + width, y + height, x, y + height, radius);
  context.arcTo(x, y + height, x, y, radius);
  context.arcTo(x, y, x + width, y, radius);
  context.closePath();
}

function getCanvasTheme(cardTheme: CardTheme): CanvasTheme {
  if (cardTheme === "dark") {
    return {
      author: "#f6efe4",
      background: "#171411",
      border: "#3a3028",
      brand: "#d8a46f",
      muted: "#b5a99d",
      platformBackground: "#f6efe4",
      platformText: "#171411",
      quote: "#f6efe4",
      surface: "#211c18"
    };
  }

  return {
    author: "#15110d",
    background: "#ebe2d6",
    border: "#ddd1c3",
    brand: "#7b3f22",
    muted: "#7a6d5f",
    platformBackground: "#1f1a16",
    platformText: "#fff",
    quote: "#15110d",
    surface: "#fffaf2"
  };
}

function playVideo(video: HTMLVideoElement): Promise<void> {
  const promise = video.play();

  return promise instanceof Promise ? promise : Promise.resolve();
}

function waitForVideoEnd(video: HTMLVideoElement): Promise<void> {
  if (video.ended) {
    return Promise.resolve();
  }

  return new Promise((resolve) => {
    video.addEventListener("ended", () => resolve(), { once: true });
  });
}

function seekVideo(video: HTMLVideoElement, currentTime: number): Promise<void> {
  return new Promise((resolve) => {
    const handleSeeked = () => resolve();

    video.addEventListener("seeked", handleSeeked, { once: true });
    video.currentTime = Math.min(currentTime, Math.max(0, video.duration - 0.05));

    if (Math.abs(video.currentTime - currentTime) < 0.02) {
      video.removeEventListener("seeked", handleSeeked);
      resolve();
    }
  });
}
