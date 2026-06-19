import { useEffect, useMemo, useRef, useState } from "react";
import Hls from "hls.js";
import type { CardContentMode, CardTheme, ExtractedPost, PostMedia, RelatedPost } from "../../../shared/types/post.types";
import { formatPublishedDate } from "../../../card-generator/card-generator";
import { SocialPlatformIcon } from "../../../card-generator/SocialPlatformIcon";
import "../../../card-generator/card-template.css";
import "./PostCardPreview.css";

type PostCardPreviewProps = {
  post: ExtractedPost;
  contentMode: CardContentMode;
  cardTheme: CardTheme;
  exportRef?: React.Ref<HTMLDivElement>;
};

type MediaSize = {
  height: number;
  width: number;
};

type CardLayout = "compact" | "portrait" | "square" | "wide";

export function PostCardPreview({ post, contentMode, cardTheme, exportRef }: PostCardPreviewProps) {
  const isMountedRef = useRef(true);
  const [primaryMediaSize, setPrimaryMediaSize] = useState<MediaSize | null>(null);
  const [relatedMediaSize, setRelatedMediaSize] = useState<MediaSize | null>(null);
  const primaryMediaItems = getRenderableMedia(post.media);
  const relatedMediaItems = getRenderableMedia(post.relatedPost?.media ?? []);
  const primaryMedia = getPrimaryMedia(primaryMediaItems);
  const relatedMedia = getPrimaryMedia(relatedMediaItems);
  const layoutMedia = contentMode === "with-media" ? primaryMedia ?? relatedMedia : undefined;
  const layoutMediaSize = primaryMedia ? primaryMediaSize : relatedMediaSize;
  const primaryMediaKey = getMediaCollectionKey(primaryMediaItems);
  const relatedMediaKey = getMediaCollectionKey(relatedMediaItems);
  const hasBodyContent = Boolean(post.content.trim() || post.relatedPost);
  const cardLayout = useMemo(() => resolveCardLayout(getLayoutContent(post), layoutMedia, layoutMediaSize), [layoutMedia, layoutMediaSize, post]);

  useEffect(() => {
    return () => {
      isMountedRef.current = false;
    };
  }, []);

  useEffect(() => {
    isMountedRef.current = true;
    setPrimaryMediaSize(null);
  }, [primaryMediaKey]);

  useEffect(() => {
    isMountedRef.current = true;
    setRelatedMediaSize(null);
  }, [relatedMediaKey]);

  const handlePrimaryMediaSize = (size: MediaSize): void => {
    if (!isMountedRef.current) {
      return;
    }

    setPrimaryMediaSize(size);
  };

  const handleRelatedMediaSize = (size: MediaSize): void => {
    if (!isMountedRef.current) {
      return;
    }

    setRelatedMediaSize(size);
  };

  return (
    <section className="post-card-preview" aria-label="Context card preview">
      <div className="post-card-preview__frame">
        <article
          className="context-card post-card-preview__card"
          data-card-content-mode={contentMode}
          data-card-layout={cardLayout}
          data-card-theme={cardTheme}
          ref={exportRef}
        >
          <div className="context-card__inner">
            <header className="context-card__source">
              <div className="context-card__author-group context-card__author-group--primary">
                <PostAvatar avatarUrl={post.authorAvatarUrl} label={post.authorName} variant="author" />
                <div className="context-card__author">
                  <span className="context-card__author-name">{post.authorName}</span>
                  {post.authorHandle ? <span className="context-card__author-handle">{post.authorHandle}</span> : null}
                </div>
              </div>
              <span className="context-card__date">{formatPublishedDate(post.publishedAt)}</span>
            </header>

            {hasBodyContent ? (
              <div className="context-card__body">
                {post.content.trim() ? (
                  <p className="context-card__quote">
                    <EmojiText text={post.content} />
                  </p>
                ) : null}
                {post.relatedPost && primaryMedia && primaryMediaItems.length > 0 ? (
                  <PostCardMediaFigure
                    active={contentMode === "with-media"}
                    layoutMedia={primaryMedia}
                    media={primaryMediaItems}
                    mediaSize={primaryMediaSize}
                    onMediaSize={handlePrimaryMediaSize}
                    showMedia={contentMode === "with-media"}
                  />
                ) : null}
                {post.relatedPost ? (
                  <RelatedPostCard
                    active={contentMode === "with-media"}
                    mediaSize={relatedMediaSize}
                    onMediaSize={handleRelatedMediaSize}
                    relatedPost={post.relatedPost}
                    showMedia={contentMode === "with-media"}
                  />
                ) : null}
              </div>
            ) : null}

            {primaryMedia && primaryMediaItems.length > 0 && !post.relatedPost ? (
              <PostCardMediaFigure
                active={contentMode === "with-media"}
                layoutMedia={primaryMedia}
                media={primaryMediaItems}
                mediaSize={primaryMediaSize}
                onMediaSize={handlePrimaryMediaSize}
                showMedia={contentMode === "with-media"}
              />
            ) : null}

            <footer className="context-card__footer">
              <SocialPlatformIcon platform={post.platform} />
              <span className="context-card__mark">Quoti</span>
            </footer>
          </div>
        </article>
      </div>
    </section>
  );
}

function PostCardMediaFigure({
  active,
  layoutMedia,
  media,
  mediaSize,
  onMediaSize,
  related = false,
  showMedia
}: {
  active: boolean;
  layoutMedia: PostMedia;
  media: PostMedia[];
  mediaSize: MediaSize | null;
  onMediaSize: (size: MediaSize) => void;
  related?: boolean;
  showMedia: boolean;
}) {
  const visibleMedia = getRenderableMedia(media);
  const mediaType = visibleMedia.some((item) => item.type === "video") ? "video" : "image";

  if (visibleMedia.length === 0) {
    return null;
  }

  return (
    <figure
      className={`context-card__media${related ? " context-card__related-media" : ""}`}
      data-media-count={visibleMedia.length}
      data-media-layout={visibleMedia.length > 1 ? "grid" : "single"}
      data-media-type={mediaType}
      hidden={!showMedia}
      style={getMediaStyle(layoutMedia, mediaSize, visibleMedia.length)}
    >
      <PostCardMediaCollection active={active} layoutMedia={layoutMedia} media={visibleMedia} onSize={onMediaSize} />
    </figure>
  );
}

function RelatedPostCard({
  active,
  mediaSize,
  onMediaSize,
  relatedPost,
  showMedia
}: {
  active: boolean;
  mediaSize: MediaSize | null;
  onMediaSize: (size: MediaSize) => void;
  relatedPost: RelatedPost;
  showMedia: boolean;
}) {
  const media = getRenderableMedia(relatedPost.media ?? []);
  const layoutMedia = getPrimaryMedia(media);
  return (
    <aside className="context-card__related-post" aria-label="Post auquel l'auteur répond">
      <div className="context-card__related-meta">
        <PostAvatar avatarUrl={relatedPost.authorAvatarUrl} label={relatedPost.authorName ?? relatedPost.authorHandle ?? ""} variant="related" />
        <div className="context-card__related-copy">
          <span className="context-card__related-label">Répond à</span>
          {relatedPost.authorName ? <span className="context-card__related-author">{relatedPost.authorName}</span> : null}
          {relatedPost.authorHandle ? <span className="context-card__related-handle">{relatedPost.authorHandle}</span> : null}
        </div>
      </div>
      <p className="context-card__related-content">
        <EmojiText text={relatedPost.content} />
      </p>
      {layoutMedia && media.length > 0 ? (
        <PostCardMediaFigure
          active={active}
          layoutMedia={layoutMedia}
          media={media}
          mediaSize={mediaSize}
          onMediaSize={onMediaSize}
          related
          showMedia={showMedia}
        />
      ) : null}
    </aside>
  );
}

function PostAvatar({ avatarUrl, label, variant }: { avatarUrl?: string; label: string; variant: "author" | "related" }) {
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    setFailed(false);
  }, [avatarUrl]);

  if (!avatarUrl || failed) {
    if (!avatarUrl) {
      return null;
    }

    return (
      <span className={`context-card__avatar context-card__avatar--${variant}`} aria-label={label ? `Photo de profil de ${label}` : undefined}>
        <span className="context-card__avatar-fallback" aria-hidden="true">
          {getAvatarInitials(label)}
        </span>
      </span>
    );
  }

  return (
    <span className={`context-card__avatar context-card__avatar--${variant}`}>
      <img
        className="context-card__avatar-image"
        data-export-src={avatarUrl}
        decoding="async"
        loading="eager"
        referrerPolicy="no-referrer"
        src={avatarUrl}
        alt={label ? `Photo de profil de ${label}` : ""}
        onError={() => setFailed(true)}
      />
    </span>
  );
}

function getAvatarInitials(label: string): string {
  return (
    label
      .replace("@", "")
      .split(/[\s_.-]+/)
      .filter(Boolean)
      .slice(0, 2)
      .map((part) => part[0]?.toUpperCase())
      .join("") || "?"
  );
}

function PostCardMedia({ active, media, onSize }: { active: boolean; media: PostMedia; onSize: (size: MediaSize) => void }) {
  if (media.type === "image") {
    return <ImageMedia media={media} onSize={onSize} />;
  }

  return <VideoMedia active={active} media={media} onSize={onSize} />;
}

function PostCardMediaCollection({
  active,
  layoutMedia,
  media,
  onSize
}: {
  active: boolean;
  layoutMedia: PostMedia;
  media: PostMedia[];
  onSize: (size: MediaSize) => void;
}) {
  if (media.length === 1) {
    return <PostCardMedia active={active} media={media[0]} onSize={onSize} />;
  }

  return (
    <div className="context-card__media-grid" data-media-count={media.length}>
      {media.map((item, index) => (
        <div className="context-card__media-item" data-media-type={item.type} key={`${getMediaKey(item) ?? item.type}-${index}`}>
          <PostCardMedia active={active} media={item} onSize={item === layoutMedia ? onSize : ignoreMediaSize} />
        </div>
      ))}
    </div>
  );
}

function ImageMedia({ media, onSize }: { media: Extract<PostMedia, { type: "image" }>; onSize: (size: MediaSize) => void }) {
  const previewImageUrl = getPreviewImageUrl(media.url);

  return (
    <img
      className="context-card__image"
      crossOrigin="anonymous"
      data-export-src={media.url}
      decoding="async"
      loading="eager"
      referrerPolicy="no-referrer"
      src={previewImageUrl}
      alt={media.alt ?? ""}
      onLoad={(event) => {
        onSize({
          height: event.currentTarget.naturalHeight,
          width: event.currentTarget.naturalWidth
        });
      }}
    />
  );
}

function VideoMedia({ active, media, onSize }: { active: boolean; media: Extract<PostMedia, { type: "video" }>; onSize: (size: MediaSize) => void }) {
  const posterSizeRef = useRef<MediaSize | null>(null);
  const videoRef = useRef<HTMLVideoElement>(null);
  const [sourceIndex, setSourceIndex] = useState(0);
  const [isPlaying, setIsPlaying] = useState(false);
  const [videoStatus, setVideoStatus] = useState<"idle" | "loading" | "ready" | "error">("idle");
  const [videoError, setVideoError] = useState<string>("");
  const [playError, setPlayError] = useState<string>("");
  const playableVideoUrls = getPlayableVideoUrls(media);
  const playableVideoUrl = playableVideoUrls[sourceIndex];
  const posterUrl = getPreviewImageUrl(media.posterUrl);

  useEffect(() => {
    posterSizeRef.current = null;
    setSourceIndex(0);
    setIsPlaying(false);
    setVideoStatus("idle");
    setVideoError("");
    setPlayError("");
  }, [media.url, media.posterUrl, media.variants]);

  useEffect(() => {
    if (!active) {
      videoRef.current?.pause();
      setIsPlaying(false);
    }
  }, [active]);

  useEffect(() => {
    const video = videoRef.current;

    if (!video) {
      return;
    }

    if (!playableVideoUrl) {
      setVideoStatus("error");
      setVideoError("Video unavailable");
      return;
    }

    const controller = new AbortController();
    const handleError = () => {
      if (!controller.signal.aborted) {
        handleVideoLoadError(`media:${video.error?.code ?? "unknown"}`);
      }
    };

    setVideoStatus("loading");
    setVideoError("");
    setPlayError("");
    video.addEventListener("error", handleError);

    if (isHlsVideoUrl(playableVideoUrl) && Hls.isSupported()) {
      const hls = new Hls({
        backBufferLength: 0,
        enableWorker: false
      });

      resetVideoElement(video);
      hls.attachMedia(video);
      hls.on(Hls.Events.MEDIA_ATTACHED, () => hls.loadSource(playableVideoUrl));
      hls.on(Hls.Events.MANIFEST_PARSED, () => {
        const hasAudio = hls.audioTracks.length > 0 || hls.levels.some((level) => Boolean(level.audioCodec));

        if (!hasAudio && sourceIndex < playableVideoUrls.length - 1) {
          hls.destroy();
          handleVideoLoadError("HLS audio missing");
          return;
        }

        setVideoStatus("ready");
      });
      hls.on(Hls.Events.ERROR, (_event, data) => {
        if (data.fatal) {
          hls.destroy();
          if (!controller.signal.aborted) {
            handleVideoLoadError(data.details || data.type || "HLS load failed");
          }
        }
      });

      return () => {
        controller.abort();
        hls.destroy();
        video.removeEventListener("error", handleError);
        resetVideoElement(video);
      };
    }

    if (isHlsVideoUrl(playableVideoUrl) && video.canPlayType("application/vnd.apple.mpegurl")) {
      resetVideoElement(video);
      video.src = playableVideoUrl;
      video.load();

      return () => {
        controller.abort();
        video.removeEventListener("error", handleError);
        resetVideoElement(video);
      };
    }

    if (isHlsVideoUrl(playableVideoUrl)) {
      handleVideoLoadError("HLS unsupported");
      return () => {
        controller.abort();
        video.removeEventListener("error", handleError);
        resetVideoElement(video);
      };
    }

    resetVideoElement(video);
    video.src = playableVideoUrl;
    video.load();

    return () => {
      controller.abort();
      video.removeEventListener("error", handleError);
      resetVideoElement(video);
    };
  }, [playableVideoUrl, sourceIndex]);

  const handleVideoLoadError = (message: string): void => {
    if (sourceIndex < playableVideoUrls.length - 1) {
      setSourceIndex((index) => index + 1);
      return;
    }

    setVideoStatus("error");
    setVideoError(message ? "Video preview unavailable" : "Video unavailable");
  };

  const handleManualPlay = (): void => {
    const video = videoRef.current;

    if (!video || !playableVideoUrl || videoStatus !== "ready") {
      return;
    }

    video.defaultMuted = false;
    video.muted = false;

    if (video.volume === 0) {
      video.volume = 1;
    }

    void video.play().catch(() => {
      setPlayError("Use video controls to play");
    });
  };

  const canPlay = videoStatus === "ready" && Boolean(playableVideoUrl);

  return (
    <div className="context-card__video-frame" data-video-state={isPlaying ? "playing" : videoStatus}>
      <video
        aria-label={media.alt ?? "Video"}
        className="context-card__video"
        controls
        playsInline
        preload="metadata"
        poster={posterUrl}
        ref={videoRef}
        onEnded={() => setIsPlaying(false)}
        onPause={() => setIsPlaying(false)}
        onPlay={() => {
          setIsPlaying(true);
          setPlayError("");
        }}
        onLoadedMetadata={(event) => {
          const video = event.currentTarget;
          setVideoStatus("ready");

          if (!posterSizeRef.current && video.videoWidth && video.videoHeight) {
            onSize({
              height: video.videoHeight,
              width: video.videoWidth
            });
          }
        }}
      />
      {!isPlaying && playableVideoUrl && videoStatus !== "error" ? (
        <button
          className="context-card__video-play-button"
          disabled={!canPlay}
          type="button"
          title={canPlay ? "Play video with sound" : "Loading video"}
          aria-label={canPlay ? "Play video with sound" : "Loading video"}
          onClick={handleManualPlay}
        >
          <span className="context-card__video-play-icon" aria-hidden="true" />
        </button>
      ) : null}
      {posterUrl ? (
        <img
          className="context-card__video-probe"
          crossOrigin="anonymous"
          data-export-src={media.posterUrl}
          decoding="async"
          loading="eager"
          referrerPolicy="no-referrer"
          src={posterUrl}
          alt={media.alt ?? ""}
          onLoad={(event) => {
            const size = getVisibleImageSize(event.currentTarget);
            posterSizeRef.current = size;
            onSize(size);
          }}
        />
      ) : null}
      {videoStatus === "error" || !playableVideoUrl ? (
        <span className="context-card__video-status">
          {playableVideoUrl ? videoError || "Video unavailable" : "Video unavailable"}
        </span>
      ) : null}
      {playError ? <span className="context-card__video-status">{playError}</span> : null}
    </div>
  );
}

function getVisibleImageSize(image: HTMLImageElement): MediaSize {
  const fallbackSize = {
    height: image.naturalHeight,
    width: image.naturalWidth
  };

  if (!image.naturalWidth || !image.naturalHeight) {
    return fallbackSize;
  }

  try {
    const canvas = document.createElement("canvas");
    const context = canvas.getContext("2d", { willReadFrequently: true });

    if (!context) {
      return fallbackSize;
    }

    canvas.width = image.naturalWidth;
    canvas.height = image.naturalHeight;
    context.drawImage(image, 0, 0);

    const pixels = context.getImageData(0, 0, canvas.width, canvas.height).data;
    const top = findFirstNonLetterboxRow(pixels, canvas.width, canvas.height);
    const bottom = findFirstNonLetterboxRow(pixels, canvas.width, canvas.height, true);
    const visibleHeight = bottom >= top ? bottom - top + 1 : canvas.height;

    if (visibleHeight / canvas.height > 0.92 || visibleHeight < canvas.height * 0.35) {
      return fallbackSize;
    }

    return {
      height: visibleHeight,
      width: canvas.width
    };
  } catch {
    return fallbackSize;
  }
}

function findFirstNonLetterboxRow(pixels: Uint8ClampedArray, width: number, height: number, reverse = false): number {
  for (let step = 0; step < height; step += 1) {
    const row = reverse ? height - 1 - step : step;

    if (!isLetterboxRow(pixels, width, row)) {
      return row;
    }
  }

  return reverse ? height - 1 : 0;
}

function isLetterboxRow(pixels: Uint8ClampedArray, width: number, row: number): boolean {
  let darkPixels = 0;
  const offset = row * width * 4;

  for (let column = 0; column < width; column += 1) {
    const index = offset + column * 4;
    const alpha = pixels[index + 3];
    const red = pixels[index];
    const green = pixels[index + 1];
    const blue = pixels[index + 2];

    if (alpha < 8 || (red < 18 && green < 18 && blue < 18)) {
      darkPixels += 1;
    }
  }

  return darkPixels / width > 0.92;
}

function resolveCardLayout(content: string, media: PostMedia | undefined, mediaSize: MediaSize | null): CardLayout {
  const estimatedLineCount = estimateRenderedLineCount(content);

  if (!media) {
    return estimatedLineCount <= 3 ? "compact" : "portrait";
  }

  if (media.type === "image") {
    return "portrait";
  }

  const isLongText = estimatedLineCount > 3;

  if (!mediaSize) {
    return isLongText ? "square" : "portrait";
  }

  const mediaRatio = mediaSize.height / mediaSize.width;
  const isTallMedia = mediaRatio >= 1.18;
  const isVeryTallMedia = mediaRatio >= 1.45;

  if (isVeryTallMedia || (isTallMedia && isLongText)) {
    return "portrait";
  }

  if (isTallMedia || isLongText) {
    return "square";
  }

  return "portrait";
}

function getLayoutContent(post: ExtractedPost): string {
  return post.relatedPost ? `${post.content}\n${post.relatedPost.content}` : post.content;
}

function estimateRenderedLineCount(content: string): number {
  return content
    .trim()
    .split("\n")
    .reduce((lineCount, line) => lineCount + Math.max(1, Math.ceil(line.trim().length / 34)), 0);
}

function getPrimaryMedia(media: PostMedia[]): PostMedia | undefined {
  return media.find((item) => item.type === "video") ?? media.find((item) => item.type === "image");
}

function getRenderableMedia(media: PostMedia[]): PostMedia[] {
  return media.slice(0, 4);
}

function getMediaCollectionKey(media: PostMedia[]): string {
  return getRenderableMedia(media)
    .map((item, index) => `${index}:${getMediaKey(item) ?? ""}`)
    .join("|");
}

function getMediaKey(media: PostMedia | undefined): string | undefined {
  if (!media) {
    return undefined;
  }

  return media.type === "image" ? media.url : media.url ?? media.posterUrl;
}

function ignoreMediaSize(): void {
  return undefined;
}

function getMediaStyle(_media: PostMedia, mediaSize: MediaSize | null, mediaCount = 1): React.CSSProperties | undefined {
  if (mediaCount > 1) {
    return {
      aspectRatio: `${getMediaGridAspectRatio(mediaCount)} / 1`
    };
  }

  if (!mediaSize) {
    return undefined;
  }

  const mediaRatio = mediaSize.height / mediaSize.width;
  const maxHeight = getVideoMediaMaxHeight(mediaRatio);

  if (maxHeight) {
    return {
      aspectRatio: `${mediaSize.width} / ${mediaSize.height}`,
      width: `min(100%, ${Math.round(maxHeight / mediaRatio)}px)`
    };
  }

  return {
    aspectRatio: `${mediaSize.width} / ${mediaSize.height}`
  };
}

function getMediaGridAspectRatio(mediaCount: number): number {
  return mediaCount === 2 ? 2 : 16 / 9;
}

function getVideoMediaMaxHeight(mediaRatio: number): number | undefined {
  if (mediaRatio >= 1.45) {
    return 560;
  }

  if (mediaRatio >= 1.18) {
    return 520;
  }

  return undefined;
}

function getPlayableVideoUrl(source: string | undefined): string | undefined {
  if (!source) {
    return undefined;
  }

  try {
    const url = new URL(source);

    return ["http:", "https:"].includes(url.protocol) ? source : undefined;
  } catch {
    return undefined;
  }
}

function resetVideoElement(video: HTMLVideoElement): void {
  video.pause();
  video.removeAttribute("src");
  video.load();
}

function getPlayableVideoUrls(media: Extract<PostMedia, { type: "video" }>): string[] {
  const candidates = [media.url, ...(media.variants ?? [])];

  return [
    ...new Set(
      candidates
        .map(getPlayableVideoUrl)
        .filter((url): url is string => Boolean(url))
        .filter((url) => isMatchingPosterMediaSource(media.posterUrl, url))
    )
  ].sort((a, b) => scorePlayableVideoUrl(b) - scorePlayableVideoUrl(a));
}

function EmojiText({ text }: { text: string }) {
  return (
    <>
      {splitFlagEmoji(text).map((part, index) =>
        part.type === "flag" ? (
          <img
            alt={part.value}
            className="context-card__emoji"
            crossOrigin="anonymous"
            decoding="async"
            key={`${part.value}-${index}`}
            loading="eager"
            referrerPolicy="no-referrer"
            src={getTwitterEmojiSvgUrl(part.value)}
          />
        ) : (
          part.value
        )
      )}
    </>
  );
}

function splitFlagEmoji(text: string): Array<{ type: "flag" | "text"; value: string }> {
  const parts: Array<{ type: "flag" | "text"; value: string }> = [];
  const flagPattern = /([\u{1F1E6}-\u{1F1FF}]{2})/gu;
  let cursor = 0;
  let match: RegExpExecArray | null;

  while ((match = flagPattern.exec(text))) {
    if (match.index > cursor) {
      parts.push({
        type: "text",
        value: text.slice(cursor, match.index)
      });
    }

    parts.push({
      type: "flag",
      value: match[1]
    });
    cursor = match.index + match[1].length;
  }

  if (cursor < text.length) {
    parts.push({
      type: "text",
      value: text.slice(cursor)
    });
  }

  return parts.length > 0 ? parts : [{ type: "text", value: text }];
}

function getTwitterEmojiSvgUrl(emoji: string): string {
  const codepoints = Array.from(emoji)
    .map((character) => character.codePointAt(0)?.toString(16))
    .filter(Boolean)
    .join("-");

  return `https://abs.twimg.com/emoji/v2/svg/${codepoints}.svg`;
}

function isMatchingPosterMediaSource(posterUrl: string | undefined, sourceUrl: string): boolean {
  const mediaId = extractTwitterVideoMediaId(posterUrl);

  return !mediaId || extractTwitterVideoSourceId(sourceUrl) === mediaId;
}

function extractTwitterVideoMediaId(value: string | undefined): string | undefined {
  if (!value) {
    return undefined;
  }

  try {
    const pathname = new URL(value).pathname;

    return /\/(?:ext_tw_video_thumb|amplify_video_thumb|tweet_video_thumb)\/([^/]+)\//.exec(pathname)?.[1];
  } catch {
    return undefined;
  }
}

function extractTwitterVideoSourceId(value: string): string | undefined {
  try {
    const pathname = new URL(value).pathname;

    return /\/(?:ext_tw_video|amplify_video|tweet_video)\/([^/]+)\//.exec(pathname)?.[1];
  } catch {
    return undefined;
  }
}

function scorePlayableVideoUrl(value: string): number {
  try {
    const pathname = new URL(value).pathname.toLowerCase();
    const resolution = /\/(\d{2,5})x(\d{2,5})(?:\/|$)/.exec(pathname);
    const pixels = resolution ? Number(resolution[1]) * Number(resolution[2]) : 0;
    let score = pixels / 1000;

    if (pathname.endsWith(".m3u8")) {
      score += 160_000;
    }

    if (pathname.endsWith(".mp4") && !pathname.includes("/pl/")) {
      score += 120_000;
    }

    return score;
  } catch {
    return 0;
  }
}

async function loadTwitterHlsVideo(video: HTMLVideoElement, playlistUrl: string, signal: AbortSignal): Promise<void> {
  const playlist = await fetchText(playlistUrl, signal);
  const playlists = resolveTwitterMediaPlaylists(playlistUrl, playlist);
  const videoPlaylist = playlists.videoPlaylistUrl === playlistUrl ? playlist : await fetchText(playlists.videoPlaylistUrl, signal);
  const videoParts = parseTwitterMediaPlaylist(playlists.videoPlaylistUrl, videoPlaylist);
  const audioPlaylist =
    playlists.audioPlaylistUrl && playlists.audioPlaylistUrl !== playlistUrl ? await fetchText(playlists.audioPlaylistUrl, signal) : null;
  const audioParts = playlists.audioPlaylistUrl && audioPlaylist ? parseTwitterMediaPlaylist(playlists.audioPlaylistUrl, audioPlaylist) : null;

  if (!videoParts.initUrl || videoParts.segmentUrls.length === 0) {
    throw new Error("HLS playlist missing segments");
  }

  const videoCodec = extractVideoCodec(playlists.videoCodecs);
  const audioCodec = extractAudioCodec(playlists.videoCodecs);
  const videoMimeType = chooseVideoMediaSourceMimeType(audioParts ? videoCodec : playlists.videoCodecs ?? videoCodec);
  const audioMimeType = audioParts?.initUrl && audioParts.segmentUrls.length > 0 ? chooseAudioMediaSourceMimeType(audioCodec) : null;

  if (!videoMimeType) {
    throw new Error("MediaSource unsupported");
  }

  const mediaSource = new MediaSource();
  const objectUrl = URL.createObjectURL(mediaSource);
  video.src = objectUrl;

  try {
    await waitForMediaSourceOpen(mediaSource, signal);
    const sourceBuffers: SourceBuffer[] = [];
    const videoSourceBuffer = mediaSource.addSourceBuffer(videoMimeType);
    sourceBuffers.push(videoSourceBuffer);

    const initSegment = await fetchArrayBuffer(videoParts.initUrl, signal);

    await appendSourceBuffer(videoSourceBuffer, initSegment, signal);

    for (const segmentUrl of videoParts.segmentUrls) {
      const segment = await fetchArrayBuffer(segmentUrl, signal);
      await appendSourceBuffer(videoSourceBuffer, segment, signal);
    }

    if (audioParts?.initUrl && audioMimeType) {
      const audioSourceBuffer = mediaSource.addSourceBuffer(audioMimeType);
      sourceBuffers.push(audioSourceBuffer);
      const audioInitSegment = await fetchArrayBuffer(audioParts.initUrl, signal);

      await appendSourceBuffer(audioSourceBuffer, audioInitSegment, signal);

      for (const segmentUrl of audioParts.segmentUrls) {
        const segment = await fetchArrayBuffer(segmentUrl, signal);
        await appendSourceBuffer(audioSourceBuffer, segment, signal);
      }
    }

    await endMediaStream(mediaSource, sourceBuffers);
    await waitForVideoMetadata(video, signal);
  } finally {
    URL.revokeObjectURL(objectUrl);
  }
}

async function fetchText(url: string, signal: AbortSignal): Promise<string> {
  const response = await fetch(url, { credentials: "omit", referrerPolicy: "no-referrer", signal });

  if (!response.ok) {
    throw new Error(`HLS request failed ${response.status}`);
  }

  return response.text();
}

async function fetchArrayBuffer(url: string, signal: AbortSignal): Promise<ArrayBuffer> {
  const response = await fetch(url, { credentials: "omit", referrerPolicy: "no-referrer", signal });

  if (!response.ok) {
    throw new Error(`Segment request failed ${response.status}`);
  }

  return response.arrayBuffer();
}

function resolveTwitterMediaPlaylists(
  playlistUrl: string,
  playlist: string
): { audioPlaylistUrl?: string; videoCodecs?: string; videoPlaylistUrl: string } {
  const lines = playlist.split("\n").map((line) => line.trim());
  const audioMedia = lines.find((line) => line.startsWith("#EXT-X-MEDIA:") && /TYPE=AUDIO/i.test(line));
  const audioMatch = audioMedia ? /URI="([^"]+)"/.exec(audioMedia) : null;
  const variant = lines
    .map((line, index) => ({
      line,
      streamInfo: lines[index - 1] ?? "",
      score: scoreHlsVariant(lines[index - 1] ?? "")
    }))
    .filter(({ line }) => line && !line.startsWith("#") && line.endsWith(".m3u8"))
    .sort((a, b) => b.score - a.score)[0];

  return {
    audioPlaylistUrl: audioMatch ? new URL(audioMatch[1], playlistUrl).toString() : undefined,
    videoCodecs: variant ? extractCodecs(variant.streamInfo) : undefined,
    videoPlaylistUrl: variant ? new URL(variant.line, playlistUrl).toString() : playlistUrl
  };
}

function scoreHlsVariant(streamInfo: string): number {
  const resolution = /RESOLUTION=(\d+)x(\d+)/i.exec(streamInfo);
  const bandwidth = /(?:AVERAGE-)?BANDWIDTH=(\d+)/i.exec(streamInfo);

  return (resolution ? Number(resolution[1]) * Number(resolution[2]) : 0) + (bandwidth ? Number(bandwidth[1]) / 100 : 0);
}

function parseTwitterMediaPlaylist(playlistUrl: string, playlist: string): { initUrl: string | null; segmentUrls: string[] } {
  const initMatch = /#EXT-X-MAP:URI="([^"]+)"/.exec(playlist);
  const segmentUrls = playlist
    .split("\n")
    .map((line) => line.trim())
    .filter((line) => line && !line.startsWith("#"))
    .map((line) => new URL(line, playlistUrl).toString());

  return {
    initUrl: initMatch ? new URL(initMatch[1], playlistUrl).toString() : null,
    segmentUrls
  };
}

function extractCodecs(streamInfo: string): string | undefined {
  return /CODECS="([^"]+)"/i.exec(streamInfo)?.[1];
}

function extractVideoCodec(codecs: string | undefined): string | undefined {
  return codecs
    ?.split(",")
    .map((codec) => codec.trim())
    .find((codec) => /^(avc1|hev1|hvc1|vp09|av01)\./i.test(codec));
}

function extractAudioCodec(codecs: string | undefined): string | undefined {
  return codecs
    ?.split(",")
    .map((codec) => codec.trim())
    .find((codec) => /^(mp4a|ac-3|ec-3)\./i.test(codec));
}

function chooseVideoMediaSourceMimeType(preferredCodecs?: string): string | null {
  const candidates = [
    preferredCodecs ? `video/mp4; codecs="${preferredCodecs}"` : null,
    'video/mp4; codecs="avc1.640020"',
    'video/mp4; codecs="avc1.64001f"',
    'video/mp4; codecs="avc1.4d401f"',
    'video/mp4; codecs="avc1.42e01e"',
    "video/mp4"
  ];

  return candidates.filter((candidate): candidate is string => Boolean(candidate)).find((candidate) => MediaSource.isTypeSupported(candidate)) ?? null;
}

function chooseAudioMediaSourceMimeType(preferredCodec?: string): string | null {
  const candidates = [preferredCodec ? `audio/mp4; codecs="${preferredCodec}"` : null, 'audio/mp4; codecs="mp4a.40.2"', 'audio/mp4; codecs="mp4a.40.5"', "audio/mp4"];

  return candidates.filter((candidate): candidate is string => Boolean(candidate)).find((candidate) => MediaSource.isTypeSupported(candidate)) ?? null;
}

function waitForMediaSourceOpen(mediaSource: MediaSource, signal: AbortSignal): Promise<void> {
  if (mediaSource.readyState === "open") {
    return Promise.resolve();
  }

  return new Promise((resolve, reject) => {
    const cleanup = () => {
      mediaSource.removeEventListener("sourceopen", handleOpen);
      signal.removeEventListener("abort", handleAbort);
    };
    const handleOpen = () => {
      cleanup();
      resolve();
    };
    const handleAbort = () => {
      cleanup();
      reject(new DOMException("Aborted", "AbortError"));
    };

    mediaSource.addEventListener("sourceopen", handleOpen, { once: true });
    signal.addEventListener("abort", handleAbort, { once: true });
  });
}

function appendSourceBuffer(sourceBuffer: SourceBuffer, data: ArrayBuffer, signal: AbortSignal): Promise<void> {
  return new Promise((resolve, reject) => {
    const cleanup = () => {
      sourceBuffer.removeEventListener("updateend", handleUpdateEnd);
      sourceBuffer.removeEventListener("error", handleError);
      signal.removeEventListener("abort", handleAbort);
    };
    const handleUpdateEnd = () => {
      cleanup();
      resolve();
    };
    const handleError = () => {
      cleanup();
      reject(new Error("Segment append failed"));
    };
    const handleAbort = () => {
      cleanup();
      reject(new DOMException("Aborted", "AbortError"));
    };

    sourceBuffer.addEventListener("updateend", handleUpdateEnd, { once: true });
    sourceBuffer.addEventListener("error", handleError, { once: true });
    signal.addEventListener("abort", handleAbort, { once: true });
    sourceBuffer.appendBuffer(data);
  });
}

function endMediaStream(mediaSource: MediaSource, sourceBuffers: SourceBuffer[]): Promise<void> {
  if (sourceBuffers.every((sourceBuffer) => !sourceBuffer.updating)) {
    mediaSource.endOfStream();
    return Promise.resolve();
  }

  return new Promise((resolve) => {
    const handleUpdateEnd = () => {
      if (sourceBuffers.some((sourceBuffer) => sourceBuffer.updating)) {
        return;
      }

      sourceBuffers.forEach((sourceBuffer) => sourceBuffer.removeEventListener("updateend", handleUpdateEnd));
      if (mediaSource.readyState === "open") {
        mediaSource.endOfStream();
      }
      resolve();
    };

    sourceBuffers.forEach((sourceBuffer) => sourceBuffer.addEventListener("updateend", handleUpdateEnd));
  });
}

function waitForVideoMetadata(video: HTMLVideoElement, signal: AbortSignal): Promise<void> {
  if (video.readyState >= HTMLMediaElement.HAVE_METADATA) {
    return Promise.resolve();
  }

  return new Promise((resolve, reject) => {
    const cleanup = () => {
      video.removeEventListener("loadedmetadata", handleLoadedMetadata);
      video.removeEventListener("error", handleError);
      signal.removeEventListener("abort", handleAbort);
    };
    const handleLoadedMetadata = () => {
      cleanup();
      resolve();
    };
    const handleError = () => {
      cleanup();
      reject(new Error(`Media error ${video.error?.code ?? "unknown"}`));
    };
    const handleAbort = () => {
      cleanup();
      reject(new DOMException("Aborted", "AbortError"));
    };

    video.addEventListener("loadedmetadata", handleLoadedMetadata, { once: true });
    video.addEventListener("error", handleError, { once: true });
    signal.addEventListener("abort", handleAbort, { once: true });
  });
}

function isHlsVideoUrl(source: string): boolean {
  try {
    return new URL(source).pathname.toLowerCase().endsWith(".m3u8");
  } catch {
    return false;
  }
}

function getPreviewImageUrl(source: string | undefined): string | undefined {
  if (!source) {
    return undefined;
  }

  try {
    const url = new URL(source);

    if (url.hostname.endsWith("twimg.com") && url.pathname.includes("/media/")) {
      url.searchParams.set("name", "small");
      return url.toString();
    }
  } catch {
    return source;
  }

  return source;
}
