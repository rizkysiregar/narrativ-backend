package com.rizkysiregar.narrativ.util;

import com.rizkysiregar.narrativ.constant.GlobalMessage;
import com.rizkysiregar.narrativ.exception.BusinessException;
import org.springframework.http.HttpStatus;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Where an inbox page ended: the time of its last entry (in microseconds, the precision of the column) and that
 * entry's group key, the pair the inbox is ordered by. Clients treat the value as opaque.
 */
public record InboxCursor(long beforeMicros, String beforeKey) {

    /** Before everything: the first page. */
    public static final InboxCursor FIRST = new InboxCursor(Long.MAX_VALUE, "");

    public String encode() {
        String raw = beforeMicros + "|" + beforeKey;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    /** No cursor is the first page; one the client made up or mangled answers 400. */
    public static InboxCursor decode(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return FIRST;
        }
        try {
            String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            int separator = raw.indexOf('|');
            return new InboxCursor(Long.parseLong(raw.substring(0, separator)), raw.substring(separator + 1));
        } catch (RuntimeException e) {
            throw new BusinessException(GlobalMessage.BAD_REQUEST, HttpStatus.BAD_REQUEST);
        }
    }
}
