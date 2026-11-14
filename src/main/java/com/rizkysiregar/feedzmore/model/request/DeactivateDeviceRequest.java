package com.rizkysiregar.feedzmore.model.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.ToString;

@Data
public class DeactivateDeviceRequest {
    @NotBlank
    @ToString.Exclude
    private String pushToken;
}
