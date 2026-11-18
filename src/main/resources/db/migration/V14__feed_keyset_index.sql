-- the feeds page by (created_at, id) descending, also after a cursor; created_at alone (V6) cannot seek to a pair
CREATE INDEX IF NOT EXISTS idx_posts_created_id ON posts (created_at DESC, id DESC);
