package com.rizkysiregar.feedzmore.model.request;

import com.rizkysiregar.feedzmore.model.enums.ReportAction;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ResolveReportRequest(
        @NotNull(message = "Action is required")
        ReportAction action,

        // required (5-500) for every action except NONE, and then used as the reason of the action itself
        @Size(max = 500, message = "The length of the note should be at most 500")
        String note
) {}
