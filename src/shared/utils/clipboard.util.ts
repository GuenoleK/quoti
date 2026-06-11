export async function copyTextToClipboard(text: string): Promise<void> {
  await navigator.clipboard.writeText(text);
}

export async function copyBlobToClipboard(blob: Blob): Promise<void> {
  const clipboardItem = new ClipboardItem({
    [blob.type]: blob
  });

  await navigator.clipboard.write([clipboardItem]);
}
