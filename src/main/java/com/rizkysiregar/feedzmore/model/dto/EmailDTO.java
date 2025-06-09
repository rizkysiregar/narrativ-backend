package com.rizkysiregar.feedzmore.model.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class EmailDTO {
    @NotBlank
    @Email
    private String email;
}
