-- the one comment the author of a post pinned on top of its comments; a deleted comment unpins itself
ALTER TABLE posts
    ADD COLUMN pinned_comment_id UUID REFERENCES comments (id) ON DELETE SET NULL;

-- the words of the caption a top-level comment answers, shown above the comment like a quote
ALTER TABLE comments
    ADD COLUMN quote_text VARCHAR(300);
