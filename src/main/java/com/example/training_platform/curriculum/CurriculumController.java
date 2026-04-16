package com.example.training_platform.curriculum;

import java.util.List;

import com.example.training_platform.auth.AuthenticatedUser;
import com.example.training_platform.curriculum.dto.CreateCurriculumRequest;
import com.example.training_platform.curriculum.dto.CreateTaskTemplateRequest;
import com.example.training_platform.curriculum.dto.CurriculumDetailResponse;
import com.example.training_platform.curriculum.dto.CurriculumResponse;
import com.example.training_platform.curriculum.dto.LearningMaterialResponse;
import com.example.training_platform.curriculum.dto.TaskTemplateResponse;
import com.example.training_platform.curriculum.dto.UpdateCurriculumRequest;
import com.example.training_platform.curriculum.dto.UpdateTaskTemplateRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/mentor/curricula")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Mentor curricula", description = "Create and manage training curricula, PDF materials, and task templates")
public class CurriculumController {

    private final CurriculumService curriculumService;

    public CurriculumController(CurriculumService curriculumService) {
        this.curriculumService = curriculumService;
    }

    @PostMapping
    @Operation(summary = "Create curriculum (DRAFT)")
    public ResponseEntity<CurriculumResponse> create(Authentication authentication,
                                                     @Valid @RequestBody CreateCurriculumRequest request) {
        AuthenticatedUser current = (AuthenticatedUser) authentication.getPrincipal();
        CurriculumResponse body = curriculumService.create(current.userId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @GetMapping
    @Operation(summary = "List my curricula")
    public ResponseEntity<List<CurriculumResponse>> list(Authentication authentication,
                                                         @RequestParam(value = "q", required = false) String query) {
        AuthenticatedUser current = (AuthenticatedUser) authentication.getPrincipal();
        return ResponseEntity.ok(curriculumService.list(current.userId(), query));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get curriculum detail with materials and task templates")
    public ResponseEntity<CurriculumDetailResponse> getDetail(Authentication authentication,
                                                            @PathVariable("id") Long curriculumId) {
        AuthenticatedUser current = (AuthenticatedUser) authentication.getPrincipal();
        return ResponseEntity.ok(curriculumService.getDetail(current.userId(), curriculumId));
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Update curriculum metadata (DRAFT only)")
    public ResponseEntity<CurriculumResponse> update(Authentication authentication,
                                                   @PathVariable("id") Long curriculumId,
                                                   @Valid @RequestBody UpdateCurriculumRequest request) {
        AuthenticatedUser current = (AuthenticatedUser) authentication.getPrincipal();
        return ResponseEntity.ok(curriculumService.update(current.userId(), curriculumId, request));
    }

    @PostMapping(value = "/{id}/materials", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload a PDF learning material (DRAFT only)")
    public ResponseEntity<LearningMaterialResponse> uploadMaterial(
            Authentication authentication,
            @PathVariable("id") Long curriculumId,
            @Parameter(description = "PDF file") @RequestPart("file") MultipartFile file,
            @Parameter(description = "Sort order within curriculum; omit to append at the end") @RequestParam(value = "sortOrder", required = false) Integer sortOrder) {
        AuthenticatedUser current = (AuthenticatedUser) authentication.getPrincipal();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(curriculumService.addMaterial(current.userId(), curriculumId, file, sortOrder));
    }

    @DeleteMapping("/{id}/materials/{materialId}")
    @Operation(summary = "Delete a learning material and its stored file (DRAFT only)")
    public ResponseEntity<Void> deleteMaterial(Authentication authentication,
                                               @PathVariable("id") Long curriculumId,
                                               @PathVariable("materialId") Long materialId) {
        AuthenticatedUser current = (AuthenticatedUser) authentication.getPrincipal();
        curriculumService.deleteMaterial(current.userId(), curriculumId, materialId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/task-templates")
    @Operation(summary = "Create task template (DRAFT only)")
    public ResponseEntity<TaskTemplateResponse> createTaskTemplate(Authentication authentication,
                                                                   @PathVariable("id") Long curriculumId,
                                                                   @Valid @RequestBody CreateTaskTemplateRequest request) {
        AuthenticatedUser current = (AuthenticatedUser) authentication.getPrincipal();
        TaskTemplateResponse body = curriculumService.createTaskTemplate(current.userId(), curriculumId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @PatchMapping("/{id}/task-templates/{templateId}")
    @Operation(summary = "Update task template (DRAFT only)",
            description = "Set learningMaterialId to 0 to detach from a learning material.")
    public ResponseEntity<TaskTemplateResponse> updateTaskTemplate(Authentication authentication,
                                                                   @PathVariable("id") Long curriculumId,
                                                                   @PathVariable("templateId") Long templateId,
                                                                   @Valid @RequestBody UpdateTaskTemplateRequest request) {
        AuthenticatedUser current = (AuthenticatedUser) authentication.getPrincipal();
        return ResponseEntity.ok(curriculumService.updateTaskTemplate(current.userId(), curriculumId, templateId, request));
    }

    @DeleteMapping("/{id}/task-templates/{templateId}")
    @Operation(summary = "Delete task template (DRAFT only)")
    public ResponseEntity<Void> deleteTaskTemplate(Authentication authentication,
                                                     @PathVariable("id") Long curriculumId,
                                                     @PathVariable("templateId") Long templateId) {
        AuthenticatedUser current = (AuthenticatedUser) authentication.getPrincipal();
        curriculumService.deleteTaskTemplate(current.userId(), curriculumId, templateId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/publish")
    @Operation(summary = "Publish curriculum (requires at least one PDF and one task template)")
    public ResponseEntity<CurriculumResponse> publish(Authentication authentication,
                                                     @PathVariable("id") Long curriculumId) {
        AuthenticatedUser current = (AuthenticatedUser) authentication.getPrincipal();
        return ResponseEntity.ok(curriculumService.publish(current.userId(), curriculumId));
    }
}
