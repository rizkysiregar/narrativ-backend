package com.rizkysiregar.feedzmore.controller;

import com.rizkysiregar.feedzmore.model.UserPrincipal;
import com.rizkysiregar.feedzmore.model.request.CreateReportRequest;
import com.rizkysiregar.feedzmore.service.ReportService;
import com.rizkysiregar.feedzmore.util.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/reports")
public class ReportController {

    private final ReportService reportService;

    @io.swagger.v3.oas.annotations.Operation(summary = "Report a post, a comment or a user",
            description = "targetType: POST, COMMENT or USER. reason: SPAM, HARASSMENT, HATE_SPEECH, SEXUAL_CONTENT, VIOLENCE, MISINFORMATION or OTHER (OTHER needs a description). One report per user per target, and not for yourself or your own content.")
    @PostMapping
    @PreAuthorize("hasRole('USER')")
    public Operation createReport(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Valid @RequestBody CreateReportRequest request
    ) {
        return reportService.createReport(userPrincipal.getUserId(), request);
    }
}
