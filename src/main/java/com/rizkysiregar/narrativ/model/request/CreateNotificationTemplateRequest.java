package com.rizkysiregar.narrativ.model.request;

import com.rizkysiregar.narrativ.model.enums.TemplateCategory;
import com.rizkysiregar.narrativ.model.enums.TemplateMode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateNotificationTemplateRequest(
        // the stable identifier the code looks a template up by, so it cannot be changed afterwards
        @NotBlank(message = "{validation.code.required}")
        @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9_.-]{2,99}$",
                message = "{validation.code.pattern}")
        String code,

        @NotBlank(message = "{validation.title.required}")
        @Size(max = 100, message = "{validation.title.max100}")
        String title,

        @NotBlank(message = "{validation.body.required}")
        @Size(max = 255, message = "{validation.body.max255}")
        String body,

        @NotNull(message = "{validation.mode.required}")
        TemplateMode mode,

        @NotNull(message = "{validation.category.required}")
        TemplateCategory category,

        @Pattern(regexp = "^[a-zA-Z0-9\\-_.~%]{1,100}$", message = "{validation.topic.pattern}")
        String topic,

        // a new template is active unless said otherwise
        Boolean active
) {}
