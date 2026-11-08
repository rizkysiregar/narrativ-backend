-- the dashboard counts users and posts per day/week/month over a created_at range
CREATE INDEX idx_users_created_at ON users (created_at);
CREATE INDEX idx_posts_created_at ON posts (created_at);
