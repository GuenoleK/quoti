import { useEffect, useMemo, useRef, useState } from "react";
import Hls from "hls.js";
import type { CardContentMode, CardTheme, ExtractedPost, PostMedia } from "../../../shared/types/post.types";
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

type CardLayout = "portrait" | "square" | "wide";

export function PostCardPreview({ post, contentMode, cardTheme, exportRef }: PostCardPreviewProps) {
  const isMountedRef = useRef(true);
  const [mediaSize, setMediaSize] = useState<MediaSize | null>(null);
  const media = contentMode === "with-media" ? getPrimaryMedia(post.media) : undefined;
  const mediaKey = getMediaKey(media);
  const cardLayout = useMemo(() => resolveCardLayout(post.content, mediaSize, Boolean(media)), [media, mediaSize, post.content]);

  useEffect(() => {
    return () => {
      isMountedRef.current = false;
    };
  }, []);

  useEffect(() => {
    isMountedRef.current = true;
    setMediaSize(null);
  }, [mediaKey]);

  const handleMediaSize = (size: MediaSize): void => {
    if (!isMountedRef.current) {
      return;
    }

    setMediaSize(size);
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
              <SocialPlatformIcon platform={post.platform} />
              <span className="context-card__date">{formatPublishedDate(post.publishedAt)}</span>
            </header>

            <p className="context-card__quote">{post.content}</p>

            {media ? (
              <figure className="context-card__media" data-media-type={media.type} style={getMediaStyle(media, mediaSize)}>
                <PostCardMedia media={media} onSize={handleMediaSize} />
              </figure>
            ) : null}

            <footer className="context-card__footer">
              <div className="context-card__author">
                <span className="context-card__author-name">{post.authorName}</span>
                {post.authorHandle ? <span className="context-card__author-handle">{post.authorHandle}</span> : null}
              </div>
              <span className="context-card__mark">Quoti</span>
            </footer>
          </div>
        </article>
      </div>
    </section>
  );
}

function PostCardMedia({ media, onSize }: { media: PostMedia; onSize: (size: MediaSize) => void }) {
  if (media.type === "image") {
    return <ImageMedia media={media} onSize={onSize} />;
  }

  return <VideoMedia media={media} onSize={onSize} />;
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

function VideoMedia({ media, onSize }: { media: Extract<PostMedia, { type: "video" }>; onSize: (size: MediaSize) => void }) {
  const videoRef = useRef<HTMLVideoElement>(null);
  const [sourceIndex, setSourceIndex] = useState(0);
  const [videoStatus, setVideoStatus] = useState<"idle" | "loading" | "ready" | "error">("idle");
  const [videoError, setVideoError] = useState<string>("");
  const playableVideoUrls = getPlayableVideoUrls(media);
  const playableVideoUrl = playableVideoUrls[sourceIndex];
  const posterUrl = getPreviewImageUrl(media.posterUrl);

  useEffect(() => {
    setSourceIndex(0);
    setVideoStatus("idle");
    setVideoError("");
  }, [media.url, media.posterUrl, media.variants]);

  useEffect(() => {
    const video = videoRef.current;

    if (!video) {
      return;
    }

    if (!playableVideoUrl) {
      setVideoStatus("error");
      setVideoError("Video URL missing");
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
    setVideoError(message);
  };

  return (
    <div className="context-card__video-frame">
      <video
        aria-label={media.alt ?? "Video"}
        className="context-card__video"
        controls
        playsInline
        preload="metadata"
        poster={posterUrl}
        ref={videoRef}
        onLoadedMetadata={(event) => {
          const video = event.currentTarget;
          setVideoStatus("ready");

          if (video.videoWidth && video.videoHeight) {
            onSize({
              height: video.videoHeight,
              width: video.videoWidth
            });
          }
        }}
      />
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
            onSize({
              height: event.currentTarget.naturalHeight,
              width: event.currentTarget.naturalWidth
            });
          }}
        />
      ) : null}
      {videoStatus === "error" || !playableVideoUrl ? (
        <span className="context-card__video-status">
          {playableVideoUrl ? videoError || "Video unavailable" : "Video URL missing"}
        </span>
      ) : null}
    </div>
  );
}

function resolveCardLayout(content: string, mediaSize: MediaSize | null, hasMedia: boolean): CardLayout {
  if (!hasMedia) {
    return "portrait";
  }

  const estimatedLineCount = estimateRenderedLineCount(content);
  const isLongText = estimatedLineCount > 3;

  if (!mediaSize) {
    return isLongText ? "square" : "portrait";
  }

  const mediaRatio = mediaSize.height / mediaSize.width;
  const isTallMedia = mediaRatio >= 1.18;
  const isVeryTallMedia = mediaRatio >= 1.45;

  if (isVeryTallMedia || (isTallMedia && isLongText)) {
    return "wide";
  }

  if (isTallMedia || isLongText) {
    return "square";
  }

  return "portrait";
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

function getMediaKey(media: PostMedia | undefined): string | undefined {
  if (!media) {
    return undefined;
  }

  return media.type === "image" ? media.url : media.url ?? media.posterUrl;
}

function getMediaStyle(media: PostMedia, mediaSize: MediaSize | null): React.CSSProperties | undefined {
  if (media.type !== "video" || !mediaSize) {
    return undefined;
  }

  return {
    aspectRatio: `${mediaSize.width} / ${mediaSize.height}`
  };
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

  return [...new Set(candidates.map(getPlayableVideoUrl).filter((url): url is string => Boolean(url)))].sort((a, b) => scorePlayableVideoUrl(b) - scorePlayableVideoUrl(a));
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
