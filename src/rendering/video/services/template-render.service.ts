import { toBlob } from "html-to-image";
import type { VideoTemplateAsset } from "../video-render.types";
import { VideoRenderError } from "../video-render.types";

const videoTemplatePixelRatio = 1.5;

export async function renderVideoTemplateAsset(templateNode: HTMLElement): Promise<VideoTemplateAsset> {
  await document.fonts?.ready;

  const exportNode = templateNode.cloneNode(true) as HTMLElement;
  const host = document.createElement("div");
  const mediaElement = prepareVideoTemplateNode(exportNode);

  exportNode.classList.remove("post-card-preview__card");
  exportNode.style.width = "100%";
  exportNode.style.maxWidth = "none";

  if (exportNode.dataset.cardLayout === "wide") {
    exportNode.dataset.cardLayout = "portrait";
  }

  host.style.position = "fixed";
  host.style.left = "-10000px";
  host.style.top = "0";
  host.style.width = `${getExportWidth(exportNode)}px`;
  host.style.pointerEvents = "none";
  host.append(exportNode);
  document.body.append(host);

  try {
    await waitForFrame();

    const cardRect = exportNode.getBoundingClientRect();
    const mediaRect = mediaElement.getBoundingClientRect();
    const mediaStyle = window.getComputedStyle(mediaElement);
    const processedMediaRect = {
      height: Math.max(2, Math.round(mediaRect.height * videoTemplatePixelRatio)),
      radius: Math.round(readPixelValue(mediaStyle.borderTopLeftRadius) * videoTemplatePixelRatio),
      width: Math.max(2, Math.round(mediaRect.width * videoTemplatePixelRatio)),
      x: Math.round((mediaRect.left - cardRect.left) * videoTemplatePixelRatio),
      y: Math.round((mediaRect.top - cardRect.top) * videoTemplatePixelRatio)
    };
    const blob = await toBlob(exportNode, {
      cacheBust: false,
      pixelRatio: videoTemplatePixelRatio
    });

    if (!blob) {
      throw new VideoRenderError("TEMPLATE_RENDER_FAILED", "Could not generate the video template image.");
    }

    const data = await createVideoTemplateOverlayData(blob, {
      borderColor: mediaStyle.borderColor,
      borderWidth: readPixelValue(mediaStyle.borderTopWidth) * videoTemplatePixelRatio,
      mediaRect: processedMediaRect
    });

    return {
      data,
      height: Math.round(cardRect.height * videoTemplatePixelRatio),
      mediaRect: processedMediaRect,
      path: "quoti-template.png",
      width: Math.round(cardRect.width * videoTemplatePixelRatio)
    };
  } catch (error) {
    if (error instanceof VideoRenderError) {
      throw error;
    }

    throw new VideoRenderError("TEMPLATE_RENDER_FAILED", "Could not generate the video template image.", error);
  } finally {
    host.remove();
  }
}

function prepareVideoTemplateNode(exportNode: HTMLElement): HTMLElement {
  const mediaElement = exportNode.querySelector<HTMLElement>(".context-card__media");

  if (!mediaElement) {
    throw new VideoRenderError("TEMPLATE_RENDER_FAILED", "The card template does not contain a media frame.");
  }

  exportNode.dataset.cardContentMode = "with-media";
  mediaElement.hidden = false;
  mediaElement.removeAttribute("hidden");

  const videoFrame = mediaElement.querySelector<HTMLElement>(".context-card__video-frame");

  if (!videoFrame) {
    throw new VideoRenderError("TEMPLATE_RENDER_FAILED", "The card template does not contain a video frame.");
  }

  videoFrame.replaceChildren(createVideoPlaceholder());

  return mediaElement;
}

function createVideoPlaceholder(): HTMLDivElement {
  const placeholder = document.createElement("div");

  placeholder.className = "context-card__video-placeholder";
  placeholder.style.background = "#000";
  placeholder.style.borderRadius = "inherit";
  placeholder.style.display = "block";
  placeholder.style.height = "100%";
  placeholder.style.width = "100%";

  return placeholder;
}

async function createVideoTemplateOverlayData(
  blob: Blob,
  options: {
    borderColor: string;
    borderWidth: number;
    mediaRect: VideoTemplateAsset["mediaRect"];
  }
): Promise<Uint8Array> {
  const bitmap = await createImageBitmap(blob);
  const canvas = document.createElement("canvas");
  const context = canvas.getContext("2d");

  if (!context) {
    throw new VideoRenderError("TEMPLATE_RENDER_FAILED", "Canvas export is not available.");
  }

  canvas.width = bitmap.width;
  canvas.height = bitmap.height;
  context.drawImage(bitmap, 0, 0);
  bitmap.close();

  context.save();
  context.globalCompositeOperation = "destination-out";
  roundedPath(context, options.mediaRect.x, options.mediaRect.y, options.mediaRect.width, options.mediaRect.height, options.mediaRect.radius);
  context.fill();
  context.restore();

  if (options.borderWidth > 0) {
    context.save();
    context.strokeStyle = options.borderColor;
    context.lineWidth = options.borderWidth;
    roundedPath(
      context,
      options.mediaRect.x + options.borderWidth / 2,
      options.mediaRect.y + options.borderWidth / 2,
      options.mediaRect.width - options.borderWidth,
      options.mediaRect.height - options.borderWidth,
      Math.max(0, options.mediaRect.radius - options.borderWidth / 2)
    );
    context.stroke();
    context.restore();
  }

  const overlayBlob = await canvasToBlob(canvas);
  const overlayBuffer = await overlayBlob.arrayBuffer();

  return new Uint8Array(overlayBuffer);
}

function canvasToBlob(canvas: HTMLCanvasElement): Promise<Blob> {
  return new Promise((resolve, reject) => {
    canvas.toBlob((blob) => {
      if (!blob) {
        reject(new VideoRenderError("TEMPLATE_RENDER_FAILED", "Could not generate the rounded video template."));
        return;
      }

      resolve(blob);
    }, "image/png");
  });
}

function roundedPath(context: CanvasRenderingContext2D, x: number, y: number, width: number, height: number, radius: number): void {
  const safeRadius = Math.min(radius, width / 2, height / 2);

  context.beginPath();
  context.moveTo(x + safeRadius, y);
  context.arcTo(x + width, y, x + width, y + height, safeRadius);
  context.arcTo(x + width, y + height, x, y + height, safeRadius);
  context.arcTo(x, y + height, x, y, safeRadius);
  context.arcTo(x, y, x + width, y, safeRadius);
  context.closePath();
}

function readPixelValue(value: string): number {
  const parsed = Number.parseFloat(value);

  return Number.isFinite(parsed) ? parsed : 0;
}

function getExportWidth(node: HTMLElement): number {
  const layout = node.dataset.cardLayout;

  if (layout === "wide") {
    return 1080;
  }

  if (layout === "square") {
    return 820;
  }

  return 720;
}

function waitForFrame(): Promise<void> {
  return new Promise((resolve) => window.requestAnimationFrame(() => resolve()));
}
