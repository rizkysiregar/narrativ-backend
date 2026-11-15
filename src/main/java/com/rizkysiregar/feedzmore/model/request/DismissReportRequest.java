package com.rizkysiregar.feedzmore.model.request;

import jakarta.validation.constraints.Size;

public record DismissReportRequest(
        @Size(max = 500, message = "{validation.note.max500}")
        String note
) {}
