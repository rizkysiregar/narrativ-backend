package com.rizkysiregar.feedzmore.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BanUserRequest(
        @NotBlank(message = "Reason is required")
        @Size(min = 5, max = 500, message = "The length of the reason should be between 5 and 500")
        String reason
) {}
