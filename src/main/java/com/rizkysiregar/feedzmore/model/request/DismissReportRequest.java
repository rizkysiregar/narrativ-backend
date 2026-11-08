package com.rizkysiregar.feedzmore.model.request;

import jakarta.validation.constraints.Size;

public record DismissReportRequest(
        @Size(max = 500, message = "The length of the note should be at most 500")
        String note
) {}
