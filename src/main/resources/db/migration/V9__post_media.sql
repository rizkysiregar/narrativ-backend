-- a post now has 0..n media (carousel, mixed images and videos) instead of exactly one
CREATE TABLE post_media
(
    id            UUID         PRIMARY KEY,
    post_id       UUID         NOT NULL REFERENCES posts (id),
    position      INTEGER      NOT NULL,
    media_key     VARCHAR(255) NOT NULL,
    media_type    VARCHAR(20)  NOT NULL,
    thumbnail_key VARCHAR(255),
    created_at    TIMESTAMP(6) NOT NULL,
    CONSTRAINT uq_post_media_position UNIQUE (post_id, position)
);

INSERT INTO post_media (id, post_id, position, media_key, media_type, thumbnail_key, created_at)
SELECT gen_random_uuid(), id, 0, media_url, media_type, thumbnail_url, created_at
FROM posts
WHERE media_url IS NOT NULL;

-- the media moved to post_media; a post without media (caption only) is valid from now on
ALTER TABLE posts
    DROP COLUMN media_url,
    DROP COLUMN media_type,
    DROP COLUMN thumbnail_url;
