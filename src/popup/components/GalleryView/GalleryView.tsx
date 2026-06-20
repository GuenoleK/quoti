import { useEffect, useMemo, useRef, useState } from "react";
import { Check, ExternalLink, FileText, Film, Grid2X2, Image, List, Search, Trash2, X } from "lucide-react";
import type { ExtractedPost, PostMedia } from "../../../shared/types/post.types";
import {
  createGallerySearchText,
  readGalleryLayoutMode,
  writeGalleryLayoutMode,
  type GalleryCard,
  type GalleryLayoutMode
} from "../../gallery-storage";
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

type GalleryFilter = "all" | "images" | "videos" | "text";

const pageSize = 18;

const galleryFilters: Array<{ label: string; value: GalleryFilter }> = [
  { label: "Tous", value: "all" },
  { label: "Images", value: "images" },
  { label: "Videos", value: "videos" },
  { label: "Textes", value: "text" }
];

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
  const [activeFilter, setActiveFilter] = useState<GalleryFilter>("all");
  const [layoutMode, setLayoutMode] = useState<GalleryLayoutMode>("grid");
  const [visibleCount, setVisibleCount] = useState(pageSize);
  const filteredCards = useMemo(() => filterGalleryCards(cards, query, activeFilter), [activeFilter, cards, query]);
  const visibleCards = filteredCards.slice(0, visibleCount);
  const hasMore = visibleCount < filteredCards.length;
  const selectedCount = selectedIds.size;
  const isSelectionMode = selectedCount > 0;

  useEffect(() => {
    let cancelled = false;

    void readGalleryLayoutMode().then((mode) => {
      if (!cancelled) {
        setLayoutMode(mode);
      }
    });

    return () => {
      cancelled = true;
    };
  }, []);

  useEffect(() => {
    setVisibleCount(pageSize);
  }, [activeFilter, cards, layoutMode, query]);

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

  const updateLayoutMode = (mode: GalleryLayoutMode) => {
    setLayoutMode(mode);
    void writeGalleryLayoutMode(mode);
  };

  return (
    <section className="gallery-view" aria-label="Bibliotheque Quoti">
      <div className="gallery-view__controls">
        <label className="gallery-view__search">
          <Search size={17} aria-hidden="true" />
          <input
            className="gallery-view__search-input"
            onChange={(event) => onQueryChange(event.target.value)}
            placeholder="Rechercher texte ou URL"
            type="search"
            value={query}
          />
        </label>

        <div className="gallery-view__library-row">
          <div className="gallery-view__filters" role="tablist" aria-label="Filtrer la bibliotheque">
            {galleryFilters.map((filter) => (
              <button
                aria-selected={activeFilter === filter.value}
                className={activeFilter === filter.value ? "gallery-view__filter gallery-view__filter--active" : "gallery-view__filter"}
                key={filter.value}
                onClick={() => setActiveFilter(filter.value)}
                role="tab"
                type="button"
              >
                {filter.label}
              </button>
            ))}
          </div>
          <button
            className="gallery-view__layout-button"
            onClick={() => updateLayoutMode(layoutMode === "grid" ? "list" : "grid")}
            type="button"
            title={layoutMode === "grid" ? "Afficher en liste" : "Afficher en grille"}
            aria-label={layoutMode === "grid" ? "Afficher en liste" : "Afficher en grille"}
          >
            {layoutMode === "grid" ? <List size={17} aria-hidden="true" /> : <Grid2X2 size={17} aria-hidden="true" />}
          </button>
        </div>
      </div>

      {selectedCount > 0 ? (
        <div className="gallery-view__selection-bar" aria-live="polite">
          <span className="gallery-view__selection-count">{selectedCount} selectionnee{selectedCount > 1 ? "s" : ""}</span>
          <button className="gallery-view__icon-button" onClick={onClearSelection} type="button" title="Annuler la selection" aria-label="Annuler la selection">
            <X size={16} aria-hidden="true" />
          </button>
          <button className="gallery-view__danger-button" onClick={onDeleteSelected} type="button">
            <Trash2 size={16} aria-hidden="true" />
            <span>Supprimer</span>
          </button>
        </div>
      ) : null}

      {notice ? <p className="gallery-view__notice">{notice}</p> : null}

      {isLoading ? (
        <GalleryEmptyState title="Chargement" message="Lecture des cartes sauvegardees dans ce navigateur." />
      ) : visibleCards.length > 0 ? (
        <>
          <div className={`gallery-view__cards gallery-view__cards--${layoutMode}`}>
            {visibleCards.map((card) => (
              <GalleryCardItem
                card={card}
                key={card.id}
                layoutMode={layoutMode}
                onOpen={() => onOpenCard(card)}
                onToggleSelection={() => onToggleSelection(card.id)}
                selected={selectedIds.has(card.id)}
                selectionMode={isSelectionMode}
              />
            ))}
          </div>
          <div className="gallery-view__sentinel" ref={loadMoreRef} aria-hidden="true" />
          {hasMore ? (
            <button className="gallery-view__load-more" onClick={() => setVisibleCount((count) => Math.min(count + pageSize, filteredCards.length))} type="button">
              Charger plus
            </button>
          ) : null}
        </>
      ) : (
        <GalleryEmptyState
          title={cards.length > 0 ? "Aucun resultat" : "Aucune carte"}
          message={cards.length > 0 ? "Essaie un autre texte, auteur ou lien source." : "Les cartes capturees apparaitront ici automatiquement."}
        />
      )}
    </section>
  );
}

function GalleryCardItem({
  card,
  layoutMode,
  onOpen,
  onToggleSelection,
  selected,
  selectionMode
}: {
  card: GalleryCard;
  layoutMode: GalleryLayoutMode;
  onOpen: () => void;
  onToggleSelection: () => void;
  selected: boolean;
  selectionMode: boolean;
}) {
  const post = card.post;
  const media = getPrimaryMedia(post);
  const mediaPreviewUrl = getMediaPreviewUrl(media);
  const hasImage = hasImageMedia(post);
  const hasVideo = hasVideoMedia(post);
  const sourceHost = getSourceHost(post.sourceUrl);
  const cardClassName = [
    "gallery-card",
    `gallery-card--${layoutMode}`,
    selected ? "gallery-card--selected" : "",
    selectionMode ? "gallery-card--selection-mode" : ""
  ]
    .filter(Boolean)
    .join(" ");

  return (
    <article className={cardClassName}>
      <button
        className="gallery-card__selector"
        onClick={onToggleSelection}
        type="button"
        title={selected ? "Retirer de la selection" : "Selectionner la carte"}
        aria-label={selected ? "Retirer de la selection" : "Selectionner la carte"}
        aria-pressed={selected}
      >
        <span className="gallery-card__check" aria-hidden="true">{selected ? <Check size={14} aria-hidden="true" /> : null}</span>
      </button>

      <button className="gallery-card__open" onClick={selectionMode ? onToggleSelection : onOpen} type="button">
        <span className="gallery-card__media" data-media-state={hasVideo ? "video" : hasImage ? "image" : "text"}>
          {mediaPreviewUrl ? (
            <img className="gallery-card__image" crossOrigin="anonymous" decoding="async" loading="lazy" referrerPolicy="no-referrer" src={mediaPreviewUrl} alt="" />
          ) : (
            <span className="gallery-card__fallback">
              {hasVideo ? <Film size={26} aria-hidden="true" /> : hasImage ? <Image size={26} aria-hidden="true" /> : <FileText size={26} aria-hidden="true" />}
              <span className="gallery-card__fallback-author">{getAuthorLabel(post)}</span>
              <span className="gallery-card__fallback-text">{getPostExcerpt(post)}</span>
            </span>
          )}
          <span className="gallery-card__media-badge" aria-label={hasVideo ? "Carte video" : hasImage ? "Carte image" : "Carte texte"}>
            {hasVideo ? <Film size={14} aria-hidden="true" /> : hasImage ? <Image size={14} aria-hidden="true" /> : <FileText size={14} aria-hidden="true" />}
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
            <span>{sourceHost ?? "Aucune URL source"}</span>
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

function filterGalleryCards(cards: GalleryCard[], query: string, activeFilter: GalleryFilter): GalleryCard[] {
  const tokens = query
    .toLocaleLowerCase()
    .split(/\s+/)
    .map((token) => token.trim())
    .filter(Boolean);

  return cards.filter((card) => {
    if (!matchesGalleryFilter(card.post, activeFilter)) {
      return false;
    }

    if (tokens.length === 0) {
      return true;
    }

    const searchText = card.searchText || createGallerySearchText(card.post);

    return tokens.every((token) => searchText.includes(token));
  });
}

function matchesGalleryFilter(post: ExtractedPost, filter: GalleryFilter): boolean {
  if (filter === "images") {
    return hasImageMedia(post);
  }

  if (filter === "videos") {
    return hasVideoMedia(post);
  }

  if (filter === "text") {
    return !hasAnyMedia(post);
  }

  return true;
}

function getAuthorLabel(post: ExtractedPost): string {
  return post.authorName.trim() || post.authorHandle.trim() || "Auteur inconnu";
}

function getPostExcerpt(post: ExtractedPost): string {
  return post.content.trim() || post.relatedPost?.content.trim() || "Post sans texte";
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

function hasAnyMedia(post: ExtractedPost): boolean {
  return getAllPostMedia(post).length > 0;
}

function hasImageMedia(post: ExtractedPost): boolean {
  return getAllPostMedia(post).some((item) => item.type === "image");
}

function hasVideoMedia(post: ExtractedPost): boolean {
  return getAllPostMedia(post).some((item) => item.type === "video");
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
