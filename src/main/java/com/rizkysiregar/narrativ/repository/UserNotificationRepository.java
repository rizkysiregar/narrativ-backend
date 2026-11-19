package com.rizkysiregar.narrativ.repository;

import com.rizkysiregar.narrativ.model.entity.UserNotificationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface UserNotificationRepository extends JpaRepository<UserNotificationEntity, UUID> {

    /**
     * The notifications [recipientId] may still see: the actor is an active account on neither side of a block, and
     * the post or comment it is about is still shown. Hidden rows stay, so an unblock or a restored post brings them
     * back.
     */
    String VISIBLE = """
            FROM user_notification n
            JOIN users a ON a.id = n.actor_id
            LEFT JOIN posts p ON p.id = n.post_id
            LEFT JOIN comments c ON c.id = n.comment_id
            WHERE n.recipient_id = :recipientId
              AND a.is_deleted = false AND a.status = 'ACTIVE'
              AND (n.post_id IS NULL OR (p.is_deleted = false AND p.moderation_status = 'ACTIVE'))
              AND (n.comment_id IS NULL OR c.moderation_status = 'ACTIVE')
              AND NOT EXISTS (SELECT 1 FROM user_blocks b
                              WHERE (b.blocker_id = :recipientId AND b.blocked_id = n.actor_id)
                                 OR (b.blocker_id = n.actor_id AND b.blocked_id = :recipientId))
            """;

    String LAST_AT_MICROS = "CAST(EXTRACT(EPOCH FROM MAX(n.created_at)) * 1000000 AS BIGINT)";

    /** One inbox entry: the rows that share a group key, newest entry first. */
    interface GroupRow {
        String getGroupKey();

        Long getLastAtMicros();

        Long getActorCount();

        Boolean getUnread();

        UUID getLatestId();

        // the second newest row of the group, for "ani and budi"; null when the group has one row
        UUID getSecondId();
    }

    /**
     * A page of inbox entries strictly after the cursor ([beforeMicros], [beforeKey]), newest first. The first page
     * passes Long.MAX_VALUE and an empty key.
     */
    @Query(nativeQuery = true, value = """
            SELECT n.group_key AS "groupKey",
                   """ + LAST_AT_MICROS + """
                    AS "lastAtMicros",
                   COUNT(*) AS "actorCount",
                   BOOL_OR(n.read_at IS NULL) AS "unread",
                   (ARRAY_AGG(n.id ORDER BY n.created_at DESC, n.id DESC))[1] AS "latestId",
                   (ARRAY_AGG(n.id ORDER BY n.created_at DESC, n.id DESC))[2] AS "secondId"
            """ + VISIBLE + """
            GROUP BY n.group_key
            HAVING """ + LAST_AT_MICROS + """
                    < :beforeMicros
                OR (""" + LAST_AT_MICROS + """
                     = :beforeMicros AND n.group_key < :beforeKey)
            ORDER BY "lastAtMicros" DESC, "groupKey" DESC
            LIMIT :limit
            """)
    List<GroupRow> findGroups(@Param("recipientId") UUID recipientId,
                              @Param("beforeMicros") long beforeMicros,
                              @Param("beforeKey") String beforeKey,
                              @Param("limit") int limit);

    /** Inbox entries with at least one unread row, what the badge on the bell shows. */
    @Query(nativeQuery = true, value = "SELECT COUNT(DISTINCT n.group_key) " + VISIBLE + " AND n.read_at IS NULL")
    long countUnreadGroups(@Param("recipientId") UUID recipientId);

    @Query("SELECT n FROM UserNotificationEntity n JOIN FETCH n.actor WHERE n.id IN :ids")
    List<UserNotificationEntity> findWithActorByIdIn(@Param("ids") Collection<UUID> ids);

    boolean existsByDedupKey(String dedupKey);

    @Modifying
    @Query("DELETE FROM UserNotificationEntity n WHERE n.dedupKey = :dedupKey")
    int deleteByDedupKey(@Param("dedupKey") String dedupKey);

    @Modifying
    @Query("UPDATE UserNotificationEntity n SET n.readAt = :now WHERE n.recipientId = :recipientId AND n.readAt IS NULL")
    int markAllRead(@Param("recipientId") UUID recipientId, @Param("now") Instant now);

    @Modifying
    @Query("""
            UPDATE UserNotificationEntity n SET n.readAt = :now
            WHERE n.recipientId = :recipientId AND n.groupKey = :groupKey AND n.readAt IS NULL
            """)
    int markGroupRead(@Param("recipientId") UUID recipientId, @Param("groupKey") String groupKey,
                      @Param("now") Instant now);

    @Modifying
    @Query("DELETE FROM UserNotificationEntity n WHERE n.createdAt < :before")
    int deleteOlderThan(@Param("before") Instant before);
}
