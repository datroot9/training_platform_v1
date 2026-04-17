package com.example.training_platform.trainee;

import java.util.Map;

import com.example.training_platform.auth.AuthenticatedUser;
import com.example.training_platform.common.dto.ApiResponse;
import com.example.training_platform.common.dto.PagedResponse;
import com.example.training_platform.trainee.dto.CreateTraineeRequest;
import com.example.training_platform.trainee.dto.CreateTraineeResponse;
import com.example.training_platform.trainee.dto.ResetPasswordResponse;
import com.example.training_platform.trainee.dto.TraineeResponse;
import com.example.training_platform.trainee.dto.UpdateTraineeStatusRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/mentor/trainees")
@SecurityRequirement(name = "bearerAuth")
public class TraineeController {

    private final TraineeService traineeService;

    public TraineeController(TraineeService traineeService) {
        this.traineeService = traineeService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<CreateTraineeResponse>> create(Authentication authentication,
                                                                     @Valid @RequestBody CreateTraineeRequest request) {
        AuthenticatedUser current = (AuthenticatedUser) authentication.getPrincipal();
        CreateTraineeResponse data = traineeService.create(current.userId(), request);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), "Trainee created", data));
    }

    @GetMapping
    @Operation(summary = "List my trainees with server-side filters, pagination, and sorting")
    public ResponseEntity<ApiResponse<PagedResponse<TraineeResponse>>> list(
            Authentication authentication,
            @Parameter(description = "Free-text search on trainee email/fullName")
            @RequestParam(value = "q", required = false) String query,
            @Parameter(description = "Filter by active status")
            @RequestParam(value = "active", required = false) Boolean active,
            @Parameter(description = "Zero-based page index")
            @RequestParam(value = "page", required = false) Integer page,
            @Parameter(description = "Page size (max 100)")
            @RequestParam(value = "size", required = false) Integer size,
            @Parameter(description = "Sort field: createdAt, fullName, email")
            @RequestParam(value = "sortBy", required = false) String sortBy,
            @Parameter(description = "Sort direction: asc or desc")
            @RequestParam(value = "sortDir", required = false) String sortDir) {
        AuthenticatedUser current = (AuthenticatedUser) authentication.getPrincipal();
        PagedResponse<TraineeResponse> data = traineeService.list(
                current.userId(),
                query,
                active,
                page,
                size,
                sortBy,
                sortDir
        );
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), "Trainee list fetched", data));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<Map<String, Object>>> updateStatus(Authentication authentication,
                                                                         @PathVariable("id") Long traineeId,
                                                                         @RequestBody UpdateTraineeStatusRequest request) {
        AuthenticatedUser current = (AuthenticatedUser) authentication.getPrincipal();
        traineeService.setActive(current.userId(), traineeId, request.active());
        return ResponseEntity.ok(ApiResponse.success(
                HttpStatus.OK.value(),
                "Trainee status updated",
                Map.of("id", traineeId, "active", request.active())
        ));
    }

    @PostMapping("/{id}/reset-password")
    public ResponseEntity<ApiResponse<ResetPasswordResponse>> resetPassword(Authentication authentication,
                                                                            @PathVariable("id") Long traineeId) {
        AuthenticatedUser current = (AuthenticatedUser) authentication.getPrincipal();
        ResetPasswordResponse data = traineeService.resetPassword(current.userId(), traineeId);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), "Trainee password reset", data));
    }
}
