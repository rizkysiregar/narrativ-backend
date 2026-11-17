package com.rizkysiregar.narrativ.model.request;

import jakarta.validation.constraints.Size;

public record UnbanUserRequest(
        @Size(max = 500, message = "{validation.reason.max500}")
        String reason
) {}
