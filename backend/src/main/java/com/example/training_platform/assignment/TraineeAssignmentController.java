package com.example.training_platform.assignment;

import java.nio.file.Path;
import java.util.List;

import com.example.training_platform.assignment.dto.AssignmentResponse;
import com.example.training_platform.assignment.dto.AssignmentTaskResponse;
import com.example.training_platform.auth.AuthenticatedUser;
import com.example.training_platform.common.dto.ApiResponse;
import com.example.training_platform.curriculum.LocalPdfStorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/trainee")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Trainee assignments", description = "View current assignment, tasks, and learning materials")
public class TraineeAssignmentController {

    private final AssignmentService assignmentService;
    private final LocalPdfStorageService storageService;

    public TraineeAssignmentController(AssignmentService assignmentService, LocalPdfStorageService storageService) {
        this.assignmentService = assignmentService;
        this.storageService = storageService;
    }

    @GetMapping("/assignments/active")
    @Operation(summary = "Get active assignment of current trainee")
    public ResponseEntity<ApiResponse<AssignmentResponse>> getActiveAssignment(Authentication authentication) {
        AuthenticatedUser current = (AuthenticatedUser) authentication.getPrincipal();
        AssignmentResponse data = assignmentService.getActiveAssignment(current.userId());
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), "Active assignment fetched", data));
    }

    @GetMapping("/assignments/{assignmentId}/tasks")
    @Operation(summary = "List tasks of an assignment owned by current trainee")
    public ResponseEntity<ApiResponse<List<AssignmentTaskResponse>>> getAssignmentTasks(
            Authentication authentication,
            @PathVariable("assignmentId") Long assignmentId
    ) {
        AuthenticatedUser current = (AuthenticatedUser) authentication.getPrincipal();
        List<AssignmentTaskResponse> data = assignmentService.getAssignmentTasks(current.userId(), assignmentId);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), "Assignment tasks fetched", data));
    }

    @GetMapping("/materials/{materialId}/download")
    @Operation(summary = "Download learning material if it belongs to trainee active assignment")
    public ResponseEntity<Resource> downloadMaterial(Authentication authentication, @PathVariable("materialId") Long materialId) {
        AuthenticatedUser current = (AuthenticatedUser) authentication.getPrincipal();
        AssignmentService.DownloadableMaterial material = assignmentService.loadMaterialForTrainee(current.userId(), materialId);
        Path absolutePath = storageService.root().resolve(material.storagePath()).normalize();
        Resource resource = new FileSystemResource(absolutePath);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(ContentDisposition.attachment().filename(material.fileName()).build());
        return ResponseEntity.ok()
                .headers(headers)
                .body(resource);
    }
}
