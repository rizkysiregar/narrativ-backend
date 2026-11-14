package com.rizkysiregar.feedzmore.controller;

import com.rizkysiregar.feedzmore.model.dto.PublicUserDTO;
import com.rizkysiregar.feedzmore.model.request.UserSearchRequest;
import com.rizkysiregar.feedzmore.model.response.PaginatedResponse;
import com.rizkysiregar.feedzmore.service.AuthService;
import io.swagger.annotations.ApiOperation;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import com.rizkysiregar.feedzmore.model.UserPrincipal;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@AllArgsConstructor
@RequestMapping("/users")
public class UserSearchController {

    final AuthService authService;

    @Operation(summary = "Get all active users or filter active users by username")
    @GetMapping()
    public PaginatedResponse<PublicUserDTO> searchUsers(
            @RequestParam(required = false) String username,
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal currentUser,
            @Parameter(hidden = true) Pageable pageable
    ) {
        return authService.search(
                new UserSearchRequest(username),
                currentUser.getUserId(),
                pageable
        );
    }

    @Operation(summary = "Get active user details by userId")
    @GetMapping("{userId}")
    public PublicUserDTO getUserById(@PathVariable @NotBlank String userId,
                                     @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal currentUser) {
        return authService.getUserById(userId, currentUser.getUserId());
    }

}
