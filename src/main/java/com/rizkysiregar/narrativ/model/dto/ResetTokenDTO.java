package com.rizkysiregar.narrativ.model.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ResetTokenDTO {
    String resetToken;
}
