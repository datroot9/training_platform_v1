package com.example.training_platform.assignment;

import java.util.List;

import com.example.training_platform.assignment.dto.AssignCurriculumRequest;
import com.example.training_platform.assignment.dto.AssignmentResponse;
import com.example.training_platform.assignment.dto.AssignmentTaskResponse;
import com.example.training_platform.auth.AuthenticatedUser;
import com.example.training_platform.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/mentor/trainees")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Mentor assignments", description = "Assign published curricula to trainees")
public class MentorAssignmentController {

    private final AssignmentService assignmentService;

    public MentorAssignmentController(AssignmentService assignmentService) {
        this.assignmentService = assignmentService;
    }

    @GetMapping("/{traineeId}/assignments")
    @Operation(summary = "List all curriculum assignments for trainee (any status, newest first)")
    public ResponseEntity<ApiResponse<List<AssignmentResponse>>> listTraineeAssignments(
            Authentication authentication,
            @Parameter(description = "Trainee user id")
            @PathVariable("traineeId") Long traineeId
    ) {
        AuthenticatedUser current = (AuthenticatedUser) authentication.getPrincipal();
        List<AssignmentResponse> data = assignmentService.listAssignmentsForMentor(current.userId(), traineeId);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), "Assignments fetched", data));
    }

    @GetMapping("/{traineeId}/assignments/active")
    @Operation(summary = "Get trainee active assignment (read-only, mentor must own trainee)")
    public ResponseEntity<ApiResponse<AssignmentResponse>> getTraineeActiveAssignment(
            Authentication authentication,
            @Parameter(description = "Trainee user id")
            @PathVariable("traineeId") Long traineeId
    ) {
        AuthenticatedUser current = (AuthenticatedUser) authentication.getPrincipal();
        AssignmentResponse data = assignmentService.getActiveAssignmentForMentor(current.userId(), traineeId);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), "Active assignment fetched", data));
    }

    @GetMapping("/{traineeId}/assignments/{assignmentId}/tasks")
    @Operation(summary = "List tasks for a trainee assignment (read-only)")
    public ResponseEntity<ApiResponse<List<AssignmentTaskResponse>>> getTraineeAssignmentTasks(
            Authentication authentication,
            @PathVariable("traineeId") Long traineeId,
            @PathVariable("assignmentId") Long assignmentId
    ) {
        AuthenticatedUser current = (AuthenticatedUser) authentication.getPrincipal();
        List<AssignmentTaskResponse> data = assignmentService.getAssignmentTasksForMentor(
                current.userId(),
                traineeId,
                assignmentId
        );
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), "Assignment tasks fetched", data));
    }

    @PostMapping("/{traineeId}/assignments")
    @Operation(summary = "Assign a published curriculum to trainee and generate tasks")
    public ResponseEntity<ApiResponse<AssignmentResponse>> assignCurriculum(
            Authentication authentication,
            @PathVariable("traineeId") Long traineeId,
            @Valid @RequestBody AssignCurriculumRequest request
    ) {
        AuthenticatedUser current = (AuthenticatedUser) authentication.getPrincipal();
        AssignmentResponse data = assignmentService.assignCurriculum(
                current.userId(),
                traineeId,
                request.curriculumId()
        );
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(HttpStatus.CREATED.value(), "Curriculum assigned successfully", data));
    }

    @PutMapping("/{traineeId}/assignments/active")
    @Operation(summary = "Replace active assignment of trainee with another published curriculum")
    public ResponseEntity<ApiResponse<AssignmentResponse>> replaceActiveAssignment(
            Authentication authentication,
            @PathVariable("traineeId") Long traineeId,
            @Valid @RequestBody AssignCurriculumRequest request
    ) {
        AuthenticatedUser current = (AuthenticatedUser) authentication.getPrincipal();
        AssignmentResponse data = assignmentService.replaceActiveAssignment(
                current.userId(),
                traineeId,
                request.curriculumId()
        );
        return ResponseEntity.ok(
                ApiResponse.success(HttpStatus.OK.value(), "Active assignment replaced successfully", data)
        );
    }
}
