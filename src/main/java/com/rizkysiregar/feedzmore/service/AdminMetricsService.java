package com.rizkysiregar.feedzmore.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rizkysiregar.feedzmore.constant.Constants;
import com.rizkysiregar.feedzmore.constant.GlobalMessage;
import com.rizkysiregar.feedzmore.exception.BusinessException;
import com.rizkysiregar.feedzmore.model.dto.AdminGrowthDTO;
import com.rizkysiregar.feedzmore.model.dto.AdminOverviewDTO;
import com.rizkysiregar.feedzmore.model.enums.GrowthRange;
import com.rizkysiregar.feedzmore.model.enums.GrowthSource;
import com.rizkysiregar.feedzmore.model.enums.MetricGranularity;
import com.rizkysiregar.feedzmore.model.enums.ReportStatus;
import com.rizkysiregar.feedzmore.repository.AdminMetricsRepository;
import com.rizkysiregar.feedzmore.repository.CommentRepository;
import com.rizkysiregar.feedzmore.repository.ReportRepository;
import com.rizkysiregar.feedzmore.util.AdminDates;
import com.rizkysiregar.feedzmore.util.RedisUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Dashboard statistics: an overview and the growth of users and posts. All days, weeks (Monday to Sunday) and months
 * are those of {@link Constants#ADMIN_ZONE}. Results are cached in Redis for a minute; if Redis is not there the
 * numbers are simply computed again.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AdminMetricsService {

    static final long CACHE_TTL_SECONDS = 60;
    static final int MAX_DAYS_PER_DAY_RANGE = 366;
    static final int MAX_YEARS_PER_WEEK_RANGE = 2;
    static final int MAX_YEARS_PER_MONTH_RANGE = 5;

    private final AdminMetricsRepository metricsRepository;
    private final CommentRepository commentRepository;
    private final ReportRepository reportRepository;
    private final RedisUtil redisUtil;
    private final ObjectMapper objectMapper;

    // ------------------------------------------------------------------ overview

    @Transactional(readOnly = true)
    public AdminOverviewDTO overview() {
        return overview(LocalDate.now(Constants.ADMIN_ZONE), Instant.now());
    }

    AdminOverviewDTO overview(LocalDate today, Instant now) {
        return cached("admin:metrics:overview:" + today, AdminOverviewDTO.class, () -> {
            var todayStart = AdminDates.startInclusive(today);
            var weekStart = AdminDates.startInclusive(today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)));
            var monthStart = AdminDates.startInclusive(today.withDayOfMonth(1));

            var users = metricsRepository.userCounts(todayStart, weekStart, monthStart);
            var posts = metricsRepository.postCounts(todayStart, weekStart, monthStart);
            return new AdminOverviewDTO(
                    now,
                    Constants.ADMIN_ZONE.getId(),
                    new AdminOverviewDTO.Users(users.total(), users.active(), users.banned(), users.deleted(),
                            users.newToday(), users.newThisWeek(), users.newThisMonth()),
                    new AdminOverviewDTO.Posts(posts.total(), posts.image(), posts.video(), posts.active(),
                            posts.takenDown(), posts.deleted(), posts.newToday(), posts.newThisWeek(), posts.newThisMonth()),
                    commentRepository.count(),
                    reportRepository.countByStatus(ReportStatus.OPEN));
        });
    }

    // -------------------------------------------------------------------- growth

    /**
     * @param granularity DAY, WEEK or MONTH, DAY when empty
     * @param range       a {@link GrowthRange} preset; give either this or both from and to. With neither the
     *                    last 30 days are used.
     */
    @Transactional(readOnly = true)
    public AdminGrowthDTO growth(GrowthSource source, String granularity, String range, LocalDate from, LocalDate to) {
        return growth(source, granularity, range, from, to, LocalDate.now(Constants.ADMIN_ZONE));
    }

    AdminGrowthDTO growth(GrowthSource source, String granularity, String range, LocalDate from, LocalDate to, LocalDate today) {
        MetricGranularity unit = granularity == null || granularity.isBlank()
                ? MetricGranularity.DAY : parseEnum(MetricGranularity.class, granularity);
        LocalDate[] window = resolveWindow(range, from, to, today);
        LocalDate start = window[0];
        LocalDate end = window[1];
        requireWithinLimit(unit, start, end);

        String key = "admin:metrics:growth:%s:%s:%s:%s".formatted(source, unit, start, end);
        return cached(key, AdminGrowthDTO.class, () -> computeGrowth(source, unit, start, end));
    }

    private AdminGrowthDTO computeGrowth(GrowthSource source, MetricGranularity unit, LocalDate from, LocalDate to) {
        Map<LocalDate, Long> counts = metricsRepository.countPerBucket(source, unit.sqlUnit(),
                AdminDates.startInclusive(from), AdminDates.endExclusive(to),
                ZoneId.systemDefault().getId(), Constants.ADMIN_ZONE.getId());

        List<AdminGrowthDTO.Bucket> buckets = new ArrayList<>();
        long total = 0;
        LocalDate cursor = bucketStart(from, unit);
        while (!cursor.isAfter(to)) {
            LocalDate next = nextBucketStart(cursor, unit);
            long count = counts.getOrDefault(cursor, 0L);
            buckets.add(new AdminGrowthDTO.Bucket(max(cursor, from), min(next.minusDays(1), to), count));
            total += count;
            cursor = next;
        }

        long days = ChronoUnit.DAYS.between(from, to) + 1;
        LocalDate previousTo = from.minusDays(1);
        LocalDate previousFrom = previousTo.minusDays(days - 1);
        long previousTotal = metricsRepository.countCreatedBetween(source,
                AdminDates.startInclusive(previousFrom), AdminDates.endExclusive(previousTo));

        return new AdminGrowthDTO(source.name().toLowerCase(), unit, from, to, Constants.ADMIN_ZONE.getId(), buckets, total,
                previousFrom, previousTo, previousTotal, changePercent(total, previousTotal));
    }

    private LocalDate[] resolveWindow(String range, LocalDate from, LocalDate to, LocalDate today) {
        boolean hasRange = range != null && !range.isBlank();
        if (hasRange) {
            if (from != null || to != null) {
                // a preset and explicit dates together would be a guess about which one wins
                throw new BusinessException(GlobalMessage.BAD_REQUEST, HttpStatus.BAD_REQUEST);
            }
            return preset(parseEnum(GrowthRange.class, range), today);
        }
        if (from != null || to != null) {
            if (from == null || to == null || from.isAfter(to)) {
                throw new BusinessException(GlobalMessage.BAD_REQUEST, HttpStatus.BAD_REQUEST);
            }
            return new LocalDate[]{from, to};
        }
        return preset(GrowthRange.LAST_30_DAYS, today);
    }

    private LocalDate[] preset(GrowthRange range, LocalDate today) {
        return switch (range) {
            case TODAY -> new LocalDate[]{today, today};
            case LAST_7_DAYS -> new LocalDate[]{today.minusDays(6), today};
            case LAST_30_DAYS -> new LocalDate[]{today.minusDays(29), today};
            case THIS_MONTH -> new LocalDate[]{today.withDayOfMonth(1), today};
            case LAST_MONTH -> {
                LocalDate firstOfThisMonth = today.withDayOfMonth(1);
                yield new LocalDate[]{firstOfThisMonth.minusMonths(1), firstOfThisMonth.minusDays(1)};
            }
            case THIS_YEAR -> new LocalDate[]{today.withDayOfYear(1), today};
        };
    }

    private void requireWithinLimit(MetricGranularity unit, LocalDate from, LocalDate to) {
        boolean tooLong = switch (unit) {
            case DAY -> ChronoUnit.DAYS.between(from, to) + 1 > MAX_DAYS_PER_DAY_RANGE;
            case WEEK -> from.plusYears(MAX_YEARS_PER_WEEK_RANGE).isBefore(to);
            case MONTH -> from.plusYears(MAX_YEARS_PER_MONTH_RANGE).isBefore(to);
        };
        if (tooLong) {
            throw new BusinessException(GlobalMessage.BAD_REQUEST, HttpStatus.BAD_REQUEST);
        }
    }

    private LocalDate bucketStart(LocalDate date, MetricGranularity unit) {
        return switch (unit) {
            case DAY -> date;
            case WEEK -> date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            case MONTH -> date.withDayOfMonth(1);
        };
    }

    private LocalDate nextBucketStart(LocalDate bucketStart, MetricGranularity unit) {
        return switch (unit) {
            case DAY -> bucketStart.plusDays(1);
            case WEEK -> bucketStart.plusWeeks(1);
            case MONTH -> bucketStart.plusMonths(1);
        };
    }

    private Double changePercent(long total, long previousTotal) {
        if (previousTotal == 0) {
            return null;
        }
        return Math.round((total - previousTotal) * 1000.0 / previousTotal) / 10.0;
    }

    private LocalDate max(LocalDate a, LocalDate b) {
        return a.isAfter(b) ? a : b;
    }

    private LocalDate min(LocalDate a, LocalDate b) {
        return a.isBefore(b) ? a : b;
    }

    // --------------------------------------------------------------------- cache

    private <T> T cached(String key, Class<T> type, Supplier<T> loader) {
        try {
            Object hit = redisUtil.get(key);
            if (hit != null) {
                return objectMapper.readValue(hit.toString(), type);
            }
        } catch (Exception e) {
            log.warn("ADMIN.METRICS.CACHE.READ.FAILED key={} reason={}", key, e.getMessage());
        }
        T value = loader.get();
        try {
            redisUtil.set(key, objectMapper.writeValueAsString(value), CACHE_TTL_SECONDS);
        } catch (Exception e) {
            log.warn("ADMIN.METRICS.CACHE.WRITE.FAILED key={} reason={}", key, e.getMessage());
        }
        return value;
    }

    private <E extends Enum<E>> E parseEnum(Class<E> type, String value) {
        try {
            return Enum.valueOf(type, value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(GlobalMessage.BAD_REQUEST, HttpStatus.BAD_REQUEST);
        }
    }
}
