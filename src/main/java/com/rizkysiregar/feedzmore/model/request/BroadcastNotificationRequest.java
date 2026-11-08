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

    @NotBlank(message = "Title is required")
    @Size(max = 100, message = "The length of the title should be at most 100")
    String title;

    // an FCM topic name; "global" reaches everybody
    @NotBlank(message = "Topic is required")
    @Pattern(regexp = "^[a-zA-Z0-9\\-_.~%]{1,100}$", message = "Topic may only contain letters, digits and - _ . ~ %")
    String topic;

    @NotBlank(message = "Body is required")
    @Size(max = 500, message = "The length of the body should be at most 500")
    String body;
}
