import type { ExtractedPost } from "../types/post.types";

export function formatPostAsText(post: ExtractedPost): string {
  const handle = post.authorHandle ? ` ${post.authorHandle}` : "";
  const source = post.sourceUrl ? `\n\n${post.sourceUrl}` : "";

  return `${post.authorName}${handle}\n\n${post.content}${source}`;
}

export function createPostFilename(post: ExtractedPost, extension = "png"): string {
  const author = sanitizeFilename(post.authorHandle || post.authorName || "post");
  const date = new Date().toISOString().slice(0, 10);

  return `quoti-${author}-${date}.${extension}`;
}

function sanitizeFilename(value: string): string {
  return value
    .replace(/^@/, "")
    .toLowerCase()
    .replace(/[^a-z0-9-]+/g, "-")
    .replace(/^-+|-+$/g, "")
    .slice(0, 40);
}
