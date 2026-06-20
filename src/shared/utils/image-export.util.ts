import { toPng } from "html-to-image";

const pixelRatio = 2;

export async function exportNodeToPngDataUrl(node: HTMLElement): Promise<string> {
  return withExportNode(node, (exportNode) => withExportReadyImages(exportNode, async () => {
    await waitForNodeAssets(exportNode);

    const dataUrl = await toPng(exportNode, {
      cacheBust: false,
      pixelRatio
    });

    return clipPngDataUrlToNodeRadius(dataUrl, exportNode);
  }));
}

export async function exportNodeToPngBlob(node: HTMLElement): Promise<Blob> {
  return withExportNode(node, (exportNode) => withExportReadyImages(exportNode, async () => {
    await waitForNodeAssets(exportNode);

    const dataUrl = await toPng(exportNode, {
      cacheBust: false,
      pixelRatio
    });
    return dataUrlToBlob(await clipPngDataUrlToNodeRadius(dataUrl, exportNode));
  }));
}

export async function dataUrlToBlob(dataUrl: string): Promise<Blob> {
  const [metadata, payload] = dataUrl.split(",");

  if (!metadata || payload === undefined) {
    throw new Error("Invalid image data URL.");
  }

  const mimeType = /^data:([^;,]+)/.exec(metadata)?.[1] ?? "application/octet-stream";
  const isBase64 = metadata.endsWith(";base64");
  const binary = isBase64 ? atob(payload) : decodeURIComponent(payload);
  const bytes = new Uint8Array(binary.length);

  for (let index = 0; index < binary.length; index += 1) {
    bytes[index] = binary.charCodeAt(index);
  }

  return new Blob([bytes], { type: mimeType });
}

export function downloadDataUrl(dataUrl: string, filename: string): void {
  const link = document.createElement("a");
  link.href = dataUrl;
  link.download = filename;
  link.click();
}

async function waitForNodeAssets(node: HTMLElement): Promise<void> {
  await document.fonts?.ready;

  const images = Array.from(node.querySelectorAll("img"));

  await Promise.all(
    images.map((image) => {
      if (image.complete) {
        return Promise.resolve();
      }

      return new Promise<void>((resolve) => {
        image.addEventListener("load", () => resolve(), { once: true });
        image.addEventListener("error", () => resolve(), { once: true });
      });
    })
  );
}

async function withExportNode<T>(node: HTMLElement, callback: (exportNode: HTMLElement) => Promise<T>): Promise<T> {
  const exportNode = node.cloneNode(true) as HTMLElement;
  const host = document.createElement("div");

  prepareStaticMediaForExport(node, exportNode);
  enforceQuotiMark(exportNode);
  exportNode.classList.remove("post-card-preview__card");
  exportNode.style.width = "100%";
  exportNode.style.maxWidth = "none";

  host.style.position = "fixed";
  host.style.left = "-10000px";
  host.style.top = "0";
  host.style.width = `${getExportWidth(exportNode)}px`;
  host.style.background = "transparent";
  host.style.pointerEvents = "none";
  host.append(exportNode);
  document.body.append(host);

  try {
    return await callback(exportNode);
  } finally {
    host.remove();
  }
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

function prepareStaticMediaForExport(sourceNode: HTMLElement, exportNode: HTMLElement): void {
  const hasVideo = Boolean(exportNode.querySelector(".context-card__video"));

  if (!hasVideo) {
    return;
  }

  replaceExportVideosWithImages(sourceNode, exportNode);
  exportNode.querySelectorAll(".context-card__video-status, .context-card__video-probe").forEach((element) => element.remove());

  if (exportNode.dataset.cardLayout === "wide") {
    exportNode.dataset.cardLayout = "portrait";
  }
}

function replaceExportVideosWithImages(sourceNode: HTMLElement, exportNode: HTMLElement): void {
  const sourceVideos = Array.from(sourceNode.querySelectorAll<HTMLVideoElement>("video"));
  const exportVideos = Array.from(exportNode.querySelectorAll<HTMLVideoElement>("video"));

  exportVideos.forEach((exportVideo, index) => {
    exportVideo.replaceWith(createVideoSnapshotImage(sourceVideos[index], exportVideo));
  });
}

function createVideoSnapshotImage(sourceVideo: HTMLVideoElement | undefined, exportVideo: HTMLVideoElement): HTMLImageElement {
  const image = document.createElement("img");
  const source = findVideoPosterSource(sourceVideo, exportVideo) ?? captureVideoFrameAsDataUrl(sourceVideo);

  image.alt = exportVideo.getAttribute("aria-label") ?? "";
  image.className = "context-card__image context-card__video-snapshot";
  image.decoding = "async";
  image.loading = "eager";
  image.referrerPolicy = "no-referrer";
  image.style.display = "block";
  image.style.width = "100%";
  image.style.height = "100%";
  image.style.maxHeight = "none";
  image.style.objectFit = "cover";

  if (source) {
    image.src = source;

    if (!source.startsWith("data:") && !source.startsWith("blob:")) {
      image.dataset.exportSrc = source;
    }
  }

  return image;
}

function findVideoPosterSource(sourceVideo: HTMLVideoElement | undefined, exportVideo: HTMLVideoElement): string | undefined {
  const videoFrame = exportVideo.closest(".context-card__video-frame");
  const posterProbe = videoFrame?.querySelector<HTMLImageElement>(".context-card__video-probe");

  return (
    getImageSource(posterProbe?.dataset.exportSrc) ??
    getImageSource(posterProbe?.currentSrc) ??
    getImageSource(posterProbe?.src) ??
    getImageSource(sourceVideo?.getAttribute("poster")) ??
    getImageSource(exportVideo.getAttribute("poster"))
  );
}

function getImageSource(source: string | null | undefined): string | undefined {
  return source && source.trim() ? source : undefined;
}

function captureVideoFrameAsDataUrl(video: HTMLVideoElement | undefined): string | undefined {
  if (!video || video.readyState < HTMLMediaElement.HAVE_CURRENT_DATA || !video.videoWidth || !video.videoHeight) {
    return undefined;
  }

  try {
    const canvas = document.createElement("canvas");
    const context = canvas.getContext("2d");

    if (!context) {
      return undefined;
    }

    canvas.width = video.videoWidth;
    canvas.height = video.videoHeight;
    context.drawImage(video, 0, 0, canvas.width, canvas.height);

    return canvas.toDataURL("image/png");
  } catch {
    return undefined;
  }
}

function getExportWidth(node: HTMLElement): number {
  const layout = node.dataset.cardLayout;

  if (layout === "wide") {
    return 1080;
  }

  if (layout === "square") {
    return 820;
  }

  if (layout === "compact") {
    return 500;
  }

  return 540;
}

async function clipPngDataUrlToNodeRadius(dataUrl: string, node: HTMLElement): Promise<string> {
  const radius = getNodeBorderRadius(node);

  if (radius <= 0) {
    return dataUrl;
  }

  const image = await loadImage(dataUrl);
  const canvas = document.createElement("canvas");
  const context = canvas.getContext("2d");

  if (!context) {
    throw new Error("Could not prepare PNG transparency.");
  }

  const bounds = node.getBoundingClientRect();
  const scale = bounds.width > 0 ? image.naturalWidth / bounds.width : pixelRatio;

  canvas.width = image.naturalWidth;
  canvas.height = image.naturalHeight;
  context.drawImage(image, 0, 0);
  context.globalCompositeOperation = "destination-in";
  context.fillStyle = "#000";
  context.beginPath();
  addRoundedRectPath(context, 0, 0, canvas.width, canvas.height, radius * scale);
  context.fill();

  return canvas.toDataURL("image/png");
}

function getNodeBorderRadius(node: HTMLElement): number {
  return parseFloat(getComputedStyle(node).borderTopLeftRadius) || 0;
}

function addRoundedRectPath(context: CanvasRenderingContext2D, x: number, y: number, width: number, height: number, radius: number): void {
  const resolvedRadius = Math.min(radius, width / 2, height / 2);

  context.moveTo(x + resolvedRadius, y);
  context.lineTo(x + width - resolvedRadius, y);
  context.quadraticCurveTo(x + width, y, x + width, y + resolvedRadius);
  context.lineTo(x + width, y + height - resolvedRadius);
  context.quadraticCurveTo(x + width, y + height, x + width - resolvedRadius, y + height);
  context.lineTo(x + resolvedRadius, y + height);
  context.quadraticCurveTo(x, y + height, x, y + height - resolvedRadius);
  context.lineTo(x, y + resolvedRadius);
  context.quadraticCurveTo(x, y, x + resolvedRadius, y);
  context.closePath();
}

function loadImage(dataUrl: string): Promise<HTMLImageElement> {
  const image = new Image();

  image.decoding = "async";
  image.src = dataUrl;

  if (image.complete) {
    return Promise.resolve(image);
  }

  return new Promise((resolve, reject) => {
    image.addEventListener("load", () => resolve(image), { once: true });
    image.addEventListener("error", () => reject(new Error("Could not load generated PNG.")), { once: true });
  });
}

type ImageSnapshot = {
  crossOrigin: string | null;
  image: HTMLImageElement;
  sizes: string | null;
  src: string | null;
  srcset: string | null;
};

async function withExportReadyImages<T>(node: HTMLElement, callback: () => Promise<T>): Promise<T> {
  const snapshots = await inlineExportImages(node);

  try {
    return await callback();
  } finally {
    restoreImages(snapshots);
  }
}

async function inlineExportImages(node: HTMLElement): Promise<ImageSnapshot[]> {
  const images = Array.from(node.querySelectorAll<HTMLImageElement>("img"));

  const snapshots = await Promise.all(
    images.map(async (image) => {
      const source = image.dataset.exportSrc || image.currentSrc || image.src;

      if (!source || source.startsWith("data:") || source.startsWith("blob:")) {
        return null;
      }

      const snapshot: ImageSnapshot = {
        crossOrigin: image.getAttribute("crossorigin"),
        image,
        sizes: image.getAttribute("sizes"),
        src: image.getAttribute("src"),
        srcset: image.getAttribute("srcset")
      };

      try {
        const dataUrl = await fetchImageAsDataUrl(source);

        image.removeAttribute("srcset");
        image.removeAttribute("sizes");
        image.removeAttribute("crossorigin");
        image.src = dataUrl;
        await waitForImage(image);

        return snapshot;
      } catch {
        return null;
      }
    })
  );

  return snapshots.filter((snapshot): snapshot is ImageSnapshot => Boolean(snapshot));
}

function restoreImages(snapshots: ImageSnapshot[]): void {
  snapshots.forEach((snapshot) => {
    restoreAttribute(snapshot.image, "crossorigin", snapshot.crossOrigin);
    restoreAttribute(snapshot.image, "sizes", snapshot.sizes);
    restoreAttribute(snapshot.image, "srcset", snapshot.srcset);
    restoreAttribute(snapshot.image, "src", snapshot.src);
  });
}

function restoreAttribute(element: HTMLElement, name: string, value: string | null): void {
  if (value === null) {
    element.removeAttribute(name);
    return;
  }

  element.setAttribute(name, value);
}

async function fetchImageAsDataUrl(source: string): Promise<string> {
  const response = await fetch(source, {
    cache: "force-cache",
    credentials: "omit",
    referrerPolicy: "no-referrer"
  });

  if (!response.ok) {
    throw new Error(`Could not load export image: ${response.status}`);
  }

  return blobToDataUrl(await response.blob());
}

function blobToDataUrl(blob: Blob): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();

    reader.addEventListener("load", () => resolve(String(reader.result)));
    reader.addEventListener("error", () => reject(reader.error));
    reader.readAsDataURL(blob);
  });
}

function waitForImage(image: HTMLImageElement): Promise<void> {
  if (image.complete) {
    return Promise.resolve();
  }

  return new Promise((resolve) => {
    image.addEventListener("load", () => resolve(), { once: true });
    image.addEventListener("error", () => resolve(), { once: true });
  });
}
