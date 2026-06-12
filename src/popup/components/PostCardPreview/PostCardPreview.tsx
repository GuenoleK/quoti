import { useEffect, useMemo, useState } from "react";
import type { CardContentMode, CardTheme, ExtractedPost } from "../../../shared/types/post.types";
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

type ImageSize = {
  height: number;
  width: number;
};

type CardLayout = "portrait" | "square" | "wide";

export function PostCardPreview({ post, contentMode, cardTheme, exportRef }: PostCardPreviewProps) {
  const [imageSize, setImageSize] = useState<ImageSize | null>(null);
  const image = contentMode === "with-media" ? post.media.find((media) => media.type === "image") : undefined;
  const cardLayout = useMemo(() => resolveCardLayout(post.content, imageSize, Boolean(image)), [image, imageSize, post.content]);

  useEffect(() => {
    setImageSize(null);
  }, [image?.url]);

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

            {image ? (
              <figure className="context-card__media">
                <img
                  className="context-card__image"
                  crossOrigin="anonymous"
                  referrerPolicy="no-referrer"
                  src={image.url}
                  alt={image.alt ?? ""}
                  onLoad={(event) => {
                    setImageSize({
                      height: event.currentTarget.naturalHeight,
                      width: event.currentTarget.naturalWidth
                    });
                  }}
                />
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

function resolveCardLayout(content: string, imageSize: ImageSize | null, hasImage: boolean): CardLayout {
  if (!hasImage) {
    return "portrait";
  }

  const estimatedLineCount = Math.ceil(content.trim().length / 34);
  const isLongText = estimatedLineCount > 3;

  if (!imageSize) {
    return isLongText ? "square" : "portrait";
  }

  const imageRatio = imageSize.height / imageSize.width;
  const isTallImage = imageRatio >= 1.18;
  const isVeryTallImage = imageRatio >= 1.45;

  if (isVeryTallImage || (isTallImage && isLongText)) {
    return "wide";
  }

  if (isTallImage || isLongText) {
    return "square";
  }

  return "portrait";
}
