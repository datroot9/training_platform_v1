package com.example.training_platform.trainee;

import java.util.List;
import java.util.Map;

import com.example.training_platform.auth.AuthenticatedUser;
import com.example.training_platform.trainee.dto.CreateTraineeRequest;
import com.example.training_platform.trainee.dto.CreateTraineeResponse;
import com.example.training_platform.trainee.dto.ResetPasswordResponse;
import com.example.training_platform.trainee.dto.TraineeResponse;
import com.example.training_platform.trainee.dto.UpdateTraineeStatusRequest;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
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
    public ResponseEntity<CreateTraineeResponse> create(Authentication authentication,
                                                        @Valid @RequestBody CreateTraineeRequest request) {
        AuthenticatedUser current = (AuthenticatedUser) authentication.getPrincipal();
        return ResponseEntity.ok(traineeService.create(current.userId(), request));
    }

    @GetMapping
    public ResponseEntity<List<TraineeResponse>> list(Authentication authentication,
                                                      @RequestParam(value = "q", required = false) String query) {
        AuthenticatedUser current = (AuthenticatedUser) authentication.getPrincipal();
        return ResponseEntity.ok(traineeService.list(current.userId(), query));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<Map<String, Object>> updateStatus(Authentication authentication,
                                                            @PathVariable("id") Long traineeId,
                                                            @RequestBody UpdateTraineeStatusRequest request) {
        AuthenticatedUser current = (AuthenticatedUser) authentication.getPrincipal();
        traineeService.setActive(current.userId(), traineeId, request.active());
        return ResponseEntity.ok(Map.of("id", traineeId, "active", request.active()));
    }

    @PostMapping("/{id}/reset-password")
    public ResponseEntity<ResetPasswordResponse> resetPassword(Authentication authentication,
                                                               @PathVariable("id") Long traineeId) {
        AuthenticatedUser current = (AuthenticatedUser) authentication.getPrincipal();
        return ResponseEntity.ok(traineeService.resetPassword(current.userId(), traineeId));
    }
}
