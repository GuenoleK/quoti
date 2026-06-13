export async function copyTextToClipboard(text: string): Promise<void> {
  await navigator.clipboard.writeText(text);
}

export async function copyBlobToClipboard(blob: Blob): Promise<void> {
  if (!navigator.clipboard?.write || typeof ClipboardItem === "undefined") {
    throw new Error("Image clipboard is not available in this browser.");
  }

  const clipboardItem = new ClipboardItem({
    [blob.type]: blob
  });

  window.focus();
  await navigator.clipboard.write([clipboardItem]);
}

export function copyImageHtmlToClipboard(dataUrl: string, alt = "Quoti card"): boolean {
  const container = document.createElement("div");
  container.contentEditable = "true";
  container.style.position = "fixed";
  container.style.left = "-10000px";
  container.style.top = "0";
  container.style.opacity = "0";
  container.innerHTML = `<img src="${escapeHtml(dataUrl)}" alt="${escapeHtml(alt)}" />`;

  document.body.append(container);

  const range = document.createRange();
  range.selectNodeContents(container);

  const selection = window.getSelection();
  selection?.removeAllRanges();
  selection?.addRange(range);

  const copied = document.execCommand("copy");

  selection?.removeAllRanges();
  container.remove();

  return copied;
}

function escapeHtml(value: string): string {
  return value.replace(/[&<>"']/g, (character) => {
    const entities: Record<string, string> = {
      "&": "&amp;",
      "<": "&lt;",
      ">": "&gt;",
      "\"": "&quot;",
      "'": "&#039;"
    };

    return entities[character];
  });
}
