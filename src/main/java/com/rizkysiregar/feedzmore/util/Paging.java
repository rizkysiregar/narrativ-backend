package com.rizkysiregar.feedzmore.util;

import com.rizkysiregar.feedzmore.constant.GlobalMessage;
import com.rizkysiregar.feedzmore.exception.BusinessException;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;

/**
 * The one place that turns the page and size of a request into a {@link PageRequest}. A page below 0 or a size
 * outside 1..max is the caller's mistake and answers 400: without a cap a single request could ask for
 * thousands of rows, and a negative page would blow up as a 500.
 */
public final class Paging {

    /** Largest page a client may ask for on the feed, comment and reply endpoints. */
    public static final int MAX_SIZE = 50;

    private Paging() {
    }

    public static PageRequest of(int page, int size) {
        return of(page, size, MAX_SIZE);
    }

    public static PageRequest of(int page, int size, int maxSize) {
        if (page < 0 || size < 1 || size > maxSize) {
            throw new BusinessException(GlobalMessage.BAD_REQUEST, HttpStatus.BAD_REQUEST);
        }
        return PageRequest.of(page, size);
    }
}
