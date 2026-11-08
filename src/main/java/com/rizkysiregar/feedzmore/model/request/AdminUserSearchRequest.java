package com.rizkysiregar.feedzmore.model.request;

import java.time.LocalDate;

/**
 * Filters for the admin user list. {@code status} is ACTIVE, BANNED or DELETED (deleted by the user),
 * {@code createdFrom}/{@code createdTo} are inclusive calendar dates in the admin time zone.
 */
public record AdminUserSearchRequest(
        String username,
        String email,
        String role,
        String status,
        LocalDate createdFrom,
        LocalDate createdTo
) {}
