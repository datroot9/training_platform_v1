package com.example.training_platform.curriculum;

import java.util.ArrayList;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.example.training_platform.common.dto.PagedResponse;
import com.example.training_platform.dao.AssignmentDao;
import com.example.training_platform.dao.CurriculumDao;
import com.example.training_platform.dao.LearningMaterialDao;
import com.example.training_platform.dao.TaskTemplateDao;
import com.example.training_platform.dao.projection.CurriculumSourceProjection;
import com.example.training_platform.dao.projection.MaterialCopyProjection;
import com.example.training_platform.dao.projection.TaskTemplateCopyProjection;
import com.example.training_platform.curriculum.dto.CreateCurriculumRequest;
import com.example.training_platform.curriculum.dto.CreateCurriculumVersionRequest;
import com.example.training_platform.curriculum.dto.CreateTaskTemplateRequest;
import com.example.training_platform.curriculum.dto.CurriculumDetailResponse;
import com.example.training_platform.curriculum.dto.CurriculumResponse;
import com.example.training_platform.curriculum.dto.LearningMaterialResponse;
import com.example.training_platform.curriculum.dto.TaskTemplateResponse;
import com.example.training_platform.curriculum.dto.UpdateCurriculumRequest;
import com.example.training_platform.curriculum.dto.UpdateTaskTemplateRequest;
import com.example.training_platform.entity.CurriculumEntity;
import com.example.training_platform.entity.LearningMaterialEntity;
import com.example.training_platform.entity.TaskTemplateEntity;
import org.seasar.doma.jdbc.UniqueConstraintException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CurriculumService {

    private static final long CLEAR_LEARNING_MATERIAL_SENTINEL = 0L;
    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 10;
    private static final int MAX_SIZE = 100;

    private final CurriculumDao curriculumDao;
    private final LearningMaterialDao learningMaterialDao;
    private final TaskTemplateDao taskTemplateDao;
    private final AssignmentDao assignmentDao;
    private final LocalPdfStorageService pdfStorage;

    public CurriculumService(
            CurriculumDao curriculumDao,
            LearningMaterialDao learningMaterialDao,
            TaskTemplateDao taskTemplateDao,
            AssignmentDao assignmentDao,
            LocalPdfStorageService pdfStorage
    ) {
        this.curriculumDao = curriculumDao;
        this.learningMaterialDao = learningMaterialDao;
        this.taskTemplateDao = taskTemplateDao;
        this.assignmentDao = assignmentDao;
        this.pdfStorage = pdfStorage;
    }

    @Transactional
    public CurriculumResponse create(Long mentorId, CreateCurriculumRequest request) {
        CurriculumEntity entity = new CurriculumEntity();
        entity.setName(request.name());
        entity.setDescription(request.description());
        entity.setCreatedBy(mentorId);
        entity.setStatus("DRAFT");
        entity.setVersionLabel("1.0");
        curriculumDao.insert(entity);
        Long id = entity.getId();
        if (id == null) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to create curriculum");
        }
        entity.setCurriculumGroupId(id);
        curriculumDao.update(entity);
        return loadCurriculumResponse(mentorId, id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to load curriculum"));
    }

    public PagedResponse<CurriculumResponse> list(Long mentorId,
                                                  String query,
                                                  String status,
                                                  Integer page,
                                                  Integer size,
                                                  String sortBy,
                                                  String sortDir) {
        int safePage = normalizePage(page);
        int safeSize = normalizeSize(size);
        String safeSortBy = resolveSortColumn(sortBy);
        String safeSortDir = resolveSortDirection(sortDir);
        String keyword = normalizeKeyword(query);
        String normalizedStatus = normalizeStatus(status);

        long safeTotal = curriculumDao.countByCreatorWithFilter(mentorId, keyword, normalizedStatus);
        List<CurriculumResponse> rows = curriculumDao
                .listByCreatorWithFilter(
                        mentorId,
                        keyword,
                        normalizedStatus,
                        safeSortBy,
                        safeSortDir,
                        safeSize,
                        (long) safePage * safeSize
                )
                .stream()
                .map(this::mapCurriculumResponse)
                .toList();
        return PagedResponse.of(rows, safePage, safeSize, safeTotal);
    }

    public CurriculumDetailResponse getDetail(Long mentorId, Long curriculumId) {
        CurriculumResponse head = loadCurriculumResponse(mentorId, curriculumId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Curriculum not found"));

        List<LearningMaterialResponse> materials = learningMaterialDao.listByCurriculumId(curriculumId)
                .stream()
                .map(this::mapMaterial)
                .toList();

        List<TaskTemplateResponse> templates = taskTemplateDao.listByCurriculumId(curriculumId)
                .stream()
                .map(this::mapTemplate)
                .toList();

        return new CurriculumDetailResponse(head, materials, templates);
    }

    public CurriculumResponse update(Long mentorId, Long curriculumId, UpdateCurriculumRequest request) {
        assertDraft(mentorId, curriculumId);
        CurriculumEntity entity = curriculumDao.selectByIdAndCreator(curriculumId, mentorId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Curriculum not found"));
        boolean any = false;
        if (request.name() != null && !request.name().isBlank()) {
            entity.setName(request.name());
            any = true;
        }
        if (request.description() != null) {
            entity.setDescription(request.description());
            any = true;
        }
        if (!any) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No fields to update");
        }
        curriculumDao.update(entity);
        return loadCurriculumResponse(mentorId, curriculumId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Curriculum not found"));
    }

    @Transactional
    public CurriculumResponse forkPublishedVersion(Long mentorId, Long sourceCurriculumId, CreateCurriculumVersionRequest request) {
        String normalizedVersion = request.versionLabel().trim();
        if (normalizedVersion.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "versionLabel is required");
        }

        CurriculumSourceProjection source = curriculumDao.selectSourceForFork(sourceCurriculumId, mentorId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Curriculum not found"));
        if (!"PUBLISHED".equals(source.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Only published curricula can be branched into a new draft version"
            );
        }

        if (curriculumDao.countByGroupAndVersion(source.getCurriculumGroupId(), normalizedVersion) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This version label already exists for this curriculum family");
        }

        String newName = request.name() != null && !request.name().isBlank() ? request.name().trim() : source.getName();
        String newDescription = request.description() != null ? request.description() : source.getDescription();

        CurriculumEntity forked = new CurriculumEntity();
        forked.setName(newName);
        forked.setDescription(newDescription);
        forked.setStatus("DRAFT");
        forked.setCreatedBy(mentorId);
        forked.setCurriculumGroupId(source.getCurriculumGroupId());
        forked.setVersionLabel(normalizedVersion);
        try {
            curriculumDao.insert(forked);
        } catch (UniqueConstraintException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This version label already exists for this curriculum family");
        }
        Long newCurriculumId = forked.getId();
        if (newCurriculumId == null) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to create curriculum version");
        }

        List<MaterialCopyProjection> materials = learningMaterialDao.listForCopy(sourceCurriculumId);
        Map<Long, Long> oldMaterialIdToNew = new LinkedHashMap<>();
        for (MaterialCopyProjection m : materials) {
            LearningMaterialEntity copied = new LearningMaterialEntity();
            copied.setCurriculumId(newCurriculumId);
            copied.setSortOrder(m.getSortOrder());
            copied.setFileName(m.getFileName());
            copied.setStoragePath(m.getStoragePath());
            copied.setFileSizeBytes(m.getFileSizeBytes());
            copied.setUploadedAt(m.getUploadedAt());
            learningMaterialDao.insert(copied);
            if (copied.getId() == null) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to copy learning material");
            }
            oldMaterialIdToNew.put(m.getId(), copied.getId());
        }

        List<TaskTemplateCopyProjection> templates = taskTemplateDao.listForCopy(sourceCurriculumId);
        for (TaskTemplateCopyProjection t : templates) {
            Long newLm = t.getLearningMaterialId() == null ? null : oldMaterialIdToNew.get(t.getLearningMaterialId());
            if (t.getLearningMaterialId() != null && newLm == null) {
                throw new ResponseStatusException(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "Task template referenced a learning material that was not copied"
                );
            }
            TaskTemplateEntity copiedTemplate = new TaskTemplateEntity();
            copiedTemplate.setCurriculumId(newCurriculumId);
            copiedTemplate.setLearningMaterialId(newLm);
            copiedTemplate.setSortOrder(t.getSortOrder());
            copiedTemplate.setTitle(t.getTitle());
            copiedTemplate.setDescription(t.getDescription());
            copiedTemplate.setEstimatedDays(t.getEstimatedDays());
            taskTemplateDao.insert(copiedTemplate);
        }

        return loadCurriculumResponse(mentorId, newCurriculumId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to load new curriculum version"));
    }

    public LearningMaterialResponse addMaterial(Long mentorId, Long curriculumId, MultipartFile file, Integer sortOrder) {
        assertDraft(mentorId, curriculumId);
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File is required");
        }
        byte[] data;
        try {
            data = file.getBytes();
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Failed to read file");
        }
        if (!PdfFileValidator.isPdf(data)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only PDF files are allowed");
        }
        String displayName = sanitizeFileName(file.getOriginalFilename());
        int order = resolveMaterialSortOrder(curriculumId, sortOrder);
        String relativePath = pdfStorage.savePdf(curriculumId, data);
        try {
            LearningMaterialEntity entity = new LearningMaterialEntity();
            entity.setCurriculumId(curriculumId);
            entity.setSortOrder(order);
            entity.setFileName(displayName);
            entity.setStoragePath(relativePath);
            entity.setFileSizeBytes((long) data.length);
            learningMaterialDao.insert(entity);
            if (entity.getId() == null) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to save material");
            }
            return mapMaterial(entity);
        } catch (UniqueConstraintException e) {
            pdfStorage.deleteIfExists(relativePath);
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Duplicate sort order for a learning material in this curriculum");
        } catch (RuntimeException e) {
            pdfStorage.deleteIfExists(relativePath);
            throw e;
        }
    }

    public void deleteMaterial(Long mentorId, Long curriculumId, Long materialId) {
        assertDraft(mentorId, curriculumId);
        LearningMaterialEntity material = learningMaterialDao.selectByIdAndCurriculumId(materialId, curriculumId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Material not found"));
        String storagePath = material.getStoragePath();
        learningMaterialDao.delete(material);
        if (learningMaterialDao.countByStoragePath(storagePath) == 0) {
            pdfStorage.deleteIfExists(storagePath);
        }
    }

    public TaskTemplateResponse createTaskTemplate(Long mentorId, Long curriculumId, CreateTaskTemplateRequest request) {
        assertDraft(mentorId, curriculumId);
        if (request.learningMaterialId() != null) {
            if (request.learningMaterialId() <= 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "learningMaterialId must be positive");
            }
            assertMaterialInCurriculum(curriculumId, request.learningMaterialId());
        }
        int order = resolveTemplateSortOrder(curriculumId, request.sortOrder());
        try {
            TaskTemplateEntity entity = new TaskTemplateEntity();
            entity.setCurriculumId(curriculumId);
            entity.setLearningMaterialId(request.learningMaterialId());
            entity.setSortOrder(order);
            entity.setTitle(request.title());
            entity.setDescription(request.description());
            entity.setEstimatedDays(request.estimatedDays());
            taskTemplateDao.insert(entity);
            return mapTemplate(entity);
        } catch (UniqueConstraintException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Duplicate sort order for a task template in this curriculum");
        }
    }

    public TaskTemplateResponse updateTaskTemplate(Long mentorId, Long curriculumId, Long templateId, UpdateTaskTemplateRequest request) {
        assertDraft(mentorId, curriculumId);
        TaskTemplateEntity entity = taskTemplateDao.selectByIdAndCurriculumId(templateId, curriculumId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Task template not found"));
        if (request.learningMaterialId() != null) {
            if (Objects.equals(request.learningMaterialId(), CLEAR_LEARNING_MATERIAL_SENTINEL)) {
                // clear link — handled below
            } else {
                assertMaterialInCurriculum(curriculumId, request.learningMaterialId());
            }
        }
        boolean any = false;
        if (request.title() != null && !request.title().isBlank()) {
            entity.setTitle(request.title());
            any = true;
        }
        if (request.description() != null) {
            entity.setDescription(request.description());
            any = true;
        }
        if (request.estimatedDays() != null) {
            entity.setEstimatedDays(request.estimatedDays());
            any = true;
        }
        if (request.sortOrder() != null) {
            try {
                entity.setSortOrder(request.sortOrder());
            } catch (UniqueConstraintException e) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Duplicate sort order for a task template in this curriculum");
            }
            any = true;
        }
        if (request.learningMaterialId() != null) {
            Long link = Objects.equals(request.learningMaterialId(), CLEAR_LEARNING_MATERIAL_SENTINEL)
                    ? null
                    : request.learningMaterialId();
            entity.setLearningMaterialId(link);
            any = true;
        }
        if (!any) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No fields to update");
        }
        try {
            taskTemplateDao.update(entity);
        } catch (UniqueConstraintException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Duplicate sort order for a task template in this curriculum");
        }
        return mapTemplate(entity);
    }

    public void deleteTaskTemplate(Long mentorId, Long curriculumId, Long templateId) {
        assertDraft(mentorId, curriculumId);
        TaskTemplateEntity entity = taskTemplateDao.selectByIdAndCurriculumId(templateId, curriculumId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Task template not found"));
        int deleted = taskTemplateDao.delete(entity);
        if (deleted < 1) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Task template not found");
        }
    }

    @Transactional
    public void deleteDraft(Long mentorId, Long curriculumId) {
        assertDraft(mentorId, curriculumId);
        if (assignmentDao.countByCurriculum(curriculumId) > 0) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Cannot delete this draft because it is already referenced by trainee assignments"
            );
        }

        List<TaskTemplateEntity> templates = taskTemplateDao.listByCurriculumId(curriculumId);
        if (!templates.isEmpty()) {
            taskTemplateDao.batchDelete(templates);
        }

        List<String> paths = learningMaterialDao.listStoragePathsByCurriculumId(curriculumId);
        List<LearningMaterialEntity> materials = learningMaterialDao.listByCurriculumId(curriculumId);
        if (!materials.isEmpty()) {
            learningMaterialDao.batchDelete(materials);
        }

        Set<String> uniquePaths = new LinkedHashSet<>(paths);
        for (String path : uniquePaths) {
            if (learningMaterialDao.countByStoragePath(path) == 0) {
                pdfStorage.deleteIfExists(path);
            }
        }

        CurriculumEntity curriculum = curriculumDao.selectByIdAndCreator(curriculumId, mentorId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Curriculum cannot be deleted"));
        if (!"DRAFT".equals(curriculum.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Curriculum cannot be deleted");
        }
        int deletedCurriculum = curriculumDao.delete(curriculum);
        if (deletedCurriculum < 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Curriculum cannot be deleted");
        }
    }

    public CurriculumResponse publish(Long mentorId, Long curriculumId) {
        assertDraft(mentorId, curriculumId);
        if (learningMaterialDao.listByCurriculumId(curriculumId).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Add at least one PDF learning material before publishing");
        }
        if (taskTemplateDao.listByCurriculumId(curriculumId).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Add at least one task template before publishing");
        }
        CurriculumEntity curriculum = curriculumDao.selectByIdAndCreator(curriculumId, mentorId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Curriculum cannot be published"));
        if (!"DRAFT".equals(curriculum.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Curriculum cannot be published");
        }
        curriculum.setStatus("PUBLISHED");
        if (curriculum.getPublishedAt() == null) {
            curriculum.setPublishedAt(LocalDateTime.now());
        }
        curriculumDao.update(curriculum);
        return loadCurriculumResponse(mentorId, curriculumId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Curriculum not found"));
    }

    private void assertDraft(Long mentorId, Long curriculumId) {
        CurriculumEntity curriculum = curriculumDao.selectByIdAndCreator(curriculumId, mentorId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Curriculum not found"));
        if (!"DRAFT".equals(curriculum.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Curriculum is " + curriculum.getStatus() + "; materials and templates can only be modified while status is DRAFT"
            );
        }
    }

    private java.util.Optional<CurriculumResponse> loadCurriculumResponse(Long mentorId, Long curriculumId) {
        return curriculumDao.selectByIdAndCreator(curriculumId, mentorId).map(this::mapCurriculumResponse);
    }

    private CurriculumResponse mapCurriculumResponse(CurriculumEntity row) {
        return new CurriculumResponse(
                row.getId(),
                row.getCurriculumGroupId(),
                row.getVersionLabel(),
                row.getName(),
                row.getDescription(),
                row.getStatus(),
                row.getPublishedAt(),
                row.getCreatedAt(),
                row.getUpdatedAt()
        );
    }

    private LearningMaterialResponse mapMaterial(LearningMaterialEntity row) {
        return new LearningMaterialResponse(
                row.getId(),
                row.getCurriculumId(),
                row.getFileName(),
                row.getStoragePath(),
                row.getFileSizeBytes(),
                row.getSortOrder(),
                row.getUploadedAt()
        );
    }

    private TaskTemplateResponse mapTemplate(TaskTemplateEntity row) {
        return new TaskTemplateResponse(
                row.getId(),
                row.getCurriculumId(),
                row.getLearningMaterialId(),
                row.getSortOrder(),
                row.getTitle(),
                row.getDescription(),
                row.getEstimatedDays(),
                row.getCreatedAt(),
                row.getUpdatedAt()
        );
    }

    private int resolveMaterialSortOrder(Long curriculumId, Integer requested) {
        if (requested != null) {
            return requested;
        }
        Integer max = learningMaterialDao.maxSortOrderByCurriculumId(curriculumId);
        return (max == null ? 0 : max) + 1;
    }

    private int resolveTemplateSortOrder(Long curriculumId, Integer requested) {
        if (requested != null) {
            return requested;
        }
        Integer max = taskTemplateDao.maxSortOrderByCurriculumId(curriculumId);
        return (max == null ? 0 : max) + 1;
    }

    private void assertMaterialInCurriculum(Long curriculumId, Long materialId) {
        if (learningMaterialDao.selectByIdAndCurriculumId(materialId, curriculumId).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Learning material does not belong to this curriculum");
        }
    }

    private static String sanitizeFileName(String original) {
        if (original == null || original.isBlank()) {
            return "document.pdf";
        }
        String name = java.nio.file.Path.of(original).getFileName().toString();
        if (name.length() > 255) {
            return name.substring(0, 255);
        }
        return name;
    }

    private static int normalizePage(Integer page) {
        if (page == null || page < 0) {
            return DEFAULT_PAGE;
        }
        return page;
    }

    private static int normalizeSize(Integer size) {
        if (size == null || size < 1) {
            return DEFAULT_SIZE;
        }
        return Math.min(size, MAX_SIZE);
    }

    private static String normalizeKeyword(String query) {
        if (query == null) {
            return null;
        }
        String trimmed = query.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        if (!"DRAFT".equals(normalized) && !"PUBLISHED".equals(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "status must be DRAFT or PUBLISHED");
        }
        return normalized;
    }

    private static String resolveSortColumn(String sortBy) {
        if (sortBy == null || sortBy.isBlank()) {
            return "updated_at";
        }
        return switch (sortBy.trim()) {
            case "name" -> "name";
            case "status" -> "status";
            case "created_at", "createdAt" -> "created_at";
            case "published_at", "publishedAt" -> "published_at";
            case "updated_at", "updatedAt" -> "updated_at";
            case "version_label", "versionLabel" -> "version_label";
            default -> "updated_at";
        };
    }

    private static String resolveSortDirection(String sortDir) {
        if (sortDir == null || sortDir.isBlank()) {
            return "desc";
        }
        return "asc".equals(sortDir.trim().toLowerCase(Locale.ROOT)) ? "asc" : "desc";
    }

}
