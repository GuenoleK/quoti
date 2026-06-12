import { toBlob, toJpeg, toPng } from "html-to-image";

const pixelRatio = 2;

export async function exportNodeToPngDataUrl(node: HTMLElement): Promise<string> {
  return withExportNode(node, (exportNode) => withExportReadyImages(exportNode, async () => {
    await waitForNodeAssets(exportNode);

    return toPng(exportNode, {
      cacheBust: false,
      pixelRatio
    });
  }));
}

export async function exportNodeToJpegDataUrl(node: HTMLElement): Promise<string> {
  return withExportNode(node, (exportNode) => withExportReadyImages(exportNode, async () => {
    await waitForNodeAssets(exportNode);

    return toJpeg(exportNode, {
      cacheBust: false,
      pixelRatio,
      quality: 0.95
    });
  }));
}

export async function exportNodeToPngBlob(node: HTMLElement): Promise<Blob> {
  return withExportNode(node, (exportNode) => withExportReadyImages(exportNode, async () => {
    await waitForNodeAssets(exportNode);

    const blob = await toBlob(exportNode, {
      cacheBust: false,
      pixelRatio
    });

    if (!blob) {
      throw new Error("Could not generate PNG image.");
    }

    return blob;
  }));
}

export async function dataUrlToBlob(dataUrl: string): Promise<Blob> {
  const response = await fetch(dataUrl);
  return response.blob();
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

  exportNode.classList.remove("post-card-preview__card");
  exportNode.style.width = "100%";
  exportNode.style.maxWidth = "none";

  host.style.position = "fixed";
  host.style.left = "-10000px";
  host.style.top = "0";
  host.style.width = `${getExportWidth(node)}px`;
  host.style.pointerEvents = "none";
  host.append(exportNode);
  document.body.append(host);

  try {
    return await callback(exportNode);
  } finally {
    host.remove();
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

  return 720;
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
      const source = image.currentSrc || image.src;

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
