package com.rizkysiregar.narrativ.repository;

import com.rizkysiregar.narrativ.model.dto.RankingCandidate;
import com.rizkysiregar.narrativ.model.entity.PostEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PostPaginationRepository extends PagingAndSortingRepository<PostEntity, UUID> {

    /** The home feed: the viewer's own posts and those of the people they follow. */
    @Query(value = """
            SELECT p FROM PostEntity p JOIN FETCH p.user author
            WHERE p.isDeleted = false AND author.isDeleted = false AND author.status = com.rizkysiregar.narrativ.model.enums.UserStatus.ACTIVE
              AND (author.id = :viewerId OR EXISTS (
                  SELECT f.id FROM FollowEntity f
                  WHERE f.follower.id = :viewerId AND f.followed.id = author.id
              ))
              AND NOT EXISTS (SELECT b.id FROM UserBlockEntity b WHERE (b.blockerId = :viewerId AND b.blockedId = author.id) OR (b.blockerId = author.id AND b.blockedId = :viewerId))
            ORDER BY p.createdAt DESC, p.id DESC
            """, countQuery = """
            SELECT COUNT(p) FROM PostEntity p JOIN p.user author
            WHERE p.isDeleted = false AND author.isDeleted = false AND author.status = com.rizkysiregar.narrativ.model.enums.UserStatus.ACTIVE
              AND (author.id = :viewerId OR EXISTS (
                  SELECT f.id FROM FollowEntity f
                  WHERE f.follower.id = :viewerId AND f.followed.id = author.id
              ))
              AND NOT EXISTS (SELECT b.id FROM UserBlockEntity b WHERE (b.blockerId = :viewerId AND b.blockedId = author.id) OR (b.blockerId = author.id AND b.blockedId = :viewerId))
            """)
    Page<PostEntity> findFollowingPosts(Pageable pageable, @Param("viewerId") UUID viewerId);

    @Query(value = """
            SELECT p FROM PostEntity p JOIN FETCH p.user author
            WHERE author.id = :userId AND author.isDeleted = false AND author.status = com.rizkysiregar.narrativ.model.enums.UserStatus.ACTIVE AND p.isDeleted = false
              AND NOT EXISTS (SELECT b.id FROM UserBlockEntity b WHERE (b.blockerId = :viewerId AND b.blockedId = author.id) OR (b.blockerId = author.id AND b.blockedId = :viewerId))
              AND (:mediaOnly = false OR EXISTS (SELECT m.id FROM PostMediaEntity m WHERE m.postId = p.id))
            ORDER BY p.createdAt DESC, p.id DESC
            """, countQuery = """
            SELECT COUNT(p) FROM PostEntity p JOIN p.user author
            WHERE author.id = :userId AND author.isDeleted = false AND author.status = com.rizkysiregar.narrativ.model.enums.UserStatus.ACTIVE AND p.isDeleted = false
              AND NOT EXISTS (SELECT b.id FROM UserBlockEntity b WHERE (b.blockerId = :viewerId AND b.blockedId = author.id) OR (b.blockerId = author.id AND b.blockedId = :viewerId))
              AND (:mediaOnly = false OR EXISTS (SELECT m.id FROM PostMediaEntity m WHERE m.postId = p.id))
            """)
    Page<PostEntity> findAllActivePostsByUserId(Pageable pageable, @Param("userId") UUID userId,
                                                @Param("mediaOnly") boolean mediaOnly, @Param("viewerId") UUID viewerId);

    /**
     * One post the viewer may see: same visibility as the feeds (not deleted, author active, no block either way).
     * A taken down post is still found; the service masks its content for everybody but the author.
     */
    @Query("""
            SELECT p FROM PostEntity p JOIN FETCH p.user author
            WHERE p.id = :postId AND p.isDeleted = false AND author.isDeleted = false AND author.status = com.rizkysiregar.narrativ.model.enums.UserStatus.ACTIVE
              AND NOT EXISTS (SELECT b.id FROM UserBlockEntity b WHERE (b.blockerId = :viewerId AND b.blockedId = author.id) OR (b.blockerId = author.id AND b.blockedId = :viewerId))
            """)
    Optional<PostEntity> findVisibleById(@Param("postId") UUID postId, @Param("viewerId") UUID viewerId);

    // --- keyset variants of the feeds above: the posts strictly after a cursor, no count query ---

    /**
     * Explore by date, where the ranked part of it ends (see {@link #findRankingCandidates}): every post the viewer
     * may see but taken down ones, so explore is only empty when there is nothing to show at all.
     */
    @Query("""
            SELECT p FROM PostEntity p JOIN FETCH p.user author
            WHERE p.isDeleted = false AND author.isDeleted = false AND author.status = com.rizkysiregar.narrativ.model.enums.UserStatus.ACTIVE
              AND p.moderationStatus <> com.rizkysiregar.narrativ.model.enums.ModerationStatus.TAKEN_DOWN
              AND NOT EXISTS (SELECT b.id FROM UserBlockEntity b WHERE (b.blockerId = :viewerId AND b.blockedId = author.id) OR (b.blockerId = author.id AND b.blockedId = :viewerId))
              AND (:mediaOnly = false OR EXISTS (SELECT m.id FROM PostMediaEntity m WHERE m.postId = p.id))
              AND (p.createdAt < :cursorAt OR (p.createdAt = :cursorAt AND p.id < :cursorId))
            ORDER BY p.createdAt DESC, p.id DESC
            """)
    List<PostEntity> findExplorePostsAfter(@Param("mediaOnly") boolean mediaOnly, @Param("viewerId") UUID viewerId,
                                             @Param("cursorAt") LocalDateTime cursorAt, @Param("cursorId") UUID cursorId,
                                             Pageable limit);

    @Query("""
            SELECT p FROM PostEntity p JOIN FETCH p.user author
            WHERE p.isDeleted = false AND author.isDeleted = false AND author.status = com.rizkysiregar.narrativ.model.enums.UserStatus.ACTIVE
              AND (author.id = :viewerId OR EXISTS (
                  SELECT f.id FROM FollowEntity f
                  WHERE f.follower.id = :viewerId AND f.followed.id = author.id
              ))
              AND NOT EXISTS (SELECT b.id FROM UserBlockEntity b WHERE (b.blockerId = :viewerId AND b.blockedId = author.id) OR (b.blockerId = author.id AND b.blockedId = :viewerId))
              AND (p.createdAt < :cursorAt OR (p.createdAt = :cursorAt AND p.id < :cursorId))
            ORDER BY p.createdAt DESC, p.id DESC
            """)
    List<PostEntity> findFollowingPostsAfter(@Param("viewerId") UUID viewerId,
                                             @Param("cursorAt") LocalDateTime cursorAt, @Param("cursorId") UUID cursorId,
                                             Pageable limit);

    @Query("""
            SELECT p FROM PostEntity p JOIN FETCH p.user author
            WHERE author.id = :userId AND author.isDeleted = false AND author.status = com.rizkysiregar.narrativ.model.enums.UserStatus.ACTIVE AND p.isDeleted = false
              AND NOT EXISTS (SELECT b.id FROM UserBlockEntity b WHERE (b.blockerId = :viewerId AND b.blockedId = author.id) OR (b.blockerId = author.id AND b.blockedId = :viewerId))
              AND (:mediaOnly = false OR EXISTS (SELECT m.id FROM PostMediaEntity m WHERE m.postId = p.id))
              AND (p.createdAt < :cursorAt OR (p.createdAt = :cursorAt AND p.id < :cursorId))
            ORDER BY p.createdAt DESC, p.id DESC
            """)
    List<PostEntity> findAllActivePostsByUserIdAfter(@Param("userId") UUID userId, @Param("mediaOnly") boolean mediaOnly,
                                                     @Param("viewerId") UUID viewerId,
                                                     @Param("cursorAt") LocalDateTime cursorAt, @Param("cursorId") UUID cursorId,
                                                     Pageable limit);

    // --- the ranked feeds ---

    /**
     * What the ranked feeds pick from: the posts since {@code since} that explore may show (same filters as
     * {@link #findExplorePostsAfter}), newest first, with the counts their score is made of. The author's own likes
     * and comments do not count, so nobody lifts their own post. {@code onlyUnknown} leaves out the viewer's own
     * posts and those of the people they follow, for the suggestions under the home feed, which already lists them.
     */
    @Query("""
            SELECT new com.rizkysiregar.narrativ.model.dto.RankingCandidate(p.id, author.id, p.createdAt,
                (SELECT COUNT(l) FROM LikeEntity l WHERE l.post.id = p.id AND l.user.id <> author.id),
                (SELECT COUNT(c) FROM CommentEntity c WHERE c.post.id = p.id AND c.user.id <> author.id),
                (SELECT COUNT(r) FROM ReportEntity r WHERE r.targetType = com.rizkysiregar.narrativ.model.enums.ReportTargetType.POST
                    AND r.targetId = p.id AND r.status = com.rizkysiregar.narrativ.model.enums.ReportStatus.OPEN))
            FROM PostEntity p JOIN p.user author
            WHERE p.createdAt >= :since
              AND p.isDeleted = false AND author.isDeleted = false AND author.status = com.rizkysiregar.narrativ.model.enums.UserStatus.ACTIVE
              AND p.moderationStatus <> com.rizkysiregar.narrativ.model.enums.ModerationStatus.TAKEN_DOWN
              AND (:onlyUnknown = false OR (author.id <> :viewerId
                  AND NOT EXISTS (SELECT f.id FROM FollowEntity f WHERE f.follower.id = :viewerId AND f.followed.id = author.id)))
              AND NOT EXISTS (SELECT b.id FROM UserBlockEntity b WHERE (b.blockerId = :viewerId AND b.blockedId = author.id) OR (b.blockerId = author.id AND b.blockedId = :viewerId))
              AND (:mediaOnly = false OR EXISTS (SELECT m.id FROM PostMediaEntity m WHERE m.postId = p.id))
            ORDER BY p.createdAt DESC, p.id DESC
            """)
    List<RankingCandidate> findRankingCandidates(@Param("viewerId") UUID viewerId, @Param("since") LocalDateTime since,
                                                 @Param("mediaOnly") boolean mediaOnly, @Param("onlyUnknown") boolean onlyUnknown,
                                                 Pageable limit);

    /**
     * The posts of a ranked page, checked again when the page is served: a post deleted, taken down or hidden by a
     * block since the ranking was made is left out. In no particular order; the caller keeps the ranked one.
     */
    @Query("""
            SELECT p FROM PostEntity p JOIN FETCH p.user author
            WHERE p.id IN :postIds AND p.isDeleted = false AND author.isDeleted = false AND author.status = com.rizkysiregar.narrativ.model.enums.UserStatus.ACTIVE
              AND p.moderationStatus <> com.rizkysiregar.narrativ.model.enums.ModerationStatus.TAKEN_DOWN
              AND NOT EXISTS (SELECT b.id FROM UserBlockEntity b WHERE (b.blockerId = :viewerId AND b.blockedId = author.id) OR (b.blockerId = author.id AND b.blockedId = :viewerId))
            """)
    List<PostEntity> findRankedVisibleByIds(@Param("postIds") List<UUID> postIds, @Param("viewerId") UUID viewerId);
}
