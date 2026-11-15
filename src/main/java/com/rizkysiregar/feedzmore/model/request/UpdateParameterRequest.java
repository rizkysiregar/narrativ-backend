package com.rizkysiregar.feedzmore.model.request;

import jakarta.validation.constraints.Size;

/** Only what is given is changed; at least one of them is needed. */
public record UpdateParameterRequest(
        @Size(min = 1, max = 255, message = "{validation.value.size}")
        String paramValue,

        @Size(min = 1, max = 255, message = "{validation.description.size}")
        String description
) {}
