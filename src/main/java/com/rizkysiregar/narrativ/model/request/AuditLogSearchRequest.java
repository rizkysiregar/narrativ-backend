package com.rizkysiregar.narrativ.model.request;

import java.time.LocalDate;

/**
 * Filters for the admin audit log. {@code from} and {@code to} are inclusive calendar dates in the admin time zone.
 */
public record AuditLogSearchRequest(
        String adminId,
        String action,
        String targetType,
        String targetId,
        LocalDate from,
        LocalDate to
) {}
