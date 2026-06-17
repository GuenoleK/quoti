import { toBlob } from "html-to-image";
import type { VideoTemplateAsset } from "../video-render.types";
import { VideoRenderError } from "../video-render.types";

const videoTemplatePixelRatio = 1.5;

type PreparedVideoTemplateNode = {
  mediaElement: HTMLElement;
  videoFrame: HTMLElement;
};

type ElementRect = {
  height: number;
  left: number;
  top: number;
  width: number;
};

export async function renderVideoTemplateAsset(templateNode: HTMLElement): Promise<VideoTemplateAsset> {
  await document.fonts?.ready;

  const exportNode = templateNode.cloneNode(true) as HTMLElement;
  const host = document.createElement("div");
  const { mediaElement, videoFrame } = prepareVideoTemplateNode(exportNode);

  exportNode.classList.remove("post-card-preview__card");
  enforceQuotiMark(exportNode);
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
    await waitForImages(exportNode);

    const initialCardRect = exportNode.getBoundingClientRect();
    const initialMediaRect = getBoundedMediaRect(initialCardRect, getUsableMediaRect(mediaElement));

    lockMediaElementSize(mediaElement, initialMediaRect);
    const sourceCrop = getVideoSourceCrop(videoFrame);
    videoFrame.replaceChildren(createVideoPlaceholder());
    await waitForFrame();

    const cardRect = exportNode.getBoundingClientRect();
    const mediaRect = getBoundedMediaRect(cardRect, mediaElement.getBoundingClientRect());
    const mediaStyle = window.getComputedStyle(mediaElement);
    const templateSize = {
      height: makeEven(Math.round(cardRect.height * videoTemplatePixelRatio)),
      width: makeEven(Math.round(cardRect.width * videoTemplatePixelRatio))
    };
    const processedMediaRect = {
      height: Math.max(2, Math.round(mediaRect.height * videoTemplatePixelRatio)),
      radius: Math.round(readPixelValue(mediaStyle.borderTopLeftRadius) * videoTemplatePixelRatio),
      width: Math.max(2, Math.round(mediaRect.width * videoTemplatePixelRatio)),
      x: Math.max(0, Math.round((mediaRect.left - cardRect.left) * videoTemplatePixelRatio)),
      y: Math.max(0, Math.round((mediaRect.top - cardRect.top) * videoTemplatePixelRatio))
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
      mediaRect: processedMediaRect,
      size: templateSize
    });

    return {
      data,
      height: templateSize.height,
      mediaRect: processedMediaRect,
      path: "quoti-template.png",
      sourceCrop,
      width: templateSize.width
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

function prepareVideoTemplateNode(exportNode: HTMLElement): PreparedVideoTemplateNode {
  const mediaElement =
    exportNode.querySelector<HTMLElement>('.context-card__media[data-media-type="video"]') ??
    exportNode.querySelector<HTMLElement>(".context-card__media");

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

  return {
    mediaElement,
    videoFrame
  };
}

function enforceQuotiMark(exportNode: HTMLElement): void {
  const mark = exportNode.querySelector<HTMLElement>(".context-card__mark");

  if (!mark) {
    return;
  }

  mark.textContent = "Quoti";
  mark.removeAttribute("hidden");
  mark.style.display = "";
  mark.style.visibility = "";
  mark.style.opacity = "";
}

function lockMediaElementSize(mediaElement: HTMLElement, mediaRect: ElementRect): void {
  const width = Math.max(2, Math.round(mediaRect.width));
  const height = Math.max(2, Math.round(mediaRect.height));

  mediaElement.style.width = `${width}px`;
  mediaElement.style.maxWidth = "100%";
  mediaElement.style.aspectRatio = `${width} / ${height}`;
}

function getUsableMediaRect(mediaElement: HTMLElement): DOMRect {
  const rect = mediaElement.getBoundingClientRect();

  if (rect.width >= 80 && rect.height >= 80) {
    return rect;
  }

  const fallbackWidth = Math.max(320, Math.round(mediaElement.parentElement?.getBoundingClientRect().width ?? 0));
  const fallbackHeight = Math.round(fallbackWidth * 9 / 16);

  mediaElement.style.width = `${fallbackWidth}px`;
  mediaElement.style.aspectRatio = `${fallbackWidth} / ${fallbackHeight}`;
  mediaElement.style.minHeight = `${fallbackHeight}px`;

  const videoFrame = mediaElement.querySelector<HTMLElement>(".context-card__video-frame");

  if (videoFrame) {
    videoFrame.style.minHeight = `${fallbackHeight}px`;
  }

  return mediaElement.getBoundingClientRect();
}

function getBoundedMediaRect(cardRect: DOMRect, mediaRect: DOMRect): ElementRect {
  const left = Math.max(mediaRect.left, cardRect.left);
  const top = Math.max(mediaRect.top, cardRect.top);
  const right = Math.min(mediaRect.right, cardRect.right);
  const bottom = Math.min(mediaRect.bottom, cardRect.bottom);

  return {
    height: Math.max(2, bottom - top),
    left,
    top,
    width: Math.max(2, right - left)
  };
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

function getVideoSourceCrop(videoFrame: HTMLElement): VideoTemplateAsset["sourceCrop"] {
  const posterProbe = videoFrame.querySelector<HTMLImageElement>(".context-card__video-probe");

  if (!posterProbe?.naturalWidth || !posterProbe.naturalHeight) {
    return undefined;
  }

  try {
    const canvas = document.createElement("canvas");
    const context = canvas.getContext("2d", { willReadFrequently: true });

    if (!context) {
      return undefined;
    }

    canvas.width = posterProbe.naturalWidth;
    canvas.height = posterProbe.naturalHeight;
    context.drawImage(posterProbe, 0, 0);

    const pixels = context.getImageData(0, 0, canvas.width, canvas.height).data;
    const left = findFirstContentColumn(pixels, canvas.width, canvas.height);
    const right = findFirstContentColumn(pixels, canvas.width, canvas.height, true);
    const top = findFirstContentRow(pixels, canvas.width, canvas.height);
    const bottom = findFirstContentRow(pixels, canvas.width, canvas.height, true);
    const cropWidth = right >= left ? right - left + 1 : canvas.width;
    const cropHeight = bottom >= top ? bottom - top + 1 : canvas.height;

    if (!shouldCropAxis(cropWidth, canvas.width) && !shouldCropAxis(cropHeight, canvas.height)) {
      return undefined;
    }

    return {
      height: cropHeight / canvas.height,
      width: cropWidth / canvas.width,
      x: left / canvas.width,
      y: top / canvas.height
    };
  } catch {
    return undefined;
  }
}

function shouldCropAxis(contentSize: number, totalSize: number): boolean {
  const ratio = contentSize / totalSize;

  return ratio <= 0.92 && ratio >= 0.35;
}

function findFirstContentRow(pixels: Uint8ClampedArray, width: number, height: number, reverse = false): number {
  for (let step = 0; step < height; step += 1) {
    const row = reverse ? height - 1 - step : step;

    if (!isDarkRow(pixels, width, row)) {
      return row;
    }
  }

  return reverse ? height - 1 : 0;
}

function findFirstContentColumn(pixels: Uint8ClampedArray, width: number, height: number, reverse = false): number {
  for (let step = 0; step < width; step += 1) {
    const column = reverse ? width - 1 - step : step;

    if (!isDarkColumn(pixels, width, height, column)) {
      return column;
    }
  }

  return reverse ? width - 1 : 0;
}

function isDarkRow(pixels: Uint8ClampedArray, width: number, row: number): boolean {
  let darkPixels = 0;
  const offset = row * width * 4;

  for (let column = 0; column < width; column += 1) {
    if (isDarkPixel(pixels, offset + column * 4)) {
      darkPixels += 1;
    }
  }

  return darkPixels / width > 0.92;
}

function isDarkColumn(pixels: Uint8ClampedArray, width: number, height: number, column: number): boolean {
  let darkPixels = 0;

  for (let row = 0; row < height; row += 1) {
    if (isDarkPixel(pixels, (row * width + column) * 4)) {
      darkPixels += 1;
    }
  }

  return darkPixels / height > 0.92;
}

function isDarkPixel(pixels: Uint8ClampedArray, index: number): boolean {
  const alpha = pixels[index + 3];
  const red = pixels[index];
  const green = pixels[index + 1];
  const blue = pixels[index + 2];

  return alpha < 8 || (red < 18 && green < 18 && blue < 18);
}

async function createVideoTemplateOverlayData(
  blob: Blob,
  options: {
    borderColor: string;
    borderWidth: number;
    mediaRect: VideoTemplateAsset["mediaRect"];
    size: {
      height: number;
      width: number;
    };
  }
): Promise<Uint8Array> {
  const bitmap = await createImageBitmap(blob);
  const canvas = document.createElement("canvas");
  const context = canvas.getContext("2d");

  if (!context) {
    throw new VideoRenderError("TEMPLATE_RENDER_FAILED", "Canvas export is not available.");
  }

  canvas.width = options.size.width;
  canvas.height = options.size.height;
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

function makeEven(value: number): number {
  return value % 2 === 0 ? value : value - 1;
}

function getExportWidth(node: HTMLElement): number {
  const layout = node.dataset.cardLayout;

  if (layout === "wide") {
    return 920;
  }

  if (layout === "square") {
    return 690;
  }

  if (layout === "compact") {
    return 500;
  }

  return 600;
}

function waitForFrame(): Promise<void> {
  return new Promise((resolve) => window.requestAnimationFrame(() => resolve()));
}

function waitForImages(node: HTMLElement): Promise<void> {
  const images = Array.from(node.querySelectorAll<HTMLImageElement>("img"));

  return Promise.all(
    images.map(
      (image) =>
        new Promise<void>((resolve) => {
          if (image.complete) {
            resolve();
            return;
          }

          image.addEventListener("load", () => resolve(), { once: true });
          image.addEventListener("error", () => resolve(), { once: true });
        })
    )
  ).then(() => undefined);
}
