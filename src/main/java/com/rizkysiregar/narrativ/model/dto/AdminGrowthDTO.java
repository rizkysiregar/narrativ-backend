package com.rizkysiregar.narrativ.model.dto;

import com.rizkysiregar.narrativ.model.enums.MetricGranularity;

import java.time.LocalDate;
import java.util.List;

/**
 * Created per day, week (Monday to Sunday) or month over a date range, in the admin time zone.
 * Every bucket of the range is there, with 0 when nothing was created. The first and last bucket are cut
 * at {@code from} and {@code to}, so a bucket never claims more days than its count covers.
 * {@code changePercent} compares {@code total} with the period of the same length right before the range,
 * and is null when that period had nothing to compare with.
 */
public record AdminGrowthDTO(
        String metric,
        MetricGranularity granularity,
        LocalDate from,
        LocalDate to,
        String timeZone,
        List<Bucket> buckets,
        long total,
        LocalDate previousFrom,
        LocalDate previousTo,
        long previousTotal,
        Double changePercent
) {
    public record Bucket(LocalDate periodStart, LocalDate periodEnd, long count) {}
}
