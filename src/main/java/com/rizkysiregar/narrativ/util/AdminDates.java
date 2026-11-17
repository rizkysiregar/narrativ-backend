package com.rizkysiregar.narrativ.util;

import com.rizkysiregar.narrativ.constant.Constants;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Turns the calendar dates an admin picks (in {@link Constants#ADMIN_ZONE}) into the naive
 * {@link LocalDateTime} boundaries used by columns that are written with the JVM default zone.
 */
public final class AdminDates {

    private AdminDates() {
    }

    public static LocalDateTime startInclusive(LocalDate date) {
        return date == null ? null : toServerTime(date);
    }

    public static LocalDateTime endExclusive(LocalDate date) {
        return date == null ? null : toServerTime(date.plusDays(1));
    }

    private static LocalDateTime toServerTime(LocalDate date) {
        return date.atStartOfDay(Constants.ADMIN_ZONE)
                .withZoneSameInstant(ZoneId.systemDefault())
                .toLocalDateTime();
    }
}
