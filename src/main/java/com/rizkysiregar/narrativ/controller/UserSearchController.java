package com.rizkysiregar.narrativ.controller;

import com.rizkysiregar.narrativ.model.dto.PublicUserDTO;
import com.rizkysiregar.narrativ.model.request.UserSearchRequest;
import com.rizkysiregar.narrativ.model.response.PaginatedResponse;
import com.rizkysiregar.narrativ.service.AuthService;
import io.swagger.annotations.ApiOperation;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import org.springdoc.core.annotations.ParameterObject;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import com.rizkysiregar.narrativ.model.UserPrincipal;
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
            @ParameterObject Pageable pageable
    ) {
        return authService.search(
                new UserSearchRequest(username),
                currentUser.getUserId(),
                pageable
        );
    }

    @Operation(summary = "Search people by a part of their username or name",
            description = "q is 2 to 50 characters (400 otherwise), case-insensitive. Best match first: the exact username, then usernames and names starting with q, then the rest. Deleted and banned users and either side of a block never show up. No match is an empty page (200), unlike GET /users which answers 404.")
    @GetMapping("search")
    public PaginatedResponse<PublicUserDTO> searchPeople(
            @RequestParam String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal currentUser
    ) {
        return authService.searchUsers(q, currentUser.getUserId(), page, size);
    }

    @Operation(summary = "Get active user details by username",
            description = "Case-insensitive. 404 when the user is deleted or banned, or either side blocked the other, like GET /users/{userId}. Profile links of the web client carry the username.")
    @GetMapping("by-username/{username}")
    public PublicUserDTO getUserByUsername(@PathVariable @NotBlank String username,
                                           @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal currentUser) {
        return authService.getUserByUsername(username, currentUser.getUserId());
    }

    @Operation(summary = "Get active user details by userId")
    @GetMapping("{userId}")
    public PublicUserDTO getUserById(@PathVariable @NotBlank String userId,
                                     @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal currentUser) {
        return authService.getUserById(userId, currentUser.getUserId());
    }

}
