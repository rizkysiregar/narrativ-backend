package com.rizkysiregar.feedzmore.model.request;

import jakarta.validation.constraints.Size;

public record UnbanUserRequest(
        @Size(max = 500, message = "The length of the reason should be at most 500")
        String reason
) {}
