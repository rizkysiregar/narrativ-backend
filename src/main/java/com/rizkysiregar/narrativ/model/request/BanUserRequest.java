package com.rizkysiregar.narrativ.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BanUserRequest(
        @NotBlank(message = "{validation.reason.required}")
        @Size(min = 5, max = 500, message = "{validation.reason.size}")
        String reason
) {}
