package com.rizkysiregar.narrativ.service.feed;

import com.rizkysiregar.narrativ.model.dto.RankingCandidate;
import com.rizkysiregar.narrativ.model.entity.ParameterEntity;
import com.rizkysiregar.narrativ.model.entity.PostEntity;
import com.rizkysiregar.narrativ.model.entity.UserEntity;
import com.rizkysiregar.narrativ.repository.ParameterRepository;
import com.rizkysiregar.narrativ.repository.PostPaginationRepository;
import com.rizkysiregar.narrativ.util.RankedCursor;
import com.rizkysiregar.narrativ.util.RedisUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RankedFeedServiceTest {

    @Mock
    private PostPaginationRepository postPaginationRepository;
    @Mock
    private ParameterRepository parameterRepository;
    @Mock
    private RedisUtil redisUtil;

    @InjectMocks
    private RankedFeedService rankedFeedService;

    private final UUID viewerId = UUID.randomUUID();

    private static PostEntity post(UUID id) {
        UserEntity author = new UserEntity();
        author.setId(UUID.randomUUID());
        return PostEntity.builder().id(id).user(author).createdAt(LocalDateTime.now()).build();
    }

    // n candidates of different authors, best first: fewer likes and older down the list
    private static List<RankingCandidate> candidates(int n) {
        LocalDateTime now = LocalDateTime.now();
        return java.util.stream.IntStream.range(0, n)
                .mapToObj(i -> new RankingCandidate(UUID.randomUUID(), UUID.randomUUID(), now.minusHours(i), 100 - i, 0, 0))
                .toList();
    }

    private static List<UUID> ids(List<RankingCandidate> candidates) {
        return candidates.stream().map(RankingCandidate::postId).toList();
    }

    private void allVisible() {
        when(postPaginationRepository.findRankedVisibleByIds(any(), eq(viewerId))).thenAnswer(invocation ->
                ((List<UUID>) invocation.getArgument(0)).reversed().stream().map(RankedFeedServiceTest::post).toList());
    }

    @Test
    void startRanksStoresTheOrderAndServesTheFirstPage() {
        List<RankingCandidate> candidates = candidates(5);
        when(postPaginationRepository.findRankingCandidates(eq(viewerId), any(), eq(false),
                eq(PageRequest.of(0, RankedFeedService.MAX_CANDIDATES)))).thenReturn(candidates);
        allVisible();

        RankedFeedService.Slice slice = rankedFeedService.start(viewerId, false, 0, 2).orElseThrow();

        // in ranked order, whatever order the database returned them in
        assertEquals(ids(candidates).subList(0, 2), slice.posts().stream().map(PostEntity::getId).toList());
        assertEquals(2, slice.next().offset());
        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object> stored = ArgumentCaptor.forClass(Object.class);
        verify(redisUtil).set(key.capture(), stored.capture(), eq(RankedFeedService.SNAPSHOT_TTL_SECONDS));
        assertEquals("feed:ranked:" + viewerId + ":" + slice.next().snapshotId(), key.getValue());
        assertEquals(String.join(",", ids(candidates).stream().map(UUID::toString).toList()), stored.getValue());
    }

    @Test
    void theWindowComesFromTheParameters() {
        when(parameterRepository.findByParamKeyStartingWith("feed.ranking."))
                .thenReturn(List.of(new ParameterEntity("feed.ranking.window_days", "3")));
        LocalDateTime before = LocalDateTime.now().minusDays(3);

        RankedFeedService.Slice slice = rankedFeedService.start(viewerId, true, 0, 10).orElseThrow();

        ArgumentCaptor<LocalDateTime> since = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(postPaginationRepository).findRankingCandidates(eq(viewerId), since.capture(), eq(true), any());
        assertTrue(!since.getValue().isBefore(before) && since.getValue().isBefore(before.plusMinutes(1)));
        // nothing to rank: nothing stored, the feed goes on by date
        assertTrue(slice.posts().isEmpty());
        assertNull(slice.next());
        verify(redisUtil, never()).set(anyString(), any(), anyLong());
    }

    @Test
    void withoutRedisThereIsNoRankedFeed() {
        when(postPaginationRepository.findRankingCandidates(any(), any(), eq(false), any())).thenReturn(candidates(3));
        doThrow(new RuntimeException("connection refused")).when(redisUtil).set(anyString(), any(), anyLong());

        assertEquals(Optional.empty(), rankedFeedService.start(viewerId, false, 0, 10));
    }

    @Test
    void resumeCutsTheNextPageAndSkipsPostsGoneSinceTheRanking() {
        UUID snapshotId = UUID.randomUUID();
        List<UUID> ranked = List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        when(redisUtil.get("feed:ranked:" + viewerId + ":" + snapshotId))
                .thenReturn(String.join(",", ranked.stream().map(UUID::toString).toList()));
        // ranked[2] was deleted since
        when(postPaginationRepository.findRankedVisibleByIds(List.of(ranked.get(1), ranked.get(2)), viewerId))
                .thenReturn(List.of(post(ranked.get(1))));
        when(postPaginationRepository.findRankedVisibleByIds(List.of(ranked.get(3)), viewerId))
                .thenReturn(List.of(post(ranked.get(3))));
        LocalDateTime windowStart = LocalDateTime.of(2026, 11, 11, 10, 0);

        RankedFeedService.Slice slice = rankedFeedService.resume(new RankedCursor(snapshotId, 1, windowStart), viewerId, 2).orElseThrow();

        assertEquals(List.of(ranked.get(1), ranked.get(3)), slice.posts().stream().map(PostEntity::getId).toList());
        assertEquals(new RankedCursor(snapshotId, 4, windowStart), slice.next());
    }

    @Test
    void theLastPageOfARankingHasNoNext() {
        UUID snapshotId = UUID.randomUUID();
        UUID only = UUID.randomUUID();
        when(redisUtil.get("feed:ranked:" + viewerId + ":" + snapshotId)).thenReturn(only.toString());
        when(postPaginationRepository.findRankedVisibleByIds(List.of(only), viewerId)).thenReturn(List.of(post(only)));

        RankedFeedService.Slice slice = rankedFeedService.resume(new RankedCursor(snapshotId, 0, LocalDateTime.now()), viewerId, 10).orElseThrow();

        assertEquals(1, slice.posts().size());
        assertNull(slice.next());
    }

    @Test
    void anExpiredRankingOrOneOfSomebodyElseIsGone() {
        RankedCursor cursor = new RankedCursor(UUID.randomUUID(), 10, LocalDateTime.now());

        assertEquals(Optional.empty(), rankedFeedService.resume(cursor, viewerId, 10));
        verify(postPaginationRepository, never()).findRankedVisibleByIds(any(), any());
    }

    @Test
    void anUnreachableRedisCountsAsExpired() {
        when(redisUtil.get(anyString())).thenThrow(new RuntimeException("timeout"));

        assertEquals(Optional.empty(), rankedFeedService.resume(new RankedCursor(UUID.randomUUID(), 0, LocalDateTime.now()), viewerId, 10));
    }
}
