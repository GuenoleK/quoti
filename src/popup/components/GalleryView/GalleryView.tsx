import { useEffect, useMemo, useRef, useState } from "react";
import { Check, ExternalLink, Film, Image, Search, Trash2, X } from "lucide-react";
import type { ExtractedPost, PostMedia } from "../../../shared/types/post.types";
import { createGallerySearchText, type GalleryCard } from "../../gallery-storage";
import "./GalleryView.css";

type GalleryViewProps = {
  cards: GalleryCard[];
  isLoading: boolean;
  notice: string;
  query: string;
  selectedIds: Set<string>;
  onClearSelection: () => void;
  onDeleteSelected: () => void;
  onOpenCard: (card: GalleryCard) => void;
  onQueryChange: (query: string) => void;
  onToggleSelection: (cardId: string) => void;
};

const pageSize = 12;

export function GalleryView({
  cards,
  isLoading,
  notice,
  onClearSelection,
  onDeleteSelected,
  onOpenCard,
  onQueryChange,
  onToggleSelection,
  query,
  selectedIds
}: GalleryViewProps) {
  const loadMoreRef = useRef<HTMLDivElement>(null);
  const [visibleCount, setVisibleCount] = useState(pageSize);
  const filteredCards = useMemo(() => filterGalleryCards(cards, query), [cards, query]);
  const visibleCards = filteredCards.slice(0, visibleCount);
  const hasMore = visibleCount < filteredCards.length;

  useEffect(() => {
    setVisibleCount(pageSize);
  }, [cards, query]);

  useEffect(() => {
    const loadMoreNode = loadMoreRef.current;

    if (!loadMoreNode || !hasMore) {
      return;
    }

    const observer = new IntersectionObserver((entries) => {
      if (entries.some((entry) => entry.isIntersecting)) {
        setVisibleCount((count) => Math.min(count + pageSize, filteredCards.length));
      }
    });

    observer.observe(loadMoreNode);

    return () => observer.disconnect();
  }, [filteredCards.length, hasMore]);

  const selectedCount = selectedIds.size;

  return (
    <section className="gallery-view" aria-label="Saved Quoti cards">
      <div className="gallery-view__toolbar">
        <label className="gallery-view__search">
          <Search size={15} aria-hidden="true" />
          <input
            className="gallery-view__search-input"
            onChange={(event) => onQueryChange(event.target.value)}
            placeholder="Search text, author, or URL"
            type="search"
            value={query}
          />
        </label>
      </div>

      {selectedCount > 0 ? (
        <div className="gallery-view__selection-bar" aria-live="polite">
          <span className="gallery-view__selection-count">{selectedCount} selected</span>
          <button className="gallery-view__icon-button" onClick={onClearSelection} type="button" title="Clear selection" aria-label="Clear selection">
            <X size={16} aria-hidden="true" />
          </button>
          <button className="gallery-view__danger-button" onClick={onDeleteSelected} type="button">
            <Trash2 size={16} aria-hidden="true" />
            <span>Delete</span>
          </button>
        </div>
      ) : null}

      {notice ? <p className="gallery-view__notice">{notice}</p> : null}

      {isLoading ? (
        <GalleryEmptyState title="Loading gallery" message="Reading saved cards from this browser." />
      ) : visibleCards.length > 0 ? (
        <>
          <div className="gallery-view__grid">
            {visibleCards.map((card) => (
              <GalleryCardItem
                card={card}
                key={card.id}
                onOpen={() => onOpenCard(card)}
                onToggleSelection={() => onToggleSelection(card.id)}
                selected={selectedIds.has(card.id)}
              />
            ))}
          </div>
          <div className="gallery-view__sentinel" ref={loadMoreRef} aria-hidden="true" />
          {hasMore ? (
            <button className="gallery-view__load-more" onClick={() => setVisibleCount((count) => Math.min(count + pageSize, filteredCards.length))} type="button">
              Load more
            </button>
          ) : null}
        </>
      ) : (
        <GalleryEmptyState
          title={cards.length > 0 ? "No matching card" : "No saved cards yet"}
          message={cards.length > 0 ? "Search the post text, author, or source URL." : "Captured cards will appear here automatically."}
        />
      )}
    </section>
  );
}

function GalleryCardItem({
  card,
  onOpen,
  onToggleSelection,
  selected
}: {
  card: GalleryCard;
  onOpen: () => void;
  onToggleSelection: () => void;
  selected: boolean;
}) {
  const post = card.post;
  const media = getPrimaryMedia(post);
  const mediaPreviewUrl = getMediaPreviewUrl(media);
  const hasVideo = getAllPostMedia(post).some((item) => item.type === "video");
  const sourceHost = getSourceHost(post.sourceUrl);

  return (
    <article className={selected ? "gallery-card gallery-card--selected" : "gallery-card"}>
      <label className="gallery-card__selector">
        <input checked={selected} className="gallery-card__checkbox" onChange={onToggleSelection} type="checkbox" />
        <span className="gallery-card__check" aria-hidden="true">
          {selected ? <Check size={13} aria-hidden="true" /> : null}
        </span>
        <span className="gallery-card__selector-label">{selected ? "Deselect card" : "Select card"}</span>
      </label>

      <button className="gallery-card__open" onClick={onOpen} type="button">
        <span className="gallery-card__media" data-media-state={media ? media.type : "text"}>
          {mediaPreviewUrl ? (
            <img className="gallery-card__image" crossOrigin="anonymous" decoding="async" loading="lazy" referrerPolicy="no-referrer" src={mediaPreviewUrl} alt="" />
          ) : (
            <span className="gallery-card__text-preview">{getInitial(post)}</span>
          )}
          <span className="gallery-card__media-badge" aria-label={hasVideo ? "Video card" : media ? "Image card" : "Text card"}>
            {hasVideo ? <Film size={14} aria-hidden="true" /> : media ? <Image size={14} aria-hidden="true" /> : null}
          </span>
        </span>

        <span className="gallery-card__body">
          <span className="gallery-card__meta">
            <span className="gallery-card__author">{getAuthorLabel(post)}</span>
            <span className="gallery-card__date">{formatGalleryDate(card.updatedAt)}</span>
          </span>
          <span className="gallery-card__excerpt">{getPostExcerpt(post)}</span>
          <span className="gallery-card__source">
            {sourceHost ? <ExternalLink size={12} aria-hidden="true" /> : null}
            <span>{sourceHost ?? "No source URL"}</span>
          </span>
        </span>
      </button>
    </article>
  );
}

function GalleryEmptyState({ message, title }: { message: string; title: string }) {
  return (
    <div className="gallery-view__empty">
      <h2 className="gallery-view__empty-title">{title}</h2>
      <p className="gallery-view__empty-message">{message}</p>
    </div>
  );
}

function filterGalleryCards(cards: GalleryCard[], query: string): GalleryCard[] {
  const tokens = query
    .toLocaleLowerCase()
    .split(/\s+/)
    .map((token) => token.trim())
    .filter(Boolean);

  if (tokens.length === 0) {
    return cards;
  }

  return cards.filter((card) => {
    const searchText = card.searchText || createGallerySearchText(card.post);

    return tokens.every((token) => searchText.includes(token));
  });
}

function getAuthorLabel(post: ExtractedPost): string {
  return post.authorName.trim() || post.authorHandle.trim() || "Unknown author";
}

function getPostExcerpt(post: ExtractedPost): string {
  return post.content.trim() || post.relatedPost?.content.trim() || "Post without text";
}

function getInitial(post: ExtractedPost): string {
  const label = getAuthorLabel(post).replace("@", "").trim();

  return label[0]?.toUpperCase() ?? "Q";
}

function getSourceHost(sourceUrl: string | undefined): string | undefined {
  if (!sourceUrl) {
    return undefined;
  }

  try {
    const url = new URL(sourceUrl);

    return url.hostname.replace(/^www\./, "");
  } catch {
    return sourceUrl;
  }
}

function getPrimaryMedia(post: ExtractedPost): PostMedia | undefined {
  const media = getAllPostMedia(post);

  return media.find((item) => item.type === "video") ?? media.find((item) => item.type === "image");
}

function getAllPostMedia(post: ExtractedPost): PostMedia[] {
  return [...post.media, ...(post.relatedPost?.media ?? [])];
}

function getMediaPreviewUrl(media: PostMedia | undefined): string | undefined {
  if (!media) {
    return undefined;
  }

  return media.type === "image" ? media.url : media.posterUrl;
}

function formatGalleryDate(value: string): string {
  const date = new Date(value);

  if (Number.isNaN(date.getTime())) {
    return "";
  }

  return new Intl.DateTimeFormat(undefined, {
    day: "numeric",
    hour: "2-digit",
    minute: "2-digit",
    month: "short"
  }).format(date);
}
