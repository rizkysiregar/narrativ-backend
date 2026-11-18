package com.rizkysiregar.narrativ.model.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class PaginatedResponse<T> {
    private List<T> content;
    private int pageNumber;
    private int pageSize;
    private long totalElements;
    private int totalPages;
    private boolean isLast;
    // feeds only: pass it back as ?cursor= for the next page; absent on the last page and on other lists
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String nextCursor;

    public PaginatedResponse(List<T> content, int pageNumber, int pageSize, long totalElements, int totalPages,
                             boolean isLast) {
        this(content, pageNumber, pageSize, totalElements, totalPages, isLast, null);
    }
}
