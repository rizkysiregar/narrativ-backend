package com.rizkysiregar.narrativ.controller;

import com.rizkysiregar.narrativ.model.UserPrincipal;
import com.rizkysiregar.narrativ.model.dto.AdminCommentDTO;
import com.rizkysiregar.narrativ.model.request.AdminCommentSearchRequest;
import com.rizkysiregar.narrativ.model.request.ModerationReasonRequest;
import com.rizkysiregar.narrativ.model.response.PaginatedResponse;
import com.rizkysiregar.narrativ.service.AdminContentModerationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/comments")
@PreAuthorize("hasRole('ADMIN')")
public class AdminCommentController {

    private final AdminContentModerationService moderationService;

    @Operation(summary = "Admin list comments", description = "moderationStatus: ACTIVE or TAKEN_DOWN")
    @GetMapping
    public PaginatedResponse<AdminCommentDTO> searchComments(
            @RequestParam(required = false) String postId,
            @RequestParam(required = false) String authorId,
            @RequestParam(required = false) String moderationStatus,
            @RequestParam(required = false) String keyword,
            @Parameter(hidden = true)
            @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return moderationService.searchComments(
                new AdminCommentSearchRequest(postId, authorId, moderationStatus, keyword), pageable);
    }

    @Operation(summary = "Admin take down a comment",
            description = "Hides the text from other users and tells the author why. It can be restored.")
    @PostMapping("{commentId}/takedown")
    public AdminCommentDTO takedownComment(
            @AuthenticationPrincipal UserPrincipal admin,
            @PathVariable @NotBlank String commentId,
            @Valid @RequestBody ModerationReasonRequest request
    ) {
        return moderationService.takedownComment(commentId, request, admin.getUserId());
    }

    @Operation(summary = "Admin restore a taken down comment")
    @PostMapping("{commentId}/restore")
    public AdminCommentDTO restoreComment(@PathVariable @NotBlank String commentId) {
        return moderationService.restoreComment(commentId);
    }
}
