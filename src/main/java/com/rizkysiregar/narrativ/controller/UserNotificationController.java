package com.rizkysiregar.narrativ.controller;

import com.rizkysiregar.narrativ.model.UserPrincipal;
import com.rizkysiregar.narrativ.model.request.MarkNotificationReadRequest;
import com.rizkysiregar.narrativ.model.response.NotificationResponse;
import com.rizkysiregar.narrativ.model.response.PaginatedResponse;
import com.rizkysiregar.narrativ.model.response.UnreadCountResponse;
import com.rizkysiregar.narrativ.service.NotificationInboxService;
import com.rizkysiregar.narrativ.util.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The signed-in user's inbox: likes, comments, replies, follows and pins, newest first. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/users/me/notifications")
@PreAuthorize("hasRole('USER')")
public class UserNotificationController {

    private final NotificationInboxService inboxService;

    @io.swagger.v3.oas.annotations.Operation(summary = "A page of your inbox; pass nextCursor back as ?cursor= for the next one")
    @GetMapping
    public PaginatedResponse<NotificationResponse> list(
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        return inboxService.list(userPrincipal.getUserId(), cursor, size);
    }

    @io.swagger.v3.oas.annotations.Operation(summary = "How many inbox entries are unread, for the badge")
    @GetMapping("/unread-count")
    public UnreadCountResponse unreadCount(@AuthenticationPrincipal UserPrincipal userPrincipal) {
        return new UnreadCountResponse(inboxService.unreadCount(userPrincipal.getUserId()));
    }

    @io.swagger.v3.oas.annotations.Operation(summary = "Mark the whole inbox read")
    @PostMapping("/read-all")
    public Operation readAll(@AuthenticationPrincipal UserPrincipal userPrincipal) {
        inboxService.markAllRead(userPrincipal.getUserId());
        return Operation.UPDATED;
    }

    @io.swagger.v3.oas.annotations.Operation(summary = "Mark one inbox entry read, by its groupKey")
    @PostMapping("/read")
    public Operation read(@Valid @RequestBody MarkNotificationReadRequest request,
                          @AuthenticationPrincipal UserPrincipal userPrincipal) {
        inboxService.markRead(userPrincipal.getUserId(), request.getGroupKey());
        return Operation.UPDATED;
    }
}
