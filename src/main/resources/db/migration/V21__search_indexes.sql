-- Search (GET /posts/search, GET /users/search).

-- 1. Posts are found by the words of their caption, with Postgres full-text search. The 'simple' configuration only
--    lower cases and splits on everything that is not a letter or digit, without stemming: Postgres has no Indonesian
--    dictionary and captions mix languages. A hashtag is a word like any other (#coffee is indexed as coffee). The
--    expression must stay exactly the one PostPaginationRepository.searchPostIds matches on, or the index is not used.
CREATE INDEX IF NOT EXISTS idx_posts_caption_fts ON posts USING gin (to_tsvector('simple', coalesce(caption, '')));

-- 2. People are found by a part of their username or name (LIKE '%term%'), which a btree index cannot serve; a
--    trigram index can. pg_trgm needs the CREATE privilege on the database, which the app role may not have: then
--    the migration only warns and user search still works, by reading the users table. An admin can run
--    CREATE EXTENSION pg_trgm and the two CREATE INDEX statements below later.
DO $$
BEGIN
    BEGIN
        CREATE EXTENSION IF NOT EXISTS pg_trgm;
    EXCEPTION WHEN OTHERS THEN
        RAISE WARNING 'pg_trgm not available (%): user search runs without trigram indexes', SQLERRM;
        RETURN;
    END;
    CREATE INDEX IF NOT EXISTS idx_users_username_trgm ON users USING gin (lower(username) gin_trgm_ops);
    -- the same expression as UserRepository.searchActive
    CREATE INDEX IF NOT EXISTS idx_users_full_name_trgm
        ON users USING gin (lower(coalesce(first_name, '') || ' ' || coalesce(last_name, '')) gin_trgm_ops);
END $$;
