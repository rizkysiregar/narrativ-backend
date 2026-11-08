package com.rizkysiregar.feedzmore.controller;

import com.rizkysiregar.feedzmore.model.dto.AdminUserDetailDTO;
import com.rizkysiregar.feedzmore.model.request.AdminUserSearchRequest;
import com.rizkysiregar.feedzmore.model.response.PaginatedResponse;
import com.rizkysiregar.feedzmore.service.UserManagementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/admins")
@PreAuthorize("hasRole('ADMIN')")
public class AdminAccountController {

    private final UserManagementService userManagementService;

    @Operation(summary = "Admin list the admin accounts",
            description = "The same list as /admin/users?role=ADMIN. Create an admin with POST /auth/register-admin, change a role with PATCH /admin/users/{id}.")
    @GetMapping
    public PaginatedResponse<AdminUserDetailDTO> admins(
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String status,
            @Parameter(hidden = true) @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return userManagementService.searchUsersForAdmin(
                new AdminUserSearchRequest(username, null, "ADMIN", status, null, null), pageable);
    }
}
