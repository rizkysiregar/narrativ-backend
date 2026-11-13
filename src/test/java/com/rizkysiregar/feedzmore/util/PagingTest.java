package com.rizkysiregar.feedzmore.util;

import com.rizkysiregar.feedzmore.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PagingTest {

    @Test
    void acceptsTheFirstPageAndTheBoundariesOfTheSize() {
        assertEquals(PageRequest.of(0, 1), Paging.of(0, 1));
        assertEquals(PageRequest.of(7, 10), Paging.of(7, 10));
        assertEquals(PageRequest.of(0, Paging.MAX_SIZE), Paging.of(0, Paging.MAX_SIZE));
    }

    @Test
    void rejectsANegativePage() {
        assertBadRequest(-1, 10);
    }

    @Test
    void rejectsASizeBelowOneOrAboveTheMaximum() {
        assertBadRequest(0, 0);
        assertBadRequest(0, -5);
        assertBadRequest(0, Paging.MAX_SIZE + 1);
        assertBadRequest(0, Integer.MAX_VALUE);
    }

    @Test
    void aWiderMaximumCanBeGivenPerEndpoint() {
        assertEquals(PageRequest.of(0, 100), Paging.of(0, 100, 100));
        assertThrows(BusinessException.class, () -> Paging.of(0, 101, 100));
    }

    private void assertBadRequest(int page, int size) {
        BusinessException error = assertThrows(BusinessException.class, () -> Paging.of(page, size));
        assertEquals(HttpStatus.BAD_REQUEST, error.getHttpStatus());
    }
}
