export type SocialPlatform = "x" | "threads" | "bluesky" | "linkedin";

export type CardTheme = "light" | "dark";

export type ExtractedPost = {
  id: string;
  platform: SocialPlatform;
  authorName: string;
  authorHandle: string;
  content: string;
  publishedAt?: string;
  sourceUrl?: string;
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
