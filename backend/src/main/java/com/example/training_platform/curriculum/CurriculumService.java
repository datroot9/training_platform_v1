package com.example.training_platform.curriculum;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
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

    private final JdbcTemplate jdbcTemplate;
    private final LocalPdfStorageService pdfStorage;

    public CurriculumService(JdbcTemplate jdbcTemplate, LocalPdfStorageService pdfStorage) {
        this.jdbcTemplate = jdbcTemplate;
        this.pdfStorage = pdfStorage;
    }

    @Transactional
    public CurriculumResponse create(Long mentorId, CreateCurriculumRequest request) {
        jdbcTemplate.update(
                """
                insert into curricula (name, description, created_by, status, version_label)
                values (?, ?, ?, 'DRAFT', '1.0')
                """,
                request.name(),
                request.description(),
                mentorId
        );
        Long id = jdbcTemplate.queryForObject("select last_insert_id()", Long.class);
        if (id == null) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to create curriculum");
        }
        jdbcTemplate.update("update curricula set curriculum_group_id = ? where id = ?", id, id);
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

        StringBuilder whereClause = new StringBuilder(
                """
                from curricula
                where created_by = ?
                """
        );
        List<Object> params = new ArrayList<>();
        params.add(mentorId);

        if (keyword != null) {
            whereClause.append(
                    """
                      and (lower(name) like lower(concat('%', ?, '%'))
                        or lower(coalesce(description, '')) like lower(concat('%', ?, '%')))
                    """
            );
            params.add(keyword);
            params.add(keyword);
        }
        if (normalizedStatus != null) {
            whereClause.append(" and status = ?");
            params.add(normalizedStatus);
        }

        Long totalElements = jdbcTemplate.queryForObject(
                "select count(*) " + whereClause,
                Long.class,
                params.toArray()
        );
        long safeTotal = totalElements == null ? 0 : totalElements;

        String listSql =
                """
                select id, curriculum_group_id, version_label, name, description, status, published_at, created_at, updated_at
                """
                        + whereClause +
                        " order by " + safeSortBy + " " + safeSortDir + " limit ? offset ?";
        List<Object> listParams = new ArrayList<>(params);
        listParams.add(safeSize);
        listParams.add((long) safePage * safeSize);
        List<CurriculumResponse> rows = jdbcTemplate.query(listSql, this::mapCurriculumResponse, listParams.toArray());
        return PagedResponse.of(rows, safePage, safeSize, safeTotal);
    }

    public CurriculumDetailResponse getDetail(Long mentorId, Long curriculumId) {
        CurriculumResponse head = loadCurriculumResponse(mentorId, curriculumId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Curriculum not found"));

        List<LearningMaterialResponse> materials = jdbcTemplate.query(
                """
                select id, curriculum_id, file_name, storage_path, file_size_bytes, sort_order, uploaded_at
                from learning_materials
                where curriculum_id = ?
                order by sort_order asc, id asc
                """,
                this::mapMaterial,
                curriculumId
        );

        List<TaskTemplateResponse> templates = jdbcTemplate.query(
                """
                select id, curriculum_id, learning_material_id, sort_order, title, description, estimated_days, created_at, updated_at
                from task_templates
                where curriculum_id = ?
                order by sort_order asc, id asc
                """,
                this::mapTemplate,
                curriculumId
        );

        return new CurriculumDetailResponse(head, materials, templates);
    }

    public CurriculumResponse update(Long mentorId, Long curriculumId, UpdateCurriculumRequest request) {
        assertDraft(mentorId, curriculumId);
        boolean any = false;
        if (request.name() != null && !request.name().isBlank()) {
            jdbcTemplate.update("update curricula set name = ? where id = ? and created_by = ?", request.name(), curriculumId, mentorId);
            any = true;
        }
        if (request.description() != null) {
            jdbcTemplate.update("update curricula set description = ? where id = ? and created_by = ?", request.description(), curriculumId, mentorId);
            any = true;
        }
        if (!any) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No fields to update");
        }
        return loadCurriculumResponse(mentorId, curriculumId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Curriculum not found"));
    }

    @Transactional
    public CurriculumResponse forkPublishedVersion(Long mentorId, Long sourceCurriculumId, CreateCurriculumVersionRequest request) {
        String normalizedVersion = request.versionLabel().trim();
        if (normalizedVersion.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "versionLabel is required");
        }

        List<SourceCurriculumRow> headRows = jdbcTemplate.query(
                """
                select curriculum_group_id, name, description, status
                from curricula
                where id = ? and created_by = ?
                """,
                (rs, i) -> new SourceCurriculumRow(
                        rs.getLong("curriculum_group_id"),
                        rs.getString("name"),
                        rs.getString("description"),
                        rs.getString("status")
                ),
                sourceCurriculumId,
                mentorId
        );
        if (headRows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Curriculum not found");
        }
        SourceCurriculumRow source = headRows.get(0);
        if (!"PUBLISHED".equals(source.status())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Only published curricula can be branched into a new draft version"
            );
        }

        Integer dup = jdbcTemplate.queryForObject(
                """
                select count(*) from curricula
                where curriculum_group_id = ? and version_label = ?
                """,
                Integer.class,
                source.groupId(),
                normalizedVersion
        );
        if (dup != null && dup > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This version label already exists for this curriculum family");
        }

        String newName = request.name() != null && !request.name().isBlank() ? request.name().trim() : source.name();
        String newDescription = request.description() != null ? request.description() : source.description();

        try {
            jdbcTemplate.update(
                    """
                    insert into curricula (name, description, created_by, status, curriculum_group_id, version_label)
                    values (?, ?, ?, 'DRAFT', ?, ?)
                    """,
                    newName,
                    newDescription,
                    mentorId,
                    source.groupId(),
                    normalizedVersion
            );
        } catch (DuplicateKeyException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This version label already exists for this curriculum family");
        }
        Long newCurriculumId = jdbcTemplate.queryForObject("select last_insert_id()", Long.class);
        if (newCurriculumId == null) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to create curriculum version");
        }

        List<MaterialCopyRow> materials = jdbcTemplate.query(
                """
                select id, sort_order, file_name, storage_path, file_size_bytes, uploaded_at
                from learning_materials
                where curriculum_id = ?
                order by sort_order asc, id asc
                """,
                (rs, i) -> new MaterialCopyRow(
                        rs.getLong("id"),
                        rs.getInt("sort_order"),
                        rs.getString("file_name"),
                        rs.getString("storage_path"),
                        rs.getLong("file_size_bytes"),
                        rs.getTimestamp("uploaded_at")
                ),
                sourceCurriculumId
        );

        Map<Long, Long> oldMaterialIdToNew = new LinkedHashMap<>();
        for (MaterialCopyRow m : materials) {
            jdbcTemplate.update(
                    """
                    insert into learning_materials (curriculum_id, sort_order, file_name, storage_path, file_size_bytes, uploaded_at)
                    values (?, ?, ?, ?, ?, ?)
                    """,
                    newCurriculumId,
                    m.sortOrder(),
                    m.fileName(),
                    m.storagePath(),
                    m.fileSizeBytes(),
                    m.uploadedAt()
            );
            Long newMaterialId = jdbcTemplate.queryForObject("select last_insert_id()", Long.class);
            if (newMaterialId == null) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to copy learning material");
            }
            oldMaterialIdToNew.put(m.oldId(), newMaterialId);
        }

        List<TemplateCopyRow> templates = jdbcTemplate.query(
                """
                select learning_material_id, sort_order, title, description, estimated_days
                from task_templates
                where curriculum_id = ?
                order by sort_order asc, id asc
                """,
                (rs, i) -> {
                    long lm = rs.getLong("learning_material_id");
                    boolean lmNull = rs.wasNull();
                    return new TemplateCopyRow(
                            lmNull ? null : lm,
                            rs.getInt("sort_order"),
                            rs.getString("title"),
                            rs.getString("description"),
                            rs.getObject("estimated_days", Integer.class)
                    );
                },
                sourceCurriculumId
        );

        for (TemplateCopyRow t : templates) {
            Long newLm = t.oldLearningMaterialId() == null ? null : oldMaterialIdToNew.get(t.oldLearningMaterialId());
            if (t.oldLearningMaterialId() != null && newLm == null) {
                throw new ResponseStatusException(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "Task template referenced a learning material that was not copied"
                );
            }
            jdbcTemplate.update(
                    """
                    insert into task_templates (curriculum_id, learning_material_id, sort_order, title, description, estimated_days)
                    values (?, ?, ?, ?, ?, ?)
                    """,
                    newCurriculumId,
                    newLm,
                    t.sortOrder(),
                    t.title(),
                    t.description(),
                    t.estimatedDays()
            );
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
            jdbcTemplate.update(
                    """
                    insert into learning_materials (curriculum_id, sort_order, file_name, storage_path, file_size_bytes)
                    values (?, ?, ?, ?, ?)
                    """,
                    curriculumId,
                    order,
                    displayName,
                    relativePath,
                    data.length
            );
        } catch (DuplicateKeyException e) {
            pdfStorage.deleteIfExists(relativePath);
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Duplicate sort order for a learning material in this curriculum");
        } catch (DataIntegrityViolationException e) {
            pdfStorage.deleteIfExists(relativePath);
            if (e.getCause() != null && e.getCause().getMessage() != null
                    && e.getCause().getMessage().contains("Duplicate")) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Duplicate sort order for a learning material in this curriculum");
            }
            throw e;
        }
        Long materialId = jdbcTemplate.queryForObject("select last_insert_id()", Long.class);
        return jdbcTemplate.query(
                """
                select id, curriculum_id, file_name, storage_path, file_size_bytes, sort_order, uploaded_at
                from learning_materials where id = ?
                """,
                this::mapMaterial,
                materialId
        ).get(0);
    }

    public void deleteMaterial(Long mentorId, Long curriculumId, Long materialId) {
        assertDraft(mentorId, curriculumId);
        List<String> paths = jdbcTemplate.query(
                """
                select storage_path from learning_materials
                where id = ? and curriculum_id = ?
                """,
                (rs, i) -> rs.getString("storage_path"),
                materialId,
                curriculumId
        );
        if (paths.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Material not found");
        }
        String storagePath = paths.get(0);
        jdbcTemplate.update("delete from learning_materials where id = ? and curriculum_id = ?", materialId, curriculumId);
        Integer remaining = jdbcTemplate.queryForObject(
                "select count(*) from learning_materials where storage_path = ?",
                Integer.class,
                storagePath
        );
        if (remaining == null || remaining == 0) {
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
            jdbcTemplate.update(
                    """
                    insert into task_templates (curriculum_id, learning_material_id, sort_order, title, description, estimated_days)
                    values (?, ?, ?, ?, ?, ?)
                    """,
                    curriculumId,
                    request.learningMaterialId(),
                    order,
                    request.title(),
                    request.description(),
                    request.estimatedDays()
            );
        } catch (DuplicateKeyException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Duplicate sort order for a task template in this curriculum");
        } catch (DataIntegrityViolationException e) {
            if (e.getCause() != null && e.getCause().getMessage() != null
                    && e.getCause().getMessage().contains("Duplicate")) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Duplicate sort order for a task template in this curriculum");
            }
            throw e;
        }
        Long templateId = jdbcTemplate.queryForObject("select last_insert_id()", Long.class);
        return jdbcTemplate.query(
                """
                select id, curriculum_id, learning_material_id, sort_order, title, description, estimated_days, created_at, updated_at
                from task_templates where id = ?
                """,
                this::mapTemplate,
                templateId
        ).get(0);
    }

    public TaskTemplateResponse updateTaskTemplate(Long mentorId, Long curriculumId, Long templateId, UpdateTaskTemplateRequest request) {
        assertDraft(mentorId, curriculumId);
        int existing = jdbcTemplate.queryForObject(
                "select count(*) from task_templates where id = ? and curriculum_id = ?",
                Integer.class,
                templateId,
                curriculumId
        );
        if (existing == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Task template not found");
        }
        if (request.learningMaterialId() != null) {
            if (Objects.equals(request.learningMaterialId(), CLEAR_LEARNING_MATERIAL_SENTINEL)) {
                // clear link — handled below
            } else {
                assertMaterialInCurriculum(curriculumId, request.learningMaterialId());
            }
        }
        boolean any = false;
        if (request.title() != null && !request.title().isBlank()) {
            jdbcTemplate.update(
                    "update task_templates set title = ? where id = ? and curriculum_id = ?",
                    request.title(),
                    templateId,
                    curriculumId
            );
            any = true;
        }
        if (request.description() != null) {
            jdbcTemplate.update(
                    "update task_templates set description = ? where id = ? and curriculum_id = ?",
                    request.description(),
                    templateId,
                    curriculumId
            );
            any = true;
        }
        if (request.estimatedDays() != null) {
            jdbcTemplate.update(
                    "update task_templates set estimated_days = ? where id = ? and curriculum_id = ?",
                    request.estimatedDays(),
                    templateId,
                    curriculumId
            );
            any = true;
        }
        if (request.sortOrder() != null) {
            try {
                jdbcTemplate.update(
                        "update task_templates set sort_order = ? where id = ? and curriculum_id = ?",
                        request.sortOrder(),
                        templateId,
                        curriculumId
                );
            } catch (DuplicateKeyException e) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Duplicate sort order for a task template in this curriculum");
            } catch (DataIntegrityViolationException e) {
                if (e.getCause() != null && e.getCause().getMessage() != null
                        && e.getCause().getMessage().contains("Duplicate")) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Duplicate sort order for a task template in this curriculum");
                }
                throw e;
            }
            any = true;
        }
        if (request.learningMaterialId() != null) {
            Long link = Objects.equals(request.learningMaterialId(), CLEAR_LEARNING_MATERIAL_SENTINEL)
                    ? null
                    : request.learningMaterialId();
            jdbcTemplate.update(
                    "update task_templates set learning_material_id = ? where id = ? and curriculum_id = ?",
                    link,
                    templateId,
                    curriculumId
            );
            any = true;
        }
        if (!any) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No fields to update");
        }
        return jdbcTemplate.query(
                """
                select id, curriculum_id, learning_material_id, sort_order, title, description, estimated_days, created_at, updated_at
                from task_templates where id = ? and curriculum_id = ?
                """,
                this::mapTemplate,
                templateId,
                curriculumId
        ).get(0);
    }

    public void deleteTaskTemplate(Long mentorId, Long curriculumId, Long templateId) {
        assertDraft(mentorId, curriculumId);
        int deleted = jdbcTemplate.update(
                "delete from task_templates where id = ? and curriculum_id = ?",
                templateId,
                curriculumId
        );
        if (deleted == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Task template not found");
        }
    }

    @Transactional
    public void deleteDraft(Long mentorId, Long curriculumId) {
        assertDraft(mentorId, curriculumId);
        Integer assignmentCount = jdbcTemplate.queryForObject(
                "select count(*) from trainee_curriculum_assignments where curriculum_id = ?",
                Integer.class,
                curriculumId
        );
        if (assignmentCount != null && assignmentCount > 0) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Cannot delete this draft because it is already referenced by trainee assignments"
            );
        }

        jdbcTemplate.update("delete from task_templates where curriculum_id = ?", curriculumId);

        List<String> paths = jdbcTemplate.query(
                "select storage_path from learning_materials where curriculum_id = ?",
                (rs, i) -> rs.getString("storage_path"),
                curriculumId
        );
        jdbcTemplate.update("delete from learning_materials where curriculum_id = ?", curriculumId);

        Set<String> uniquePaths = new LinkedHashSet<>(paths);
        for (String path : uniquePaths) {
            Integer remaining = jdbcTemplate.queryForObject(
                    "select count(*) from learning_materials where storage_path = ?",
                    Integer.class,
                    path
            );
            if (remaining == null || remaining == 0) {
                pdfStorage.deleteIfExists(path);
            }
        }

        int deletedCurriculum = jdbcTemplate.update(
                "delete from curricula where id = ? and created_by = ? and status = 'DRAFT'",
                curriculumId,
                mentorId
        );
        if (deletedCurriculum == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Curriculum cannot be deleted");
        }
    }

    public CurriculumResponse publish(Long mentorId, Long curriculumId) {
        assertDraft(mentorId, curriculumId);
        Integer materialCount = jdbcTemplate.queryForObject(
                "select count(*) from learning_materials where curriculum_id = ?",
                Integer.class,
                curriculumId
        );
        Integer templateCount = jdbcTemplate.queryForObject(
                "select count(*) from task_templates where curriculum_id = ?",
                Integer.class,
                curriculumId
        );
        if (materialCount == null || materialCount < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Add at least one PDF learning material before publishing");
        }
        if (templateCount == null || templateCount < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Add at least one task template before publishing");
        }
        int updated = jdbcTemplate.update(
                """
                update curricula
                set status = 'PUBLISHED', published_at = coalesce(published_at, CURRENT_TIMESTAMP)
                where id = ? and created_by = ? and status = 'DRAFT'
                """,
                curriculumId,
                mentorId
        );
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Curriculum cannot be published");
        }
        return loadCurriculumResponse(mentorId, curriculumId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Curriculum not found"));
    }

    private void assertDraft(Long mentorId, Long curriculumId) {
        List<String> rows = jdbcTemplate.query(
                "select status from curricula where id = ? and created_by = ?",
                (rs, i) -> rs.getString("status"),
                curriculumId,
                mentorId
        );
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Curriculum not found");
        }
        String currentStatus = rows.get(0);
        if (!"DRAFT".equals(currentStatus)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Curriculum is " + currentStatus + "; materials and templates can only be modified while status is DRAFT"
            );
        }
    }

    private java.util.Optional<CurriculumResponse> loadCurriculumResponse(Long mentorId, Long curriculumId) {
        List<CurriculumResponse> rows = jdbcTemplate.query(
                """
                select id, curriculum_group_id, version_label, name, description, status, published_at, created_at, updated_at
                from curricula
                where id = ? and created_by = ?
                """,
                this::mapCurriculumResponse,
                curriculumId,
                mentorId
        );
        return rows.stream().findFirst();
    }

    private CurriculumResponse mapCurriculumResponse(ResultSet rs, int rowNum) throws SQLException {
        Timestamp pub = rs.getTimestamp("published_at");
        Timestamp created = rs.getTimestamp("created_at");
        Timestamp updated = rs.getTimestamp("updated_at");
        return new CurriculumResponse(
                rs.getLong("id"),
                rs.getLong("curriculum_group_id"),
                rs.getString("version_label"),
                rs.getString("name"),
                rs.getString("description"),
                rs.getString("status"),
                pub == null ? null : pub.toLocalDateTime(),
                created == null ? null : created.toLocalDateTime(),
                updated == null ? null : updated.toLocalDateTime()
        );
    }

    private LearningMaterialResponse mapMaterial(ResultSet rs, int rowNum) throws SQLException {
        Timestamp up = rs.getTimestamp("uploaded_at");
        return new LearningMaterialResponse(
                rs.getLong("id"),
                rs.getLong("curriculum_id"),
                rs.getString("file_name"),
                rs.getString("storage_path"),
                rs.getLong("file_size_bytes"),
                rs.getInt("sort_order"),
                up == null ? null : up.toLocalDateTime()
        );
    }

    private TaskTemplateResponse mapTemplate(ResultSet rs, int rowNum) throws SQLException {
        long lm = rs.getLong("learning_material_id");
        boolean lmNull = rs.wasNull();
        Timestamp created = rs.getTimestamp("created_at");
        Timestamp updated = rs.getTimestamp("updated_at");
        return new TaskTemplateResponse(
                rs.getLong("id"),
                rs.getLong("curriculum_id"),
                lmNull ? null : lm,
                rs.getInt("sort_order"),
                rs.getString("title"),
                rs.getString("description"),
                rs.getObject("estimated_days", Integer.class),
                created == null ? null : created.toLocalDateTime(),
                updated == null ? null : updated.toLocalDateTime()
        );
    }

    private int resolveMaterialSortOrder(Long curriculumId, Integer requested) {
        if (requested != null) {
            return requested;
        }
        Integer next = jdbcTemplate.queryForObject(
                "select coalesce(max(sort_order), 0) + 1 from learning_materials where curriculum_id = ?",
                Integer.class,
                curriculumId
        );
        return next == null ? 1 : next;
    }

    private int resolveTemplateSortOrder(Long curriculumId, Integer requested) {
        if (requested != null) {
            return requested;
        }
        Integer next = jdbcTemplate.queryForObject(
                "select coalesce(max(sort_order), 0) + 1 from task_templates where curriculum_id = ?",
                Integer.class,
                curriculumId
        );
        return next == null ? 1 : next;
    }

    private void assertMaterialInCurriculum(Long curriculumId, Long materialId) {
        Integer n = jdbcTemplate.queryForObject(
                "select count(*) from learning_materials where id = ? and curriculum_id = ?",
                Integer.class,
                materialId,
                curriculumId
        );
        if (n == null || n == 0) {
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

    private record SourceCurriculumRow(long groupId, String name, String description, String status) {
    }

    private record MaterialCopyRow(long oldId, int sortOrder, String fileName, String storagePath, long fileSizeBytes,
                                   Timestamp uploadedAt) {
    }

    private record TemplateCopyRow(Long oldLearningMaterialId, int sortOrder, String title, String description,
                                   Integer estimatedDays) {
    }
}
