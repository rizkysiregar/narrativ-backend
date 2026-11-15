package com.rizkysiregar.feedzmore.model.request;

import com.rizkysiregar.feedzmore.model.enums.TemplateCategory;
import com.rizkysiregar.feedzmore.model.enums.TemplateMode;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Only what is given is changed; at least one of them is needed. The code cannot change. */
public record UpdateNotificationTemplateRequest(
        @Size(min = 1, max = 100, message = "{validation.title.size}")
        String title,

        @Size(min = 1, max = 255, message = "{validation.body.size}")
        String body,

        TemplateMode mode,

        TemplateCategory category,

        @Pattern(regexp = "^[a-zA-Z0-9\\-_.~%]{1,100}$", message = "{validation.topic.pattern}")
        String topic,

        Boolean active
) {}
