package com.rizkysiregar.feedzmore.model.request;

import com.rizkysiregar.feedzmore.model.enums.TemplateCategory;
import com.rizkysiregar.feedzmore.model.enums.TemplateMode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateNotificationTemplateRequest(
        // the stable identifier the code looks a template up by, so it cannot be changed afterwards
        @NotBlank(message = "Code is required")
        @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9_.-]{2,99}$",
                message = "Code must be 3-100 characters: letters, digits, '.', '_' or '-'")
        String code,

        @NotBlank(message = "Title is required")
        @Size(max = 100, message = "The length of the title should be at most 100")
        String title,

        @NotBlank(message = "Body is required")
        @Size(max = 255, message = "The length of the body should be at most 255")
        String body,

        @NotNull(message = "Mode is required")
        TemplateMode mode,

        @NotNull(message = "Category is required")
        TemplateCategory category,

        @Pattern(regexp = "^[a-zA-Z0-9\\-_.~%]{1,100}$", message = "Topic may only contain letters, digits and - _ . ~ %")
        String topic,

        // a new template is active unless said otherwise
        Boolean active
) {}
