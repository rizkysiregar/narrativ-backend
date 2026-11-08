package com.rizkysiregar.feedzmore.model.request;

import jakarta.validation.constraints.Size;

/** Only what is given is changed; at least one of them is needed. */
public record UpdateParameterRequest(
        @Size(min = 1, max = 255, message = "The length of the value should be between 1 and 255")
        String paramValue,

        @Size(min = 1, max = 255, message = "The length of the description should be between 1 and 255")
        String description
) {}
