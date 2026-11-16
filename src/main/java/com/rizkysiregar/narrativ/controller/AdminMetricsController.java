package com.rizkysiregar.narrativ.controller;

import com.rizkysiregar.narrativ.model.dto.AdminGrowthDTO;
import com.rizkysiregar.narrativ.model.dto.AdminOverviewDTO;
import com.rizkysiregar.narrativ.model.enums.GrowthSource;
import com.rizkysiregar.narrativ.service.AdminMetricsService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/metrics")
@PreAuthorize("hasRole('ADMIN')")
public class AdminMetricsController {

    private static final String GROWTH_DESCRIPTION = "Counts what was created per day, week (Monday to Sunday) or month in Asia/Jakarta time. "
            + "granularity: DAY (default), WEEK or MONTH. The range is either range (TODAY, LAST_7_DAYS, LAST_30_DAYS, THIS_MONTH, LAST_MONTH, THIS_YEAR) "
            + "or both from and to (yyyy-MM-dd, inclusive); with neither it is LAST_30_DAYS, and giving both a range and dates is a 400. "
            + "Limits: DAY up to 366 days, WEEK up to 2 years, MONTH up to 5 years. Every bucket is returned, 0 when empty, and the total is compared "
            + "with the period of the same length right before. Cached for a minute.";

    private final AdminMetricsService metricsService;

    @Operation(summary = "Dashboard overview: users and posts by status and what is new today, this week and this month, comments and open reports",
            description = "Cached for a minute. 'New' counts everything created in the period whatever its status is now.")
    @GetMapping("overview")
    public AdminOverviewDTO overview() {
        return metricsService.overview();
    }

    @Operation(summary = "Growth of users: registrations per day, week or month", description = GROWTH_DESCRIPTION)
    @GetMapping("users/growth")
    public AdminGrowthDTO usersGrowth(
            @RequestParam(required = false) String granularity,
            @RequestParam(required = false) String range,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return metricsService.growth(GrowthSource.USERS, granularity, range, from, to);
    }

    @Operation(summary = "Growth of posts: posts created per day, week or month", description = GROWTH_DESCRIPTION)
    @GetMapping("posts/growth")
    public AdminGrowthDTO postsGrowth(
            @RequestParam(required = false) String granularity,
            @RequestParam(required = false) String range,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return metricsService.growth(GrowthSource.POSTS, granularity, range, from, to);
    }
}
