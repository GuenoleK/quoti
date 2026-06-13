export type SocialPlatform = "x" | "threads" | "bluesky" | "linkedin";

export type CardTheme = "light" | "dark";
export type CardContentMode = "text-only" | "with-media";

export type ImagePostMedia = {
  type: "image";
  url: string;
  alt?: string;
};

export type VideoPostMedia = {
  type: "video";
  url?: string;
  posterUrl?: string;
  variants?: string[];
  alt?: string;
  duration?: number;
};

export type PostMedia = ImagePostMedia | VideoPostMedia;

export type ExtractedPost = {
  id: string;
  platform: SocialPlatform;
  authorName: string;
  authorHandle: string;
  content: string;
  publishedAt?: string;
  sourceUrl?: string;
  media: PostMedia[];
  capturedAt: string;
};

export type PostExtractionResult =
  | {
      status: "success";
      post: ExtractedPost;
    }
  | {
      status: "empty";
      reason: string;
    };
