package com.example.training_platform.reporting;

import java.time.LocalDate;
import java.util.List;

import com.example.training_platform.auth.AuthenticatedUser;
import com.example.training_platform.common.dto.ApiResponse;
import com.example.training_platform.reporting.dto.DailyReportResponse;
import com.example.training_platform.reporting.dto.SaveDailyReportRequest;
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
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/trainee")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Trainee reports", description = "Create daily reports and view weekly summaries")
public class TraineeReportingController {

    private final ReportingService reportingService;

    public TraineeReportingController(ReportingService reportingService) {
        this.reportingService = reportingService;
    }

    @GetMapping("/daily-reports")
    @Operation(summary = "List trainee daily reports across assignments with optional filters")
    public ResponseEntity<ApiResponse<List<DailyReportResponse>>> listAllDailyReports(
            Authentication authentication,
            @RequestParam(value = "assignmentId", required = false) Long assignmentId,
            @RequestParam(value = "fromDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate fromDate,
            @RequestParam(value = "toDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate toDate
    ) {
        AuthenticatedUser current = (AuthenticatedUser) authentication.getPrincipal();
        List<DailyReportResponse> data = reportingService.listDailyReportsForTrainee(
                current.userId(),
                assignmentId,
                fromDate,
                toDate
        );
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), "Daily reports fetched", data));
    }

    @GetMapping("/assignments/{assignmentId}/daily-reports")
    @Operation(summary = "List daily reports in a week")
    public ResponseEntity<ApiResponse<List<DailyReportResponse>>> listDailyReportsByWeek(
            Authentication authentication,
            @PathVariable("assignmentId") Long assignmentId,
            @RequestParam("weekStart")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate weekStart
    ) {
        AuthenticatedUser current = (AuthenticatedUser) authentication.getPrincipal();
        List<DailyReportResponse> data = reportingService.listDailyReportsByWeek(current.userId(), assignmentId, weekStart);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), "Daily reports fetched", data));
    }

    @GetMapping("/assignments/{assignmentId}/daily-reports/{reportDate}")
    @Operation(summary = "Get daily report by date")
    public ResponseEntity<ApiResponse<DailyReportResponse>> getDailyReport(
            Authentication authentication,
            @PathVariable("assignmentId") Long assignmentId,
            @PathVariable("reportDate")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate reportDate
    ) {
        AuthenticatedUser current = (AuthenticatedUser) authentication.getPrincipal();
        DailyReportResponse data = reportingService.getDailyReport(current.userId(), assignmentId, reportDate)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Daily report not found"));
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), "Daily report fetched", data));
    }

    @PutMapping("/assignments/{assignmentId}/daily-reports/{reportDate}")
    @Operation(summary = "Save daily report as draft")
    public ResponseEntity<ApiResponse<DailyReportResponse>> saveDailyReportDraft(
            Authentication authentication,
            @PathVariable("assignmentId") Long assignmentId,
            @PathVariable("reportDate")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate reportDate,
            @Valid @RequestBody SaveDailyReportRequest request
    ) {
        AuthenticatedUser current = (AuthenticatedUser) authentication.getPrincipal();
        DailyReportResponse data = reportingService.saveDailyReportDraft(
                current.userId(),
                assignmentId,
                reportDate,
                request
        );
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), "Daily report draft saved", data));
    }

    @PostMapping("/assignments/{assignmentId}/daily-reports/{reportDate}/submit")
    @Operation(summary = "Submit daily report")
    public ResponseEntity<ApiResponse<DailyReportResponse>> submitDailyReport(
            Authentication authentication,
            @PathVariable("assignmentId") Long assignmentId,
            @PathVariable("reportDate")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate reportDate,
            @Valid @RequestBody SaveDailyReportRequest request
    ) {
        AuthenticatedUser current = (AuthenticatedUser) authentication.getPrincipal();
        DailyReportResponse data = reportingService.submitDailyReport(
                current.userId(),
                assignmentId,
                reportDate,
                request
        );
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), "Daily report submitted", data));
    }

    @GetMapping("/assignments/{assignmentId}/weekly-summaries")
    @Operation(summary = "List weekly summaries for assignment")
    public ResponseEntity<ApiResponse<List<WeeklySummaryResponse>>> listWeeklySummaries(
            Authentication authentication,
            @PathVariable("assignmentId") Long assignmentId
    ) {
        AuthenticatedUser current = (AuthenticatedUser) authentication.getPrincipal();
        List<WeeklySummaryResponse> data = reportingService.listWeeklySummariesForTrainee(current.userId(), assignmentId);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), "Weekly summaries fetched", data));
    }
}
