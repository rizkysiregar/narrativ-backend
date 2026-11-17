package com.rizkysiregar.narrativ.model.request;

import java.time.LocalDate;

/**
 * Filters for the admin report queue. {@code createdFrom}/{@code createdTo} are inclusive calendar dates
 * in the admin time zone.
 */
public record AdminReportSearchRequest(
        String status,
        String targetType,
        String reason,
        String reporterId,
        String targetId,
        LocalDate createdFrom,
        LocalDate createdTo
) {}
