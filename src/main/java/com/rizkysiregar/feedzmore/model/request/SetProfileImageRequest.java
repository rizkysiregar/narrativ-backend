package com.rizkysiregar.feedzmore.model.request;

import jakarta.validation.constraints.NotBlank;

public record SetProfileImageRequest(
        @NotBlank(message = "{validation.objectKey.required}")
        String objectKey
) {
}
