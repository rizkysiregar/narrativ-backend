package com.rizkysiregar.narrativ.model.request;

import com.rizkysiregar.narrativ.model.enums.ReportReason;
import com.rizkysiregar.narrativ.model.enums.ReportTargetType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateReportRequest(
        @NotNull(message = "{validation.targetType.required}")
        ReportTargetType targetType,

        @NotBlank(message = "{validation.targetId.required}")
        String targetId,

        @NotNull(message = "{validation.reason.required}")
        ReportReason reason,

        // required when the reason is OTHER, checked in the service
        @Size(max = 500, message = "{validation.description.max500}")
        String description
) {}
