import type { CardTheme, ExtractedPost } from "../../../shared/types/post.types";
import { formatPublishedDate } from "../../../card-generator/card-generator";
import { SocialPlatformIcon } from "../../../card-generator/SocialPlatformIcon";
import "../../../card-generator/card-template.css";
import "./PostCardPreview.css";

type PostCardPreviewProps = {
  post: ExtractedPost;
  cardTheme: CardTheme;
  exportRef?: React.Ref<HTMLDivElement>;
};

export function PostCardPreview({ post, cardTheme, exportRef }: PostCardPreviewProps) {
  return (
    <section className="post-card-preview" aria-label="Context card preview">
      <div className="post-card-preview__frame">
        <article className="context-card post-card-preview__card" data-card-theme={cardTheme} ref={exportRef}>
          <div className="context-card__inner">
            <header className="context-card__source">
              <SocialPlatformIcon platform={post.platform} />
              <span className="context-card__date">{formatPublishedDate(post.publishedAt)}</span>
            </header>

            <p className="context-card__quote">{post.content}</p>

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
