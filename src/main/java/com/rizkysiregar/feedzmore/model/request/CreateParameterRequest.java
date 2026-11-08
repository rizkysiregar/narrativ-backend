package com.rizkysiregar.feedzmore.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateParameterRequest(
        // the key is what the code looks a parameter up by, so it cannot be changed afterwards
        @NotBlank(message = "Key is required")
        @Pattern(regexp = "^[a-z][a-z0-9_.-]{2,99}$",
                message = "Key must be 3-100 characters: lower case letters, digits, '.', '_' or '-', starting with a letter")
        String paramKey,

        @NotBlank(message = "Value is required")
        @Size(max = 255, message = "The length of the value should be at most 255")
        String paramValue,

        @NotBlank(message = "Description is required")
        @Size(max = 255, message = "The length of the description should be at most 255")
        String description
) {}
