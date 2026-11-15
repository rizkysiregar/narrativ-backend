package com.rizkysiregar.feedzmore.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateParameterRequest(
        // the key is what the code looks a parameter up by, so it cannot be changed afterwards
        @NotBlank(message = "{validation.key.required}")
        @Pattern(regexp = "^[a-z][a-z0-9_.-]{2,99}$",
                message = "{validation.key.pattern}")
        String paramKey,

        @NotBlank(message = "{validation.value.required}")
        @Size(max = 255, message = "{validation.value.max255}")
        String paramValue,

        @NotBlank(message = "{validation.description.required}")
        @Size(max = 255, message = "{validation.description.max255}")
        String description
) {}
