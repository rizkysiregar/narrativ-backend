package com.rizkysiregar.narrativ.model.request;

import jakarta.validation.constraints.NotBlank;

public record CreateProfileImageUploadUrlRequest(
        @NotBlank(message = "{validation.contentType.required}")
        String contentType
) {
}
