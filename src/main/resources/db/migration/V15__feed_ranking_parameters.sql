-- The weights of the ranked feeds (explore, and the suggestions under the home feed). They are read on every new
-- ranking, so an admin can tune them from App management without a deploy. A missing or invalid value falls back
-- to the default in FeedRankingSettings; a parameter an admin already created is left as it is.
INSERT INTO app_parameters (param_key, param_value, description, created_at, updated_at) VALUES
    ('feed.ranking.window_days',    '7',   'Ranked feeds: only posts of the last N days are ranked (1-30); older ones follow by date', now(), now()),
    ('feed.ranking.like_weight',    '1',   'Ranked feeds: points per like (0-100)', now(), now()),
    ('feed.ranking.comment_weight', '2',   'Ranked feeds: points per comment (0-100)', now(), now()),
    ('feed.ranking.gravity',        '1.5', 'Ranked feeds: how fast a post sinks with age, higher sinks faster (0.5-3)', now(), now()),
    ('feed.ranking.report_penalty', '1',   'Ranked feeds: score divided by (1 + penalty x open reports) (0-10)', now(), now()),
    ('feed.ranking.max_per_author', '2',   'Ranked feeds: at most N posts of one author in every 10 (1-10)', now(), now()),
    ('feed.ranking.followed_factor', '0.5', 'Explore: score multiplier for posts of people the viewer follows (0-1)', now(), now()),
    ('feed.ranking.own_factor',     '0.2', 'Explore: score multiplier for the viewer''s own posts (0-1)', now(), now())
ON CONFLICT (param_key) DO NOTHING;
