package com.example.training_platform.reporting;

import java.time.LocalDate;
import java.util.List;

import com.example.training_platform.auth.AuthenticatedUser;
import com.example.training_platform.common.dto.ApiResponse;
import com.example.training_platform.reporting.dto.DailyReportResponse;
import com.example.training_platform.reporting.dto.ReviewWeeklySummaryRequest;
import com.example.training_platform.reporting.dto.WeeklySummaryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/mentor/trainees/{traineeId}/assignments/{assignmentId}")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Mentor weekly reports", description = "Review and score trainee weekly summaries")
public class MentorReportingController {

    private final ReportingService reportingService;

    public MentorReportingController(ReportingService reportingService) {
        this.reportingService = reportingService;
    }

    @GetMapping("/weekly-summaries")
    @Operation(summary = "List trainee weekly summaries")
    public ResponseEntity<ApiResponse<List<WeeklySummaryResponse>>> listWeeklySummaries(
            Authentication authentication,
            @PathVariable("traineeId") Long traineeId,
            @PathVariable("assignmentId") Long assignmentId
    ) {
        AuthenticatedUser current = (AuthenticatedUser) authentication.getPrincipal();
        List<WeeklySummaryResponse> data = reportingService.listWeeklySummariesForMentor(
                current.userId(),
                traineeId,
                assignmentId
        );
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), "Weekly summaries fetched", data));
    }

    @GetMapping("/daily-reports")
    @Operation(summary = "List trainee daily reports by date range")
    public ResponseEntity<ApiResponse<List<DailyReportResponse>>> listDailyReports(
            Authentication authentication,
            @PathVariable("traineeId") Long traineeId,
            @PathVariable("assignmentId") Long assignmentId,
            @RequestParam("fromDate")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate fromDate,
            @RequestParam("toDate")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate toDate
    ) {
        AuthenticatedUser current = (AuthenticatedUser) authentication.getPrincipal();
        List<DailyReportResponse> data = reportingService.listDailyReportsForMentor(
                current.userId(),
                traineeId,
                assignmentId,
                fromDate,
                toDate
        );
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), "Daily reports fetched", data));
    }

    @PutMapping("/weekly-summaries/{weekStart}/review")
    @Operation(summary = "Submit mentor weekly score and feedback")
    public ResponseEntity<ApiResponse<WeeklySummaryResponse>> reviewWeeklySummary(
            Authentication authentication,
            @PathVariable("traineeId") Long traineeId,
            @PathVariable("assignmentId") Long assignmentId,
            @PathVariable("weekStart")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate weekStart,
            @Valid @RequestBody ReviewWeeklySummaryRequest request
    ) {
        AuthenticatedUser current = (AuthenticatedUser) authentication.getPrincipal();
        WeeklySummaryResponse data = reportingService.reviewWeeklySummary(
                current.userId(),
                traineeId,
                assignmentId,
                weekStart,
                request
        );
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), "Weekly summary reviewed", data));
    }

    @PostMapping("/weekly-summaries/generate")
    @Operation(summary = "Generate weekly summary")
    public ResponseEntity<ApiResponse<WeeklySummaryResponse>> generateWeeklySummary(
            Authentication authentication,
            @PathVariable("traineeId") Long traineeId,
            @PathVariable("assignmentId") Long assignmentId,
            @RequestParam(value = "weekStart", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate weekStart
    ) {
        AuthenticatedUser current = (AuthenticatedUser) authentication.getPrincipal();
        LocalDate targetWeekStart = weekStart != null
                ? weekStart
                : LocalDate.now().with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY)).minusWeeks(1);
        WeeklySummaryResponse data = reportingService.generateWeeklySummaryForMentor(
                current.userId(),
                traineeId,
                assignmentId,
                targetWeekStart
        );
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), "Weekly summary generated", data));
    }
}
