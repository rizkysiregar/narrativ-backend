package com.rizkysiregar.feedzmore.model.request;

import java.time.LocalDate;

/**
 * Filters for the admin post list. {@code mediaType} keeps posts with at least one media of that type and
 * {@code hasMedia} false selects the caption-only ones. {@code deleted} true/false selects posts the owner deleted / did not delete,
 * {@code createdFrom}/{@code createdTo} are inclusive calendar dates in the admin time zone.
 */
public record AdminPostSearchRequest(
        String authorId,
        String mediaType,
        Boolean hasMedia,
        String moderationStatus,
        Boolean deleted,
        String keyword,
        LocalDate createdFrom,
        LocalDate createdTo
) {}
