package com.rizkysiregar.narrativ.model.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.ToString;

@Data
public class RegisterDeviceRequest {
    @NotBlank
    @ToString.Exclude
    private String pushToken;

    @NotBlank
    private String platform;

    private String deviceId;
    private String appVersion;
    private String osVersion;
    private String deviceModel;
    private String locale;
    private String timezone;
}
