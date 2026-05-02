package com.rizkysiregar.feedzmore.model.dto;


import com.rizkysiregar.feedzmore.model.enums.Role;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UserLoginDTO {
    private String token;
    private String username;
    private String userId;
    private Role role;
}
