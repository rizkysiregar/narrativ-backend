package com.rizkysiregar.feedzmore.controller;

import com.rizkysiregar.feedzmore.model.document.UserDocument;
import com.rizkysiregar.feedzmore.model.dto.UserDetailDTO;
import com.rizkysiregar.feedzmore.model.request.UserSearchRequest;
import com.rizkysiregar.feedzmore.model.response.PaginatedResponse;
import com.rizkysiregar.feedzmore.service.AuthService;
import com.rizkysiregar.feedzmore.service.UserSearchService;
import io.swagger.annotations.ApiOperation;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@AllArgsConstructor
@RequestMapping("/api/v1/users")
public class UserSearchController {

    final AuthService authService;
    final UserSearchService userSearchService;

    @ApiOperation(value = "Search users by it's username")
    @GetMapping()
    public PaginatedResponse<UserDetailDTO> searchUsers(
            @RequestParam(required = false) @NotBlank String username,
            @Parameter(hidden = true) Pageable pageable
    ) {
        return authService.search(
                new UserSearchRequest(username),
                pageable
        );
    }
    @ApiOperation(value = "Getting user details data by userId", notes = "pass userId that you got from login response")
    @GetMapping("/{userId}")
    public UserDetailDTO getUserById(@PathVariable @NotBlank String userId) {
        return authService.getUserById(userId);
    }

    @ApiOperation(value = "Search user by username but return it as List not pagination")
    @GetMapping("usersList")
    public List<UserDetailDTO> searchUsersList(
            @RequestParam(required = false) @NotBlank String username
    ) {
        return authService.searchList(
                new UserSearchRequest(username)
        );
    }

    @GetMapping("search-elastic-search")
    public List<UserDocument> searchUsersViaES(
            @RequestParam String query
    ) {
        return userSearchService.searchUsers(query);
    }

}
