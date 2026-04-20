package com.example.training_platform.curriculum;

import com.example.training_platform.auth.AuthenticatedUser;
import com.example.training_platform.common.dto.ApiResponse;
import com.example.training_platform.common.dto.PagedResponse;
import com.example.training_platform.curriculum.dto.CreateCurriculumRequest;
import com.example.training_platform.curriculum.dto.CreateCurriculumVersionRequest;
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
    public ResponseEntity<ApiResponse<CurriculumResponse>> create(Authentication authentication,
                                                                  @Valid @RequestBody CreateCurriculumRequest request) {
        AuthenticatedUser current = (AuthenticatedUser) authentication.getPrincipal();
        CurriculumResponse body = curriculumService.create(current.userId(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(HttpStatus.CREATED.value(), "Curriculum created", body));
    }

    @GetMapping
    @Operation(summary = "List my curricula with server-side filters, pagination, and sorting")
    public ResponseEntity<ApiResponse<PagedResponse<CurriculumResponse>>> list(
            Authentication authentication,
            @Parameter(description = "Free-text search on curriculum name/description")
            @RequestParam(value = "q", required = false) String query,
            @Parameter(description = "Filter by curriculum status: DRAFT or PUBLISHED")
            @RequestParam(value = "status", required = false) String status,
            @Parameter(description = "Zero-based page index")
            @RequestParam(value = "page", required = false) Integer page,
            @Parameter(description = "Page size (max 100)")
            @RequestParam(value = "size", required = false) Integer size,
            @Parameter(description = "Sort field: updatedAt, createdAt, name, status, publishedAt, versionLabel")
            @RequestParam(value = "sortBy", required = false) String sortBy,
            @Parameter(description = "Sort direction: asc or desc")
            @RequestParam(value = "sortDir", required = false) String sortDir) {
        AuthenticatedUser current = (AuthenticatedUser) authentication.getPrincipal();
        PagedResponse<CurriculumResponse> data = curriculumService.list(
                current.userId(),
                query,
                status,
                page,
                size,
                sortBy,
                sortDir
        );
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), "Curriculum list fetched", data));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get curriculum detail with materials and task templates")
    public ResponseEntity<ApiResponse<CurriculumDetailResponse>> getDetail(Authentication authentication,
                                                                           @PathVariable("id") Long curriculumId) {
        AuthenticatedUser current = (AuthenticatedUser) authentication.getPrincipal();
        CurriculumDetailResponse data = curriculumService.getDetail(current.userId(), curriculumId);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), "Curriculum detail fetched", data));
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Update curriculum metadata (DRAFT only)")
    public ResponseEntity<ApiResponse<CurriculumResponse>> update(Authentication authentication,
                                                                  @PathVariable("id") Long curriculumId,
                                                                  @Valid @RequestBody UpdateCurriculumRequest request) {
        AuthenticatedUser current = (AuthenticatedUser) authentication.getPrincipal();
        CurriculumResponse data = curriculumService.update(current.userId(), curriculumId, request);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), "Curriculum updated", data));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete curriculum draft (DRAFT only)")
    public ResponseEntity<ApiResponse<Void>> deleteDraft(Authentication authentication,
                                                         @PathVariable("id") Long curriculumId) {
        AuthenticatedUser current = (AuthenticatedUser) authentication.getPrincipal();
        curriculumService.deleteDraft(current.userId(), curriculumId);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), "Curriculum draft deleted", null));
    }

    @PostMapping(value = "/{id}/materials", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload a PDF learning material (DRAFT only)")
    public ResponseEntity<ApiResponse<LearningMaterialResponse>> uploadMaterial(
            Authentication authentication,
            @PathVariable("id") Long curriculumId,
            @Parameter(description = "PDF file") @RequestPart("file") MultipartFile file,
            @Parameter(description = "Sort order within curriculum; omit to append at the end") @RequestParam(value = "sortOrder", required = false) Integer sortOrder) {
        AuthenticatedUser current = (AuthenticatedUser) authentication.getPrincipal();
        LearningMaterialResponse data = curriculumService.addMaterial(current.userId(), curriculumId, file, sortOrder);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(HttpStatus.CREATED.value(), "Learning material uploaded", data));
    }

    @DeleteMapping("/{id}/materials/{materialId}")
    @Operation(
            summary = "Delete a learning material row (DRAFT only)",
            description = "Removes the row for this curriculum. The PDF file on disk is deleted only when no other "
                    + "learning_material row (including other curriculum versions) still references the same storage_path."
    )
    public ResponseEntity<ApiResponse<Void>> deleteMaterial(Authentication authentication,
                                                            @PathVariable("id") Long curriculumId,
                                                            @PathVariable("materialId") Long materialId) {
        AuthenticatedUser current = (AuthenticatedUser) authentication.getPrincipal();
        curriculumService.deleteMaterial(current.userId(), curriculumId, materialId);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), "Learning material deleted", null));
    }

    @PostMapping("/{id}/task-templates")
    @Operation(summary = "Create task template (DRAFT only)")
    public ResponseEntity<ApiResponse<TaskTemplateResponse>> createTaskTemplate(Authentication authentication,
                                                                                @PathVariable("id") Long curriculumId,
                                                                                @Valid @RequestBody CreateTaskTemplateRequest request) {
        AuthenticatedUser current = (AuthenticatedUser) authentication.getPrincipal();
        TaskTemplateResponse body = curriculumService.createTaskTemplate(current.userId(), curriculumId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(HttpStatus.CREATED.value(), "Task template created", body));
    }

    @PatchMapping("/{id}/task-templates/{templateId}")
    @Operation(summary = "Update task template (DRAFT only)",
            description = "Set learningMaterialId to 0 to detach from a learning material.")
    public ResponseEntity<ApiResponse<TaskTemplateResponse>> updateTaskTemplate(Authentication authentication,
                                                                                @PathVariable("id") Long curriculumId,
                                                                                @PathVariable("templateId") Long templateId,
                                                                                @Valid @RequestBody UpdateTaskTemplateRequest request) {
        AuthenticatedUser current = (AuthenticatedUser) authentication.getPrincipal();
        TaskTemplateResponse data = curriculumService.updateTaskTemplate(current.userId(), curriculumId, templateId, request);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), "Task template updated", data));
    }

    @DeleteMapping("/{id}/task-templates/{templateId}")
    @Operation(summary = "Delete task template (DRAFT only)")
    public ResponseEntity<ApiResponse<Void>> deleteTaskTemplate(Authentication authentication,
                                                                @PathVariable("id") Long curriculumId,
                                                                @PathVariable("templateId") Long templateId) {
        AuthenticatedUser current = (AuthenticatedUser) authentication.getPrincipal();
        curriculumService.deleteTaskTemplate(current.userId(), curriculumId, templateId);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), "Task template deleted", null));
    }

    @PostMapping("/{id}/versions")
    @Operation(
            summary = "Create a new draft version from a published curriculum",
            description = """
                    Copies task templates and learning_material rows into a new DRAFT curriculum in the same \
                    curriculum family (curriculumGroupId). PDF files are shared via the same storage_path; deleting \
                    a material in one draft only removes the file from disk when no row still references that path."""
    )
    public ResponseEntity<ApiResponse<CurriculumResponse>> createVersion(Authentication authentication,
                                                                         @PathVariable("id") Long sourceCurriculumId,
                                                                         @Valid @RequestBody CreateCurriculumVersionRequest request) {
        AuthenticatedUser current = (AuthenticatedUser) authentication.getPrincipal();
        CurriculumResponse data = curriculumService.forkPublishedVersion(current.userId(), sourceCurriculumId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(HttpStatus.CREATED.value(), "Curriculum version created", data));
    }

    @PostMapping("/{id}/publish")
    @Operation(summary = "Publish curriculum (requires at least one PDF and one task template)")
    public ResponseEntity<ApiResponse<CurriculumResponse>> publish(Authentication authentication,
                                                                   @PathVariable("id") Long curriculumId) {
        AuthenticatedUser current = (AuthenticatedUser) authentication.getPrincipal();
        CurriculumResponse data = curriculumService.publish(current.userId(), curriculumId);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), "Curriculum published", data));
    }
}
