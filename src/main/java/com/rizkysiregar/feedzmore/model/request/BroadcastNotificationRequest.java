package com.rizkysiregar.feedzmore.model.request;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

/**
 * Author: Habib Rizky Almajid Siregar
 * Date: 07/11/25
 * Description: Programmer
 */

@Setter
@Getter
@Data
public class BroadcastNotificationRequest {
    String title;
    String topic;
    String body;
}
