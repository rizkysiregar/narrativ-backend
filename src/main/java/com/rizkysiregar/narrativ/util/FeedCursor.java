package com.rizkysiregar.narrativ.util;

import com.rizkysiregar.narrativ.constant.GlobalMessage;
import com.rizkysiregar.narrativ.exception.BusinessException;
import org.springframework.http.HttpStatus;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.UUID;

/**
 * Where a feed page ended: the created_at and id of its last post, the same pair the feeds are ordered by. The next
 * page is everything strictly after it, so a post added at the top while somebody scrolls never shifts the pages
 * (no post shown twice or skipped) and the database seeks to it instead of counting and skipping rows.
 * Clients treat the value as opaque.
 */
public record FeedCursor(LocalDateTime createdAt, UUID id) {

    public String encode() {
        String raw = createdAt + "|" + id;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    /** A cursor the client made up or mangled answers 400, like any other bad paging input. */
    public static FeedCursor decode(String cursor) {
        try {
            String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            int separator = raw.indexOf('|');
            return new FeedCursor(LocalDateTime.parse(raw.substring(0, separator)), UUID.fromString(raw.substring(separator + 1)));
        } catch (RuntimeException e) {
            throw new BusinessException(GlobalMessage.BAD_REQUEST, HttpStatus.BAD_REQUEST);
        }
    }
}
