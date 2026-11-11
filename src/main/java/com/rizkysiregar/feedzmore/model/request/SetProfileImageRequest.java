package com.rizkysiregar.feedzmore.model.request;

import jakarta.validation.constraints.NotBlank;

public record SetProfileImageRequest(
        @NotBlank(message = "Object key is required")
        String objectKey
) {
}
