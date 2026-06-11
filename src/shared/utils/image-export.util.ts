import { toBlob, toJpeg, toPng } from "html-to-image";

const pixelRatio = 2;

export async function exportNodeToPngDataUrl(node: HTMLElement): Promise<string> {
  return toPng(node, {
    cacheBust: true,
    pixelRatio
  });
}

export async function exportNodeToJpegDataUrl(node: HTMLElement): Promise<string> {
  return toJpeg(node, {
    cacheBust: true,
    pixelRatio,
    quality: 0.95
  });
}

export async function exportNodeToPngBlob(node: HTMLElement): Promise<Blob> {
  const blob = await toBlob(node, {
    cacheBust: true,
    pixelRatio
  });

  if (!blob) {
    throw new Error("Could not generate PNG image.");
  }

  return blob;
}

export function downloadDataUrl(dataUrl: string, filename: string): void {
  const link = document.createElement("a");
  link.href = dataUrl;
  link.download = filename;
  link.click();
}
