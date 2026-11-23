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
 * Where a page of the home feed ended. The feed mixes two sources, so it carries both: the date cursor of the
 * followed posts ({@code following}, null once they ran out), how far the suggestions got ({@link Suggestions}), and
 * the {@code slot} in the current round of followed posts and suggestions (see PostServiceImpl#homePage). Opaque to
 * clients like the other cursors, told apart from them by a prefix.
 * <p>
 * The cursors the home feed answered before (a {@link FeedCursor} while in the followed posts, a {@link RankedCursor}
 * once in the suggestions) are still read, so somebody scrolling through a deploy goes on where they were.
 */
public record HomeCursor(FeedCursor following, Suggestions suggestions, int slot) {

    private static final String PREFIX = "h|";
    private static final String NOT_STARTED = "-";
    private static final String DONE = "x";

    /** The first page: the followed posts from the newest, no suggestions yet. */
    public static HomeCursor start() {
        return new HomeCursor(new FeedCursor(LocalDateTime.now().plusDays(1), new UUID(-1L, -1L)), Suggestions.NOT_STARTED, 0);
    }

    /**
     * The suggestions of a home feed: not ranked yet ({@code ranking} null, not done), going on from a ranking, or
     * used up ({@code done}).
     */
    public record Suggestions(RankedCursor ranking, boolean done) {
        public static final Suggestions NOT_STARTED = new Suggestions(null, false);
        public static final Suggestions DONE = new Suggestions(null, true);

        public static Suggestions at(RankedCursor ranking) {
            return ranking == null ? DONE : new Suggestions(ranking, false);
        }
    }

    public String encode() {
        String followingPart = following == null ? DONE : following.createdAt() + "," + following.id();
        String suggestionsPart = suggestions.done() ? DONE
                : suggestions.ranking() == null ? NOT_STARTED
                : suggestions.ranking().snapshotId() + "," + suggestions.ranking().offset() + "," + suggestions.ranking().windowStart();
        String raw = PREFIX + followingPart + "|" + suggestionsPart + "|" + slot;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    /** Reads a home cursor, or one of the cursors the home feed answered before; anything mangled answers 400. */
    public static HomeCursor decode(String cursor) {
        String raw;
        try {
            raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw badRequest();
        }
        if (!raw.startsWith(PREFIX)) {
            // in the suggestions already: the followed posts were used up
            Optional<RankedCursor> ranked = RankedCursor.tryDecode(cursor);
            if (ranked.isPresent()) {
                return new HomeCursor(null, Suggestions.at(ranked.get()), 0);
            }
            return new HomeCursor(FeedCursor.decode(cursor), Suggestions.NOT_STARTED, 0);
        }
        try {
            String[] parts = raw.substring(PREFIX.length()).split("\\|", -1);
            if (parts.length != 3) {
                throw new IllegalArgumentException("bad home cursor");
            }
            int slot = Integer.parseInt(parts[2]);
            if (slot < 0) {
                throw new IllegalArgumentException("bad slot");
            }
            return new HomeCursor(parseFollowing(parts[0]), parseSuggestions(parts[1]), slot);
        } catch (RuntimeException e) {
            throw badRequest();
        }
    }

    private static FeedCursor parseFollowing(String part) {
        if (part.equals(DONE)) {
            return null;
        }
        String[] fields = part.split(",", -1);
        if (fields.length != 2) {
            throw new IllegalArgumentException("bad following part");
        }
        return new FeedCursor(LocalDateTime.parse(fields[0]), UUID.fromString(fields[1]));
    }

    private static Suggestions parseSuggestions(String part) {
        if (part.equals(DONE)) {
            return Suggestions.DONE;
        }
        if (part.equals(NOT_STARTED)) {
            return Suggestions.NOT_STARTED;
        }
        String[] fields = part.split(",", -1);
        if (fields.length != 3) {
            throw new IllegalArgumentException("bad suggestions part");
        }
        int offset = Integer.parseInt(fields[1]);
        if (offset < 0) {
            throw new IllegalArgumentException("bad suggestions offset");
        }
        return Suggestions.at(new RankedCursor(UUID.fromString(fields[0]), offset, LocalDateTime.parse(fields[2])));
    }

    private static BusinessException badRequest() {
        return new BusinessException(GlobalMessage.BAD_REQUEST, HttpStatus.BAD_REQUEST);
    }
}
