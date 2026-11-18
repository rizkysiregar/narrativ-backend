-- Explore now also holds the posts the viewer already knows, scaled down so new faces come first (see V15 for the
-- other weights). A parameter an admin already created is left as it is.
INSERT INTO app_parameters (param_key, param_value, description, created_at, updated_at) VALUES
    ('feed.ranking.followed_factor', '0.5', 'Explore: score multiplier for posts of people the viewer follows (0-1)', now(), now()),
    ('feed.ranking.own_factor',      '0.2', 'Explore: score multiplier for the viewer''s own posts (0-1)', now(), now())
ON CONFLICT (param_key) DO NOTHING;
