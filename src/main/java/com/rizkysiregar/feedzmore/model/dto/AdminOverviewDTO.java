package com.rizkysiregar.feedzmore.model.dto;

import java.time.Instant;

/**
 * Numbers for the top of the dashboard. "New" counts everything created in the period, whatever its status is now.
 * Cached for a minute, {@code generatedAt} says when it was computed.
 */
public record AdminOverviewDTO(
        Instant generatedAt,
        String timeZone,
        Users users,
        Posts posts,
        long comments,
        long openReports
) {
    /** active + banned + deleted = total: deleted is what the user deleted, banned and active only count the rest. */
    public record Users(long total, long active, long banned, long deleted,
                        long newToday, long newThisWeek, long newThisMonth) {}

    /**
     * active + takenDown + deleted = total: deleted is what the owner deleted, the other two only count the rest.
     * A post can hold several media: image and video count the posts with at least one of that type (a mixed post
     * is in both) and textOnly the posts without any media.
     */
    public record Posts(long total, long image, long video, long textOnly, long active, long takenDown, long deleted,
                        long newToday, long newThisWeek, long newThisMonth) {}
}
