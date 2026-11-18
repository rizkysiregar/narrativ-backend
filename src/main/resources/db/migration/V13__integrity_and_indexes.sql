-- 1. A like is one row per user and post. Without the constraint a double tap could insert two rows (the service
--    checks, then inserts) and every count was off by one. The oldest row of a pair is kept.
DELETE FROM likes l
USING likes newer
WHERE l.user_id = newer.user_id
  AND l.post_id = newer.post_id
  AND (l.created_at, l.id) > (newer.created_at, newer.id);

ALTER TABLE likes ADD CONSTRAINT uk_likes_user_post UNIQUE (user_id, post_id);

-- 2. Postgres does not index foreign keys by itself. These serve the feeds, the counts per post and the lookups per
--    user that run on almost every request. (likes by user is served by the unique constraint above.)
CREATE INDEX IF NOT EXISTS idx_posts_user_created    ON posts (user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_likes_post            ON likes (post_id);
CREATE INDEX IF NOT EXISTS idx_comments_post_created ON comments (post_id, created_at);
CREATE INDEX IF NOT EXISTS idx_user_device_user      ON user_device (user_id);
CREATE INDEX IF NOT EXISTS idx_user_notification_user ON user_notification (user_id, created_at DESC);

-- 3. Emails are stored in lower case from now on (EmailNormalizer). Existing rows are lowered unless that would
--    collide with another account, which needs a manual decision and is left as it is.
UPDATE users u
SET email = lower(u.email)
WHERE u.email <> lower(u.email)
  AND NOT EXISTS (SELECT 1 FROM users other WHERE other.id <> u.id AND lower(other.email) = lower(u.email));

-- 4. Usernames and emails are unique regardless of case, so "Alice" cannot sit next to "alice". The username keeps
--    the case its owner typed. A database that already holds such a pair keeps working: the index is then skipped
--    with a warning (the service still refuses new duplicates) and can be created once the pair is resolved.
DO $$
BEGIN
    IF EXISTS (SELECT lower(username) FROM users GROUP BY lower(username) HAVING COUNT(*) > 1) THEN
        RAISE WARNING 'ux_users_username_lower not created: usernames that differ only in case exist';
    ELSE
        CREATE UNIQUE INDEX ux_users_username_lower ON users (lower(username));
    END IF;

    IF EXISTS (SELECT lower(email) FROM users GROUP BY lower(email) HAVING COUNT(*) > 1) THEN
        RAISE WARNING 'ux_users_email_lower not created: emails that differ only in case exist';
    ELSE
        CREATE UNIQUE INDEX ux_users_email_lower ON users (lower(email));
    END IF;
END $$;
