package com.rizkysiregar.narrativ.service.feed;

import com.rizkysiregar.narrativ.model.entity.ParameterEntity;
import com.rizkysiregar.narrativ.model.entity.PostEntity;
import com.rizkysiregar.narrativ.repository.ParameterRepository;
import com.rizkysiregar.narrativ.repository.PostPaginationRepository;
import com.rizkysiregar.narrativ.util.RankedCursor;
import com.rizkysiregar.narrativ.util.RedisUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The ranked part of explore, also shown as suggestions once the home feed runs out.
 * <p>
 * The first page ranks the recent posts the viewer may discover ({@link FeedRanker}) and keeps the order in Redis
 * for a while; the next pages are cut from that same order. Scores move with every like, so ranking again on each
 * page would show a post twice or skip one. A page checks its posts again, so one deleted, taken down or blocked
 * since the ranking is left out.
 * <p>
 * Without Redis there is no ranked feed: {@link #start} answers empty and explore falls back to its date order.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RankedFeedService {

    // how many posts one ranking holds; past them the feed goes on by date
    static final int SNAPSHOT_SIZE = 500;
    // the newest posts of the window that are scored at most, so a busy week cannot make the ranking slow
    static final int MAX_CANDIDATES = 5000;
    static final long SNAPSHOT_TTL_SECONDS = 30L * 60;

    private final PostPaginationRepository postPaginationRepository;
    private final ParameterRepository parameterRepository;
    private final RedisUtil redisUtil;

    /**
     * The posts of a ranked page in feed order. {@code next} is null once the ranking is used up; the feed then
     * goes on by date from {@code windowStart}.
     */
    public record Slice(List<PostEntity> posts, RankedCursor next, LocalDateTime windowStart) {
    }

    /** Ranks anew and returns {@code count} posts after the first {@code skip}; empty when Redis is unavailable. */
    public Optional<Slice> start(UUID viewerId, boolean mediaOnly, int skip, int count) {
        FeedRankingSettings settings = settings();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime windowStart = now.minusDays(settings.windowDays());
        List<UUID> ranked = FeedRanker.rank(
                postPaginationRepository.findRankingCandidates(viewerId, windowStart, mediaOnly, PageRequest.of(0, MAX_CANDIDATES)),
                settings, now, SNAPSHOT_SIZE);
        log.debug("FEED.RANKING.BUILT viewerId={} mediaOnly={} posts={}", viewerId, mediaOnly, ranked.size());
        if (ranked.isEmpty()) {
            return Optional.of(new Slice(List.of(), null, windowStart));
        }

        UUID snapshotId = UUID.randomUUID();
        try {
            redisUtil.set(key(viewerId, snapshotId), ranked.stream().map(UUID::toString)
                    .collect(Collectors.joining(",")), SNAPSHOT_TTL_SECONDS);
        } catch (RuntimeException e) {
            log.warn("FEED.RANKING.STORE.FAILED viewerId={} reason={}", viewerId, e.getMessage());
            return Optional.empty();
        }
        return Optional.of(slice(snapshotId, ranked, windowStart, skip, count, viewerId));
    }

    /** The next page of a ranking; empty when it expired (or belongs to somebody else). */
    public Optional<Slice> resume(RankedCursor cursor, UUID viewerId, int count) {
        Object stored;
        try {
            stored = redisUtil.get(key(viewerId, cursor.snapshotId()));
        } catch (RuntimeException e) {
            log.warn("FEED.RANKING.READ.FAILED viewerId={} reason={}", viewerId, e.getMessage());
            return Optional.empty();
        }
        if (!(stored instanceof String value)) {
            return Optional.empty();
        }
        List<UUID> ranked = Arrays.stream(value.split(","))
                .map(UUID::fromString)
                .toList();
        return Optional.of(slice(cursor.snapshotId(), ranked, cursor.windowStart(), cursor.offset(), count, viewerId));
    }

    private Slice slice(UUID snapshotId, List<UUID> ranked, LocalDateTime windowStart,
                        int offset, int count, UUID viewerId) {
        List<PostEntity> posts = new ArrayList<>(count);
        int position = Math.min(offset, ranked.size());
        // a post gone since the ranking leaves a hole, which the posts after it fill
        while (posts.size() < count && position < ranked.size()) {
            List<UUID> batch = ranked.subList(position, Math.min(ranked.size(), position + count - posts.size()));
            Map<UUID, PostEntity> visible = postPaginationRepository.findRankedVisibleByIds(batch, viewerId).stream()
                    .collect(Collectors.toMap(PostEntity::getId, Function.identity()));
            batch.stream().map(visible::get).filter(Objects::nonNull).forEach(posts::add);
            position += batch.size();
        }
        RankedCursor next = position < ranked.size() ? new RankedCursor(snapshotId, position, windowStart) : null;
        return new Slice(posts, next, windowStart);
    }

    private FeedRankingSettings settings() {
        return FeedRankingSettings.from(parameterRepository.findByParamKeyStartingWith(FeedRankingSettings.PREFIX)
                .stream()
                .collect(Collectors.toMap(ParameterEntity::getParamKey, ParameterEntity::getParamValue)));
    }

    // the viewer is part of the key: the ranking leaves out whom they follow, so it is theirs alone
    private static String key(UUID viewerId, UUID snapshotId) {
        return "feed:ranked:" + viewerId + ":" + snapshotId;
    }
}
