package com.rizkysiregar.feedzmore.model.request;

import com.rizkysiregar.feedzmore.model.enums.ReportReason;
import com.rizkysiregar.feedzmore.model.enums.ReportTargetType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateReportRequest(
        @NotNull(message = "Target type is required")
        ReportTargetType targetType,

        @NotBlank(message = "Target id is required")
        String targetId,

        @NotNull(message = "Reason is required")
        ReportReason reason,

        // required when the reason is OTHER, checked in the service
        @Size(max = 500, message = "The length of the description should be at most 500")
        String description
) {}
