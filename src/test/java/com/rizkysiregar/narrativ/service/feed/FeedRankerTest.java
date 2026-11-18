package com.rizkysiregar.narrativ.service.feed;

import com.rizkysiregar.narrativ.model.dto.RankingCandidate;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FeedRankerTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 18, 12, 0);
    private static final FeedRankingSettings SETTINGS = FeedRankingSettings.DEFAULTS;

    private static RankingCandidate candidate(UUID author, double hoursAgo, long likes, long comments, long reports) {
        return new RankingCandidate(UUID.randomUUID(), author, NOW.minusSeconds((long) (hoursAgo * 3600)),
                likes, comments, reports);
    }

    private static RankingCandidate candidate(double hoursAgo, long likes) {
        return candidate(UUID.randomUUID(), hoursAgo, likes, 0, 0);
    }

    @Test
    void scoreIsTheHotFormula() {
        // (1 x 3 likes + 2 x 1 comment + 1) / (2h + 2)^1.5 = 6 / 8
        assertEquals(0.75, FeedRanker.score(candidate(UUID.randomUUID(), 2, 3, 1, 0), SETTINGS, NOW), 1e-9);
    }

    @Test
    void engagementLiftsAPostAndAgeSinksIt() {
        RankingCandidate liked = candidate(5, 10);
        RankingCandidate quiet = candidate(5, 0);
        RankingCandidate stale = candidate(72, 10);

        assertEquals(List.of(liked.postId(), quiet.postId(), stale.postId()),
                FeedRanker.rank(List.of(stale, quiet, liked), SETTINGS, NOW, 10));
    }

    @Test
    void aBrandNewPostGetsItsChanceAboveAnOlderPopularOne() {
        RankingCandidate fresh = candidate(0.1, 0);
        RankingCandidate yesterday = candidate(24, 10);

        assertEquals(fresh.postId(), FeedRanker.rank(List.of(yesterday, fresh), SETTINGS, NOW, 10).getFirst());
    }

    @Test
    void openReportsPullAPostDown() {
        UUID author = UUID.randomUUID();
        RankingCandidate reported = candidate(author, 1, 5, 0, 3);
        RankingCandidate clean = candidate(author, 1, 5, 0, 0);

        assertEquals(FeedRanker.score(clean, SETTINGS, NOW) / 4, FeedRanker.score(reported, SETTINGS, NOW), 1e-9);
    }

    @Test
    void aPostDatedInTheFutureCountsAsNew() {
        RankingCandidate future = candidate(-1, 0);

        assertEquals(FeedRanker.score(candidate(0, 0), SETTINGS, NOW), FeedRanker.score(future, SETTINGS, NOW), 1e-9);
    }

    @Test
    void noAuthorHasMoreThanMaxPerAuthorPostsInAWindow() {
        UUID busy = UUID.randomUUID();
        List<RankingCandidate> candidates = new ArrayList<>();
        // the busy author has the 5 best posts, 5 others follow
        for (int i = 0; i < 5; i++) candidates.add(candidate(busy, 1, 100 - i, 0, 0));
        for (int i = 0; i < 5; i++) candidates.add(candidate(UUID.randomUUID(), 1, 50 - i, 0, 0));

        List<UUID> ranked = FeedRanker.rank(candidates, SETTINGS, NOW, 10);

        List<UUID> busyPosts = candidates.subList(0, 5).stream().map(RankingCandidate::postId).toList();
        // two of them open the feed, the others wait for the next window rather than sinking to the end
        assertEquals(busyPosts.subList(0, 2), ranked.subList(0, 2));
        assertEquals(2, ranked.subList(0, 7).stream().filter(busyPosts::contains).count());
        assertEquals(busyPosts.get(2), ranked.get(7));
        assertEquals(10, ranked.size());
    }

    @Test
    void diversifyKeepsSkippedPostsAtTheTopOfTheNextWindow() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        List<RankingCandidate> sorted = List.of(
                candidate(a, 1, 0, 0, 0), candidate(a, 1, 0, 0, 0), candidate(a, 1, 0, 0, 0),
                candidate(b, 1, 0, 0, 0), candidate(b, 1, 0, 0, 0));

        List<RankingCandidate> ordered = FeedRanker.diversify(sorted, 1, 2, 10);

        assertEquals(List.of(a, b, a, b, a), ordered.stream().map(RankingCandidate::authorId).toList());
    }

    @Test
    void rankStopsAtTheLimit() {
        List<RankingCandidate> candidates = new ArrayList<>();
        for (int i = 0; i < 30; i++) candidates.add(candidate(i, 0));

        List<UUID> ranked = FeedRanker.rank(candidates, SETTINGS, NOW, 12);

        assertEquals(12, ranked.size());
        assertTrue(ranked.contains(candidates.getFirst().postId()));
    }
}
