package com.rizkysiregar.narrativ.model.response;


import com.rizkysiregar.narrativ.model.enums.Role;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LoginResponse {
    private String token;
    private String refreshToken;
    private String username;
    private String userId;
    private Role role;
}
