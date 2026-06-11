import type { ExtractedPost, SocialPlatform } from "../shared/types/post.types";

export function formatPublishedDate(value: string | undefined): string {
  if (!value) {
    return "Captured just now";
  }

  return new Intl.DateTimeFormat("en", {
    month: "short",
    day: "numeric",
    year: "numeric"
  }).format(new Date(value));
}

export function getPlatformLabel(post: ExtractedPost): string {
  return platformLabels[post.platform];
}

const platformLabels = {
  x: "X",
  threads: "Threads",
  bluesky: "Bluesky",
  linkedin: "LinkedIn"
} satisfies Record<SocialPlatform, string>;
