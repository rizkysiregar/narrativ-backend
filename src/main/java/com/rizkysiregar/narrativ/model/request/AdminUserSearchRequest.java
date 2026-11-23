package com.rizkysiregar.narrativ.model.request;

import java.time.LocalDate;

/**
 * Filters for the admin user list. {@code status} is ACTIVE, BANNED or DELETED (deleted by the user),
 * {@code createdFrom}/{@code createdTo} are inclusive calendar dates in the admin time zone, {@code seeded} true for
 * the accounts an admin created, false for the ones people signed up for themselves (null: both).
 */
public record AdminUserSearchRequest(
        String username,
        String email,
        String role,
        String status,
        LocalDate createdFrom,
        LocalDate createdTo,
        Boolean seeded
) {
    public AdminUserSearchRequest(String username, String email, String role, String status,
                                  LocalDate createdFrom, LocalDate createdTo) {
        this(username, email, role, status, createdFrom, createdTo, null);
    }
}
