package com.rizkysiregar.feedzmore.model.request;

import com.rizkysiregar.feedzmore.model.enums.ReportAction;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ResolveReportRequest(
        @NotNull(message = "{validation.action.required}")
        ReportAction action,

        // required (5-500) for every action except NONE, and then used as the reason of the action itself
        @Size(max = 500, message = "{validation.note.max500}")
        String note
) {}
