-- replies are one level deep: parent_id always points to the top-level comment,
-- reply_to_comment_id points to the comment that was actually answered (the tag shown in the app)
ALTER TABLE comments
    ADD COLUMN reply_to_comment_id UUID REFERENCES comments (id);

UPDATE comments SET reply_to_comment_id = parent_id WHERE parent_id IS NOT NULL;

-- flatten replies that were written under another reply before this rule existed
WITH RECURSIVE up AS (
    SELECT c.id AS cid, c.parent_id AS anc
    FROM comments c
    WHERE c.parent_id IS NOT NULL
    UNION ALL
    SELECT up.cid, p.parent_id
    FROM up
             JOIN comments p ON p.id = up.anc
    WHERE p.parent_id IS NOT NULL
)
UPDATE comments c
SET parent_id = r.anc
FROM (SELECT cid, anc
      FROM up
      WHERE NOT EXISTS (SELECT 1 FROM comments p WHERE p.id = up.anc AND p.parent_id IS NOT NULL)) r
WHERE c.id = r.cid
  AND c.parent_id <> r.anc;

CREATE INDEX idx_comments_parent_id ON comments (parent_id, created_at);
CREATE INDEX idx_comments_reply_to ON comments (reply_to_comment_id);
