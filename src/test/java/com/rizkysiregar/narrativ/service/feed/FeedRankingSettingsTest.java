package com.rizkysiregar.narrativ.service.feed;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FeedRankingSettingsTest {

    @Test
    void missingParametersUseTheDefaults() {
        assertEquals(FeedRankingSettings.DEFAULTS, FeedRankingSettings.from(Map.of()));
    }

    @Test
    void validParametersAreRead() {
        FeedRankingSettings settings = FeedRankingSettings.from(Map.of(
                "feed.ranking.window_days", "3",
                "feed.ranking.like_weight", "1.5",
                "feed.ranking.comment_weight", " 4 ",
                "feed.ranking.gravity", "1.8",
                "feed.ranking.report_penalty", "0",
                "feed.ranking.max_per_author", "3"));

        assertEquals(new FeedRankingSettings(3, 1.5, 4, 1.8, 0, 3), settings);
    }

    @Test
    void aTypoOrAValueOutOfRangeFallsBackToTheDefault() {
        FeedRankingSettings settings = FeedRankingSettings.from(Map.of(
                "feed.ranking.window_days", "2.5",
                "feed.ranking.like_weight", "abc",
                "feed.ranking.gravity", "0",
                "feed.ranking.max_per_author", "11"));

        assertEquals(FeedRankingSettings.DEFAULTS, settings);
    }
}
