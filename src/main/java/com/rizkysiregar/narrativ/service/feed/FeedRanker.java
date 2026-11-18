package com.rizkysiregar.narrativ.service.feed;

import com.rizkysiregar.narrativ.model.dto.RankingCandidate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Orders the ranked feeds. The score is the "hot" formula of Hacker News and Reddit:
 * <pre>
 *   (likeWeight x likes + commentWeight x comments + 1) / (age in hours + 2) ^ gravity / (1 + reportPenalty x open reports)
 * </pre>
 * A post climbs with engagement and sinks with age, so a new post gets its chance on top (the + 1) and a popular old
 * one does not stay there for days. A post the viewer already knows (their own, or of somebody they follow) is
 * scaled down, so the new faces come first. The order is then spread so no author fills a page.
 */
public final class FeedRanker {

    private FeedRanker() {
    }

    public static double score(RankingCandidate candidate, FeedRankingSettings settings, LocalDateTime now) {
        double points = settings.likeWeight() * candidate.likeCount()
                + settings.commentWeight() * candidate.commentCount()
                + 1;
        // a post dated a little in the future (clock skew) counts as brand new
        double ageHours = Math.max(0, Duration.between(candidate.createdAt(), now).toSeconds() / 3600.0);
        return points / Math.pow(ageHours + 2, settings.gravity())
                / (1 + settings.reportPenalty() * candidate.openReportCount());
    }

    /**
     * The ids of the best {@code limit} candidates for {@code viewerId}, in feed order. Ties go to the newer post.
     * {@code followedAuthors} holds whom of the candidates' authors the viewer follows.
     */
    public static List<UUID> rank(List<RankingCandidate> candidates, FeedRankingSettings settings, LocalDateTime now,
                                  int limit, UUID viewerId, Set<UUID> followedAuthors) {
        Map<UUID, Double> scores = new HashMap<>();
        candidates.forEach(c -> scores.put(c.postId(),
                score(c, settings, now) * familiarity(c.authorId(), settings, viewerId, followedAuthors)));
        List<RankingCandidate> sorted = candidates.stream()
                .sorted(Comparator.comparingDouble((RankingCandidate c) -> scores.get(c.postId())).reversed()
                        .thenComparing(RankingCandidate::createdAt, Comparator.reverseOrder())
                        .thenComparing(RankingCandidate::postId, Comparator.reverseOrder()))
                .toList();
        return diversify(sorted, settings.maxPerAuthor(), FeedRankingSettings.DIVERSITY_WINDOW, limit).stream()
                .map(RankingCandidate::postId)
                .toList();
    }

    static double familiarity(UUID authorId, FeedRankingSettings settings, UUID viewerId, Set<UUID> followedAuthors) {
        if (authorId.equals(viewerId)) {
            return settings.ownFactor();
        }
        return followedAuthors.contains(authorId) ? settings.followedFactor() : 1;
    }

    /**
     * Fills the order window by window, at most {@code maxPerAuthor} posts of an author in each. A post that does
     * not fit keeps its place in the queue, so it opens the next window instead of sinking to the end. When only one
     * author is left, their posts simply follow each other.
     */
    static List<RankingCandidate> diversify(List<RankingCandidate> sorted, int maxPerAuthor, int window, int limit) {
        List<RankingCandidate> remaining = new ArrayList<>(sorted);
        List<RankingCandidate> ordered = new ArrayList<>(Math.min(limit, sorted.size()));
        while (!remaining.isEmpty() && ordered.size() < limit) {
            Map<UUID, Integer> perAuthor = new HashMap<>();
            List<RankingCandidate> skipped = new ArrayList<>();
            int taken = 0;
            int i = 0;
            for (; i < remaining.size() && taken < window && ordered.size() < limit; i++) {
                RankingCandidate candidate = remaining.get(i);
                int count = perAuthor.getOrDefault(candidate.authorId(), 0);
                if (count < maxPerAuthor) {
                    perAuthor.put(candidate.authorId(), count + 1);
                    ordered.add(candidate);
                    taken++;
                } else {
                    skipped.add(candidate);
                }
            }
            skipped.addAll(remaining.subList(i, remaining.size()));
            remaining = skipped;
        }
        return ordered;
    }
}
