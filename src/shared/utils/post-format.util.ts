import type { ExtractedPost } from "../types/post.types";

export function formatPostAsText(post: ExtractedPost): string {
  const handle = post.authorHandle ? ` ${post.authorHandle}` : "";
  const relatedPost = post.relatedPost ? `\n\nRépond à${formatRelatedPostAuthor(post.relatedPost)}\n${post.relatedPost.content}` : "";
  const source = post.sourceUrl ? `\n\n${post.sourceUrl}` : "";

  return `${post.authorName}${handle}\n\n${post.content}${relatedPost}${source}`;
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

function formatRelatedPostAuthor(post: ExtractedPost["relatedPost"]): string {
  if (!post) {
    return "";
  }

  const author = [post.authorName, post.authorHandle].filter(Boolean).join(" ");

  return author ? ` ${author}` : "";
}
