package com.rizkysiregar.narrativ.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class MarkNotificationReadRequest {
    @NotBlank
    @Size(max = 160)
    String groupKey;
}
