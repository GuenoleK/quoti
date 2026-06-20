import type { CardContentMode, CardTheme, ExtractedPost, PostMedia, SocialPlatform } from "../shared/types/post.types";

export type GalleryCard = {
  id: string;
  post: ExtractedPost;
  savedAt: string;
  updatedAt: string;
  cardTheme: CardTheme;
  contentMode: CardContentMode;
  searchText: string;
};

export type GalleryLayoutMode = "grid" | "list";

type GalleryCardOptions = {
  cardTheme: CardTheme;
  contentMode: CardContentMode;
};

const galleryStorageKey = "quoti-gallery-cards";
const galleryLayoutModeStorageKey = "quoti-gallery-layout-mode";
const galleryLocalStorageKey = "quoti:gallery-cards";
const galleryLayoutModeLocalStorageKey = "quoti:gallery-layout-mode";

export async function readGalleryLayoutMode(): Promise<GalleryLayoutMode> {
  const stored = await readStoredValue(galleryLayoutModeStorageKey, galleryLayoutModeLocalStorageKey, "grid");

  return isGalleryLayoutMode(stored) ? stored : "grid";
}

export async function writeGalleryLayoutMode(mode: GalleryLayoutMode): Promise<void> {
  await writeStoredValue(galleryLayoutModeStorageKey, galleryLayoutModeLocalStorageKey, mode);
}

export async function readGalleryCards(): Promise<GalleryCard[]> {
  const stored = await readStoredValue(galleryStorageKey, galleryLocalStorageKey, []);

  return normalizeGalleryCards(stored);
}

export async function upsertGalleryCard(post: ExtractedPost, options: GalleryCardOptions): Promise<GalleryCard> {
  const cards = await readGalleryCards();
  const id = getGalleryCardId(post);
  const existingCard = cards.find((card) => card.id === id);
  const now = new Date().toISOString();
  const nextCard: GalleryCard = {
    id,
    post,
    savedAt: existingCard?.savedAt ?? post.capturedAt ?? now,
    updatedAt: now,
    cardTheme: options.cardTheme,
    contentMode: options.contentMode,
    searchText: createGallerySearchText(post)
  };
  const nextCards = [nextCard, ...cards.filter((card) => card.id !== id)]
    .sort((left, right) => getTime(right.updatedAt) - getTime(left.updatedAt));

  await writeGalleryCards(nextCards);

  return nextCard;
}

export async function deleteGalleryCards(cardIds: string[]): Promise<GalleryCard[]> {
  const ids = new Set(cardIds);
  const nextCards = (await readGalleryCards()).filter((card) => !ids.has(card.id));

  await writeGalleryCards(nextCards);

  return nextCards;
}

export function mergeGalleryCard(cards: GalleryCard[], card: GalleryCard): GalleryCard[] {
  return [card, ...cards.filter((item) => item.id !== card.id)]
    .sort((left, right) => getTime(right.updatedAt) - getTime(left.updatedAt));
}

export function createGallerySearchText(post: ExtractedPost): string {
  return [
    post.authorName,
    post.authorHandle,
    post.content,
    post.sourceUrl,
    post.publishedAt,
    post.relatedPost?.authorName,
    post.relatedPost?.authorHandle,
    post.relatedPost?.content,
    post.relatedPost?.sourceUrl,
    ...getAllPostMedia(post)
      .map((media) => (media.type === "image" ? [media.url, media.alt] : [media.url, media.posterUrl, media.alt, ...(media.variants ?? [])]))
      .flat()
  ]
    .filter((value): value is string => typeof value === "string" && value.trim().length > 0)
    .join(" ")
    .toLocaleLowerCase();
}

function getGalleryCardId(post: ExtractedPost): string {
  const sourceUrl = normalizeSourceUrl(post.sourceUrl);

  return sourceUrl ? `source:${sourceUrl}` : `post:${post.platform}:${post.id}`;
}

function normalizeSourceUrl(sourceUrl: string | undefined): string | undefined {
  if (!sourceUrl) {
    return undefined;
  }

  try {
    const url = new URL(sourceUrl);
    url.hash = "";
    url.search = "";

    return url.toString();
  } catch {
    return sourceUrl.trim() || undefined;
  }
}

async function readStoredValue(storageKey: string, localStorageKey: string, fallback: unknown): Promise<unknown> {
  if (typeof chrome !== "undefined" && chrome.storage?.local) {
    const stored = await chrome.storage.local.get(storageKey);

    return stored[storageKey] ?? fallback;
  }

  if (typeof window === "undefined" || !window.localStorage) {
    return fallback;
  }

  try {
    const rawValue = window.localStorage.getItem(localStorageKey);

    return rawValue ? JSON.parse(rawValue) : fallback;
  } catch {
    return fallback;
  }
}

async function writeGalleryCards(cards: GalleryCard[]): Promise<void> {
  const normalizedCards = normalizeGalleryCards(cards);

  await writeStoredValue(galleryStorageKey, galleryLocalStorageKey, normalizedCards);
}

async function writeStoredValue(storageKey: string, localStorageKey: string, value: unknown): Promise<void> {
  if (typeof chrome !== "undefined" && chrome.storage?.local) {
    await chrome.storage.local.set({
      [storageKey]: value
    });
    return;
  }

  if (typeof window === "undefined" || !window.localStorage) {
    return;
  }

  window.localStorage.setItem(localStorageKey, JSON.stringify(value));
}

function normalizeGalleryCards(value: unknown): GalleryCard[] {
  if (!Array.isArray(value)) {
    return [];
  }

  const seenIds = new Set<string>();
  const normalizedCards = value
    .map(normalizeGalleryCard)
    .filter((card): card is GalleryCard => Boolean(card))
    .sort((left, right) => getTime(right.updatedAt) - getTime(left.updatedAt))
    .filter((card) => {
      if (seenIds.has(card.id)) {
        return false;
      }

      seenIds.add(card.id);
      return true;
    });

  return normalizedCards;
}

function normalizeGalleryCard(value: unknown): GalleryCard | null {
  if (!isRecord(value) || !isExtractedPost(value.post)) {
    return null;
  }

  const id = typeof value.id === "string" && value.id.trim() ? value.id : getGalleryCardId(value.post);
  const savedAt = readDateString(value.savedAt) ?? value.post.capturedAt ?? new Date(0).toISOString();
  const updatedAt = readDateString(value.updatedAt) ?? savedAt;
  const cardTheme = isCardTheme(value.cardTheme) ? value.cardTheme : "light";
  const contentMode = isCardContentMode(value.contentMode) ? value.contentMode : "with-media";

  return {
    id,
    post: value.post,
    savedAt,
    updatedAt,
    cardTheme,
    contentMode,
    searchText: createGallerySearchText(value.post)
  };
}

function readDateString(value: unknown): string | undefined {
  if (typeof value !== "string") {
    return undefined;
  }

  const time = Date.parse(value);

  return Number.isFinite(time) ? new Date(time).toISOString() : undefined;
}

function getTime(value: string): number {
  const time = Date.parse(value);

  return Number.isFinite(time) ? time : 0;
}

function isExtractedPost(value: unknown): value is ExtractedPost {
  return (
    isRecord(value) &&
    typeof value.id === "string" &&
    isSocialPlatform(value.platform) &&
    typeof value.authorName === "string" &&
    typeof value.authorHandle === "string" &&
    typeof value.content === "string" &&
    Array.isArray(value.media) &&
    typeof value.capturedAt === "string"
  );
}

function isSocialPlatform(value: unknown): value is SocialPlatform {
  return value === "x" || value === "threads" || value === "bluesky" || value === "linkedin";
}

function isCardTheme(value: unknown): value is CardTheme {
  return value === "light" || value === "dark";
}

function isCardContentMode(value: unknown): value is CardContentMode {
  return value === "text-only" || value === "with-media";
}

function isGalleryLayoutMode(value: unknown): value is GalleryLayoutMode {
  return value === "grid" || value === "list";
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null;
}

function getAllPostMedia(post: ExtractedPost): PostMedia[] {
  return [...post.media, ...(post.relatedPost?.media ?? [])];
}
