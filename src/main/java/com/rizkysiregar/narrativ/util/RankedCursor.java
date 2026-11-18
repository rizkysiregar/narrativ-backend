package com.rizkysiregar.narrativ.util;

import com.rizkysiregar.narrativ.constant.GlobalMessage;
import com.rizkysiregar.narrativ.exception.BusinessException;
import org.springframework.http.HttpStatus;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

/**
 * Where a page of a ranked feed ended: the ranking it was cut from (kept for a while, see
 * {@link com.rizkysiregar.narrativ.service.feed.RankedFeedService}), how far into it, and where the ranked posts
 * start in time. Once the ranking is used up or gone, the feed goes on by date from {@code windowStart}, so no post
 * of the ranking can come back. Opaque to clients, like {@link FeedCursor}, which it is told apart from by a prefix.
 */
public record RankedCursor(UUID snapshotId, int offset, LocalDateTime windowStart) {

    private static final String PREFIX = "r|";

    public String encode() {
        String raw = PREFIX + snapshotId + "|" + offset + "|" + windowStart;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    /** Empty for any cursor that is not a ranked one (a {@link FeedCursor}); a mangled ranked cursor answers 400. */
    public static Optional<RankedCursor> tryDecode(String cursor) {
        String raw;
        try {
            raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(GlobalMessage.BAD_REQUEST, HttpStatus.BAD_REQUEST);
        }
        if (!raw.startsWith(PREFIX)) {
            return Optional.empty();
        }
        try {
            String[] parts = raw.substring(PREFIX.length()).split("\\|", -1);
            int offset = Integer.parseInt(parts[1]);
            if (parts.length != 3 || offset < 0) {
                throw new IllegalArgumentException("bad ranked cursor");
            }
            return Optional.of(new RankedCursor(UUID.fromString(parts[0]), offset, LocalDateTime.parse(parts[2])));
        } catch (RuntimeException e) {
            throw new BusinessException(GlobalMessage.BAD_REQUEST, HttpStatus.BAD_REQUEST);
        }
    }

    /** The date cursor the feed goes on with after the ranking: strictly older than every ranked post. */
    public FeedCursor afterRanking() {
        return below(windowStart);
    }

    // the smallest id, so a post dated exactly windowStart (which was ranked) is not shown again
    public static FeedCursor below(LocalDateTime windowStart) {
        return new FeedCursor(windowStart, new UUID(0L, 0L));
    }
}
