package com.rizkysiregar.feedzmore.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.rizkysiregar.feedzmore.config.JacksonConfig;
import com.rizkysiregar.feedzmore.constant.GlobalMessage;
import com.rizkysiregar.feedzmore.exception.BusinessException;
import com.rizkysiregar.feedzmore.model.dto.AdminGrowthDTO;
import com.rizkysiregar.feedzmore.model.dto.AdminOverviewDTO;
import com.rizkysiregar.feedzmore.model.enums.GrowthSource;
import com.rizkysiregar.feedzmore.model.enums.ReportStatus;
import com.rizkysiregar.feedzmore.repository.AdminMetricsRepository;
import com.rizkysiregar.feedzmore.repository.CommentRepository;
import com.rizkysiregar.feedzmore.repository.ReportRepository;
import com.rizkysiregar.feedzmore.util.AdminDates;
import com.rizkysiregar.feedzmore.util.RedisUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminMetricsServiceTest {

    // 2026-11-08 is a Sunday, so its week started on Monday 2026-11-02
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 8);

    @Mock
    private AdminMetricsRepository metricsRepository;
    @Mock
    private CommentRepository commentRepository;
    @Mock
    private ReportRepository reportRepository;
    @Mock
    private RedisUtil redisUtil;

    private final ObjectMapper objectMapper = new JacksonConfig().objectMapper(new JavaTimeModule());
    private AdminMetricsService service;

    @BeforeEach
    void setUp() {
        service = new AdminMetricsService(metricsRepository, commentRepository, reportRepository, redisUtil, objectMapper);
    }

    private AdminGrowthDTO growth(GrowthSource source, String granularity, String range, LocalDate from, LocalDate to) {
        return service.growth(source, granularity, range, from, to, TODAY);
    }

    private void assertBadRequest(Runnable action) {
        BusinessException exception = assertThrows(BusinessException.class, action::run);
        assertEquals(GlobalMessage.BAD_REQUEST.getMessage(), exception.getMessage());
        assertEquals(HttpStatus.BAD_REQUEST, exception.getHttpStatus());
    }

    // ------------------------------------------------------------------ ranges

    @Test
    void presets_resolveAgainstTodayInTheAdminZone() {
        assertEquals(List.of(TODAY, TODAY), window(growth(GrowthSource.USERS, null, "TODAY", null, null)));
        assertEquals(List.of(LocalDate.of(2026, 11, 2), TODAY), window(growth(GrowthSource.USERS, null, "LAST_7_DAYS", null, null)));
        assertEquals(List.of(LocalDate.of(2026, 10, 10), TODAY), window(growth(GrowthSource.USERS, null, "last_30_days", null, null)));
        assertEquals(List.of(LocalDate.of(2026, 11, 1), TODAY), window(growth(GrowthSource.USERS, null, "THIS_MONTH", null, null)));
        assertEquals(List.of(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31)), window(growth(GrowthSource.USERS, null, "LAST_MONTH", null, null)));
        assertEquals(List.of(LocalDate.of(2026, 1, 1), TODAY), window(growth(GrowthSource.USERS, null, "THIS_YEAR", null, null)));
    }

    @Test
    void withNeitherRangeNorDates_itIsTheLast30DaysPerDay() {
        AdminGrowthDTO result = growth(GrowthSource.POSTS, null, null, null, null);

        assertEquals(List.of(LocalDate.of(2026, 10, 10), TODAY), window(result));
        assertEquals("DAY", result.granularity().name());
        assertEquals(30, result.buckets().size());
        assertEquals("posts", result.metric());
        assertEquals("Asia/Jakarta", result.timeZone());
    }

    @Test
    void customDates_areTakenAsTheyAre() {
        AdminGrowthDTO result = growth(GrowthSource.USERS, "DAY", null, LocalDate.of(2026, 11, 5), LocalDate.of(2026, 11, 8));

        assertEquals(List.of(LocalDate.of(2026, 11, 5), TODAY), window(result));
    }

    private List<LocalDate> window(AdminGrowthDTO dto) {
        return List.of(dto.from(), dto.to());
    }

    @Test
    void ambiguousOrBrokenInput_isABadRequestAndNothingIsQueried() {
        assertBadRequest(() -> growth(GrowthSource.USERS, null, "LAST_7_DAYS", LocalDate.of(2026, 11, 1), null)); // range + date
        assertBadRequest(() -> growth(GrowthSource.USERS, null, null, LocalDate.of(2026, 11, 1), null));          // from only
        assertBadRequest(() -> growth(GrowthSource.USERS, null, null, null, TODAY));                              // to only
        assertBadRequest(() -> growth(GrowthSource.USERS, null, null, TODAY, LocalDate.of(2026, 11, 1)));         // from after to
        assertBadRequest(() -> growth(GrowthSource.USERS, null, "FOREVER", null, null));
        assertBadRequest(() -> growth(GrowthSource.USERS, "HOUR", null, null, null));

        verifyNoInteractions(metricsRepository, redisUtil);
    }

    @Test
    void limits_perGranularity() {
        // DAY: 366 days is the longest
        growth(GrowthSource.USERS, "DAY", null, LocalDate.of(2025, 11, 8), TODAY);
        assertBadRequest(() -> growth(GrowthSource.USERS, "DAY", null, LocalDate.of(2025, 11, 7), TODAY));
        // WEEK: 2 years
        growth(GrowthSource.USERS, "WEEK", null, LocalDate.of(2024, 11, 8), TODAY);
        assertBadRequest(() -> growth(GrowthSource.USERS, "WEEK", null, LocalDate.of(2024, 11, 7), TODAY));
        // MONTH: 5 years
        growth(GrowthSource.USERS, "MONTH", null, LocalDate.of(2021, 11, 8), TODAY);
        assertBadRequest(() -> growth(GrowthSource.USERS, "MONTH", null, LocalDate.of(2021, 11, 7), TODAY));
    }

    // ----------------------------------------------------------------- buckets

    @Test
    void days_everyDayIsThereAndEmptyOnesAreZero() {
        LocalDate from = LocalDate.of(2026, 11, 5);
        when(metricsRepository.countPerBucket(eq(GrowthSource.USERS), eq("day"), any(), any(), anyString(), eq("Asia/Jakarta")))
                .thenReturn(Map.of(LocalDate.of(2026, 11, 6), 2L, LocalDate.of(2026, 11, 8), 1L));
        when(metricsRepository.countCreatedBetween(eq(GrowthSource.USERS), any(), any())).thenReturn(2L);

        AdminGrowthDTO result = growth(GrowthSource.USERS, "DAY", null, from, TODAY);

        assertEquals(List.of(0L, 2L, 0L, 1L), result.buckets().stream().map(AdminGrowthDTO.Bucket::count).toList());
        assertEquals(LocalDate.of(2026, 11, 5), result.buckets().get(0).periodStart());
        assertEquals(LocalDate.of(2026, 11, 5), result.buckets().get(0).periodEnd());
        assertEquals(3, result.total());
    }

    @Test
    void weeks_runMondayToSundayAndTheEdgeBucketsAreCutAtTheRange() {
        when(metricsRepository.countPerBucket(eq(GrowthSource.USERS), eq("week"), any(), any(), anyString(), anyString()))
                .thenReturn(Map.of(LocalDate.of(2026, 10, 26), 3L, LocalDate.of(2026, 11, 2), 5L));

        // Wednesday 2026-10-28 until Sunday 2026-11-08
        AdminGrowthDTO result = growth(GrowthSource.USERS, "WEEK", null, LocalDate.of(2026, 10, 28), TODAY);

        assertEquals(2, result.buckets().size());
        assertEquals(new AdminGrowthDTO.Bucket(LocalDate.of(2026, 10, 28), LocalDate.of(2026, 11, 1), 3), result.buckets().get(0));
        assertEquals(new AdminGrowthDTO.Bucket(LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 8), 5), result.buckets().get(1));
        assertEquals(8, result.total());
    }

    @Test
    void months_areCalendarMonthsAndTheEdgeBucketsAreCutAtTheRange() {
        when(metricsRepository.countPerBucket(eq(GrowthSource.POSTS), eq("month"), any(), any(), anyString(), anyString()))
                .thenReturn(Map.of(LocalDate.of(2026, 9, 1), 4L, LocalDate.of(2026, 11, 1), 7L));

        AdminGrowthDTO result = growth(GrowthSource.POSTS, "MONTH", null, LocalDate.of(2026, 9, 15), TODAY);

        assertEquals(List.of(
                new AdminGrowthDTO.Bucket(LocalDate.of(2026, 9, 15), LocalDate.of(2026, 9, 30), 4),
                new AdminGrowthDTO.Bucket(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), 0),
                new AdminGrowthDTO.Bucket(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 8), 7)), result.buckets());
        assertEquals(11, result.total());
    }

    @Test
    void theQueryGetsTheServerTimeBoundariesAndBothZones() {
        LocalDate from = LocalDate.of(2026, 11, 2);

        growth(GrowthSource.USERS, "WEEK", null, from, TODAY);

        ArgumentCaptor<LocalDateTime> start = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> endExclusive = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(metricsRepository).countPerBucket(eq(GrowthSource.USERS), eq("week"), start.capture(), endExclusive.capture(),
                eq(ZoneId.systemDefault().getId()), eq("Asia/Jakarta"));
        assertEquals(AdminDates.startInclusive(from), start.getValue());
        assertEquals(AdminDates.endExclusive(TODAY), endExclusive.getValue());
    }

    // -------------------------------------------------------------- comparison

    @Test
    void comparesWithThePeriodOfTheSameLengthRightBefore() {
        when(metricsRepository.countPerBucket(any(), anyString(), any(), any(), anyString(), anyString()))
                .thenReturn(Map.of(LocalDate.of(2026, 11, 6), 6L));
        when(metricsRepository.countCreatedBetween(eq(GrowthSource.USERS),
                eq(AdminDates.startInclusive(LocalDate.of(2026, 11, 1))), eq(AdminDates.endExclusive(LocalDate.of(2026, 11, 4)))))
                .thenReturn(4L);

        AdminGrowthDTO result = growth(GrowthSource.USERS, "DAY", null, LocalDate.of(2026, 11, 5), TODAY);

        assertEquals(LocalDate.of(2026, 11, 1), result.previousFrom());
        assertEquals(LocalDate.of(2026, 11, 4), result.previousTo());
        assertEquals(4, result.previousTotal());
        assertEquals(50.0, result.changePercent());
    }

    @Test
    void aDropIsNegativeAndNothingToCompareWithIsNull() {
        when(metricsRepository.countPerBucket(any(), anyString(), any(), any(), anyString(), anyString()))
                .thenReturn(Map.of(LocalDate.of(2026, 11, 6), 1L));
        when(metricsRepository.countCreatedBetween(eq(GrowthSource.USERS), any(), any())).thenReturn(3L);
        assertEquals(-66.7, growth(GrowthSource.USERS, "DAY", null, LocalDate.of(2026, 11, 5), TODAY).changePercent());

        when(metricsRepository.countCreatedBetween(eq(GrowthSource.POSTS), any(), any())).thenReturn(0L);
        assertNull(growth(GrowthSource.POSTS, "DAY", null, LocalDate.of(2026, 11, 5), TODAY).changePercent());
    }

    // ------------------------------------------------------------------- cache

    @Test
    void aCachedAnswerIsReturnedWithoutTouchingTheDatabase() throws Exception {
        AdminGrowthDTO cached = new AdminGrowthDTO("users", com.rizkysiregar.feedzmore.model.enums.MetricGranularity.DAY,
                LocalDate.of(2026, 11, 5), TODAY, "Asia/Jakarta",
                List.of(new AdminGrowthDTO.Bucket(LocalDate.of(2026, 11, 5), LocalDate.of(2026, 11, 5), 9)), 9,
                LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 4), 3, 200.0);
        when(redisUtil.get("admin:metrics:growth:USERS:DAY:2026-11-05:2026-11-08")).thenReturn(objectMapper.writeValueAsString(cached));

        AdminGrowthDTO result = growth(GrowthSource.USERS, "DAY", null, LocalDate.of(2026, 11, 5), TODAY);

        assertEquals(cached, result);
        verifyNoInteractions(metricsRepository);
        verify(redisUtil, never()).set(anyString(), any(), anyLong());
    }

    @Test
    void aMissIsComputedAndStoredForAMinute() {
        growth(GrowthSource.USERS, "DAY", null, LocalDate.of(2026, 11, 5), TODAY);

        verify(redisUtil).set(eq("admin:metrics:growth:USERS:DAY:2026-11-05:2026-11-08"), any(String.class), eq(60L));
    }

    @Test
    void redisBeingDownNeverBreaksTheDashboard() {
        when(redisUtil.get(anyString())).thenThrow(new RuntimeException("redis down"));
        org.mockito.Mockito.doThrow(new RuntimeException("redis down")).when(redisUtil).set(anyString(), any(), anyLong());
        when(metricsRepository.countPerBucket(any(), anyString(), any(), any(), anyString(), anyString()))
                .thenReturn(Map.of(TODAY, 2L));

        AdminGrowthDTO result = growth(GrowthSource.USERS, "DAY", "TODAY", null, null);

        assertEquals(2, result.total());
    }

    // ---------------------------------------------------------------- overview

    @Test
    void overview_mapsTheCountsAndUsesTheBoundariesOfTodayThisWeekAndThisMonth() {
        LocalDateTime todayStart = AdminDates.startInclusive(TODAY);
        LocalDateTime weekStart = AdminDates.startInclusive(LocalDate.of(2026, 11, 2));
        LocalDateTime monthStart = AdminDates.startInclusive(LocalDate.of(2026, 11, 1));
        when(metricsRepository.userCounts(todayStart, weekStart, monthStart))
                .thenReturn(new AdminMetricsRepository.UserCounts(10, 7, 2, 1, 1, 3, 5));
        when(metricsRepository.postCounts(todayStart, weekStart, monthStart))
                .thenReturn(new AdminMetricsRepository.PostCounts(20, 12, 8, 15, 3, 2, 2, 6, 9));
        when(commentRepository.count()).thenReturn(42L);
        when(reportRepository.countByStatus(ReportStatus.OPEN)).thenReturn(4L);
        Instant now = Instant.parse("2026-11-08T03:00:00Z");

        AdminOverviewDTO result = service.overview(TODAY, now);

        assertEquals(now, result.generatedAt());
        assertEquals(new AdminOverviewDTO.Users(10, 7, 2, 1, 1, 3, 5), result.users());
        assertEquals(new AdminOverviewDTO.Posts(20, 12, 8, 15, 3, 2, 2, 6, 9), result.posts());
        assertEquals(42, result.comments());
        assertEquals(4, result.openReports());
        verify(redisUtil).set(eq("admin:metrics:overview:2026-11-08"), any(String.class), eq(60L));
    }

    @Test
    void overview_cameFromTheCacheWhenItIsThere() throws Exception {
        AdminOverviewDTO cached = new AdminOverviewDTO(Instant.parse("2026-11-08T03:00:00Z"), "Asia/Jakarta",
                new AdminOverviewDTO.Users(1, 1, 0, 0, 0, 0, 0), new AdminOverviewDTO.Posts(0, 0, 0, 0, 0, 0, 0, 0, 0), 0, 0);
        when(redisUtil.get("admin:metrics:overview:2026-11-08")).thenReturn(objectMapper.writeValueAsString(cached));

        assertEquals(cached, service.overview(TODAY, Instant.now()));

        verifyNoInteractions(metricsRepository, commentRepository, reportRepository);
    }
}
