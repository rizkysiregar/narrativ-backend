package com.rizkysiregar.feedzmore.controller;

import com.rizkysiregar.feedzmore.model.UserPrincipal;
import com.rizkysiregar.feedzmore.model.dto.AdminPostDTO;
import com.rizkysiregar.feedzmore.model.request.AdminPostSearchRequest;
import com.rizkysiregar.feedzmore.model.request.ModerationReasonRequest;
import com.rizkysiregar.feedzmore.model.response.PaginatedResponse;
import com.rizkysiregar.feedzmore.service.AdminContentModerationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/posts")
@PreAuthorize("hasRole('ADMIN')")
public class AdminPostController {

    private final AdminContentModerationService moderationService;

    @Operation(summary = "Admin list posts",
            description = "Includes taken down posts and posts the owner deleted. moderationStatus: ACTIVE or TAKEN_DOWN, mediaType: IMAGE or VIDEO, deleted: true/false, createdFrom/createdTo are inclusive dates (yyyy-MM-dd) in Asia/Jakarta time.")
    @GetMapping
    public PaginatedResponse<AdminPostDTO> searchPosts(
            @RequestParam(required = false) String authorId,
            @RequestParam(required = false) String mediaType,
            @RequestParam(required = false) String moderationStatus,
            @RequestParam(required = false) Boolean deleted,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdTo,
            @Parameter(hidden = true)
            @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return moderationService.searchPosts(new AdminPostSearchRequest(
                authorId, mediaType, moderationStatus, deleted, keyword, createdFrom, createdTo), pageable);
    }

    @Operation(summary = "Admin get post detail with media URLs, like and comment counts")
    @GetMapping("{postId}")
    public AdminPostDTO getPost(@PathVariable @NotBlank String postId) {
        return moderationService.getPost(postId);
    }

    @Operation(summary = "Admin take down a post",
            description = "Hides the post from other users and tells the owner why. The media is kept, so it can be restored.")
    @PostMapping("{postId}/takedown")
    public AdminPostDTO takedownPost(
            @AuthenticationPrincipal UserPrincipal admin,
            @PathVariable @NotBlank String postId,
            @Valid @RequestBody ModerationReasonRequest request
    ) {
        return moderationService.takedownPost(postId, request, admin.getUserId());
    }

    @Operation(summary = "Admin restore a taken down post")
    @PostMapping("{postId}/restore")
    public AdminPostDTO restorePost(@PathVariable @NotBlank String postId) {
        return moderationService.restorePost(postId);
    }

    @Operation(summary = "Admin permanently delete a post",
            description = "IRREVERSIBLE. Removes the post, its likes and comments and the media in object storage. Works on a post in any state. A reason is required and what was removed is kept in the audit log.")
    @PostMapping("{postId}/hard-delete")
    public com.rizkysiregar.feedzmore.util.Operation hardDeletePost(
            @AuthenticationPrincipal UserPrincipal admin,
            @PathVariable @NotBlank String postId,
            @Valid @RequestBody ModerationReasonRequest request
    ) {
        return moderationService.hardDeletePost(postId, request, admin.getUserId());
    }
}
