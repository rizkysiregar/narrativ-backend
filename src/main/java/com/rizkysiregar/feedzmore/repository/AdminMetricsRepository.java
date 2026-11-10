package com.rizkysiregar.feedzmore.repository;

import com.rizkysiregar.feedzmore.model.enums.GrowthSource;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Aggregations for the admin dashboard. users.created_at and posts.created_at are timestamps without a time zone
 * that hold the time in the zone of the JVM, so a calendar day of the admin zone is turned into server time for the
 * range and into the admin zone again for the grouping.
 */
@Repository
@RequiredArgsConstructor
public class AdminMetricsRepository {

    public record UserCounts(long total, long active, long banned, long deleted,
                             long newToday, long newThisWeek, long newThisMonth) {}

    public record PostCounts(long total, long image, long video, long textOnly, long active, long takenDown, long deleted,
                             long newToday, long newThisWeek, long newThisMonth) {}

    private final EntityManager entityManager;

    /**
     * Rows created in [from, toExclusive) per bucket; the key is the first day of the bucket (the Monday of a week,
     * the 1st of a month). Buckets without rows are not in the result.
     */
    public Map<LocalDate, Long> countPerBucket(GrowthSource source, String sqlUnit, LocalDateTime from,
                                               LocalDateTime toExclusive, String serverZone, String adminZone) {
        Query query = entityManager.createNativeQuery("""
                SELECT to_char(date_trunc(:unit, (created_at AT TIME ZONE :serverZone) AT TIME ZONE :adminZone), 'YYYY-MM-DD'),
                       COUNT(*)
                FROM %s
                WHERE created_at >= :from AND created_at < :to
                GROUP BY 1
                ORDER BY 1
                """.formatted(source.tableName()));
        query.setParameter("unit", sqlUnit);
        query.setParameter("serverZone", serverZone);
        query.setParameter("adminZone", adminZone);
        query.setParameter("from", from);
        query.setParameter("to", toExclusive);

        Map<LocalDate, Long> result = new LinkedHashMap<>();
        for (Object row : query.getResultList()) {
            Object[] columns = (Object[]) row;
            result.put(LocalDate.parse((String) columns[0]), ((Number) columns[1]).longValue());
        }
        return result;
    }

    public long countCreatedBetween(GrowthSource source, LocalDateTime from, LocalDateTime toExclusive) {
        Query query = entityManager.createNativeQuery(
                "SELECT COUNT(*) FROM %s WHERE created_at >= :from AND created_at < :to".formatted(source.tableName()));
        query.setParameter("from", from);
        query.setParameter("to", toExclusive);
        return ((Number) query.getSingleResult()).longValue();
    }

    public UserCounts userCounts(LocalDateTime todayStart, LocalDateTime weekStart, LocalDateTime monthStart) {
        Query query = entityManager.createNativeQuery("""
                SELECT COUNT(*),
                       COUNT(*) FILTER (WHERE NOT is_deleted AND status = 'ACTIVE'),
                       COUNT(*) FILTER (WHERE NOT is_deleted AND status = 'BANNED'),
                       COUNT(*) FILTER (WHERE is_deleted),
                       COUNT(*) FILTER (WHERE created_at >= :today),
                       COUNT(*) FILTER (WHERE created_at >= :week),
                       COUNT(*) FILTER (WHERE created_at >= :month)
                FROM users
                """);
        bindPeriods(query, todayStart, weekStart, monthStart);
        long[] c = row(query);
        return new UserCounts(c[0], c[1], c[2], c[3], c[4], c[5], c[6]);
    }

    public PostCounts postCounts(LocalDateTime todayStart, LocalDateTime weekStart, LocalDateTime monthStart) {
        Query query = entityManager.createNativeQuery("""
                SELECT COUNT(*),
                       COUNT(*) FILTER (WHERE EXISTS (SELECT 1 FROM post_media m WHERE m.post_id = posts.id AND m.media_type = 'IMAGE')),
                       COUNT(*) FILTER (WHERE EXISTS (SELECT 1 FROM post_media m WHERE m.post_id = posts.id AND m.media_type = 'VIDEO')),
                       COUNT(*) FILTER (WHERE NOT EXISTS (SELECT 1 FROM post_media m WHERE m.post_id = posts.id)),
                       COUNT(*) FILTER (WHERE NOT is_deleted AND moderation_status = 'ACTIVE'),
                       COUNT(*) FILTER (WHERE NOT is_deleted AND moderation_status = 'TAKEN_DOWN'),
                       COUNT(*) FILTER (WHERE is_deleted),
                       COUNT(*) FILTER (WHERE created_at >= :today),
                       COUNT(*) FILTER (WHERE created_at >= :week),
                       COUNT(*) FILTER (WHERE created_at >= :month)
                FROM posts
                """);
        bindPeriods(query, todayStart, weekStart, monthStart);
        long[] c = row(query);
        return new PostCounts(c[0], c[1], c[2], c[3], c[4], c[5], c[6], c[7], c[8], c[9]);
    }

    private void bindPeriods(Query query, LocalDateTime todayStart, LocalDateTime weekStart, LocalDateTime monthStart) {
        query.setParameter("today", todayStart);
        query.setParameter("week", weekStart);
        query.setParameter("month", monthStart);
    }

    private long[] row(Query query) {
        Object[] columns = (Object[]) query.getSingleResult();
        long[] values = new long[columns.length];
        for (int i = 0; i < columns.length; i++) {
            values[i] = ((Number) columns[i]).longValue();
        }
        return values;
    }
}
