package com.rizkysiregar.feedzmore.model.dto;


import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UserLoginDTO {
    String token;
    String username;
}
