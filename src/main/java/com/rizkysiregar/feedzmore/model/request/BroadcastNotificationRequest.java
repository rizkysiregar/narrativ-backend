package com.rizkysiregar.feedzmore.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Author: Habib Rizky Almajid Siregar
 * Date: 07/11/25
 * Description: Programmer
 */

@Data
public class BroadcastNotificationRequest {

    @NotBlank(message = "{validation.title.required}")
    @Size(max = 100, message = "{validation.title.max100}")
    String title;

    // an FCM topic name; "global" reaches everybody
    @NotBlank(message = "{validation.topic.required}")
    @Pattern(regexp = "^[a-zA-Z0-9\\-_.~%]{1,100}$", message = "{validation.topic.pattern}")
    String topic;

    @NotBlank(message = "{validation.body.required}")
    @Size(max = 500, message = "{validation.body.max500}")
    String body;
}
