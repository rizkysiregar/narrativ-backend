package com.rizkysiregar.narrativ.service.feed;

import lombok.extern.slf4j.Slf4j;

import java.util.Map;

/**
 * The tunable part of the ranked feeds, kept as app parameters under {@code feed.ranking.*} (seeded by V15). A value
 * that is missing, not a number or out of its range falls back to the default, so a typo in App management cannot
 * break the feed.
 *
 * @param windowDays   only posts of the last N days are ranked; older ones follow by date
 * @param likeWeight   points per like
 * @param commentWeight points per comment
 * @param gravity      how fast a post sinks with age
 * @param reportPenalty the score is divided by (1 + reportPenalty x open reports)
 * @param maxPerAuthor at most this many posts of one author in every {@link #DIVERSITY_WINDOW}
 * @param followedFactor explore multiplies the score of a post of somebody the viewer follows by this: they see it
 *                       on home already, but it keeps explore from going empty
 * @param ownFactor    the same for the viewer's own posts
 */
@Slf4j
public record FeedRankingSettings(int windowDays, double likeWeight, double commentWeight, double gravity,
                                  double reportPenalty, int maxPerAuthor, double followedFactor, double ownFactor) {

    public static final String PREFIX = "feed.ranking.";
    // the default page size of the feeds, so a page rarely shows one author more than maxPerAuthor times
    public static final int DIVERSITY_WINDOW = 10;

    public static final FeedRankingSettings DEFAULTS = new FeedRankingSettings(7, 1, 2, 1.5, 1, 2, 0.5, 0.2);

    public static FeedRankingSettings from(Map<String, String> parameters) {
        return new FeedRankingSettings(
                (int) read(parameters, "window_days", DEFAULTS.windowDays, 1, 30, true),
                read(parameters, "like_weight", DEFAULTS.likeWeight, 0, 100, false),
                read(parameters, "comment_weight", DEFAULTS.commentWeight, 0, 100, false),
                read(parameters, "gravity", DEFAULTS.gravity, 0.5, 3, false),
                read(parameters, "report_penalty", DEFAULTS.reportPenalty, 0, 10, false),
                (int) read(parameters, "max_per_author", DEFAULTS.maxPerAuthor, 1, DIVERSITY_WINDOW, true),
                read(parameters, "followed_factor", DEFAULTS.followedFactor, 0, 1, false),
                read(parameters, "own_factor", DEFAULTS.ownFactor, 0, 1, false));
    }

    private static double read(Map<String, String> parameters, String name, double fallback,
                               double min, double max, boolean whole) {
        String raw = parameters.get(PREFIX + name);
        if (raw == null) {
            return fallback;
        }
        try {
            double value = Double.parseDouble(raw.trim());
            if (value >= min && value <= max && (!whole || value == Math.rint(value))) {
                return value;
            }
        } catch (NumberFormatException ignored) {
            // falls through to the default
        }
        log.warn("FEED.RANKING.PARAMETER.INVALID key={}{} value={} using={}", PREFIX, name, raw, fallback);
        return fallback;
    }
}
