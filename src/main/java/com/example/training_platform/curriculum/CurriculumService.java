package com.example.training_platform.curriculum;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Objects;

import com.example.training_platform.curriculum.dto.CreateCurriculumRequest;
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
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CurriculumService {

    private static final long CLEAR_LEARNING_MATERIAL_SENTINEL = 0L;

    private final JdbcTemplate jdbcTemplate;
    private final LocalPdfStorageService pdfStorage;

    public CurriculumService(JdbcTemplate jdbcTemplate, LocalPdfStorageService pdfStorage) {
        this.jdbcTemplate = jdbcTemplate;
        this.pdfStorage = pdfStorage;
    }

    public CurriculumResponse create(Long mentorId, CreateCurriculumRequest request) {
        jdbcTemplate.update(
                """
                insert into curricula (name, description, created_by, status)
                values (?, ?, ?, 'DRAFT')
                """,
                request.name(),
                request.description(),
                mentorId
        );
        Long id = jdbcTemplate.queryForObject("select last_insert_id()", Long.class);
        return loadCurriculumResponse(mentorId, id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to load curriculum"));
    }

    public List<CurriculumResponse> list(Long mentorId, String query) {
        String keyword = query == null ? null : query.trim();
        if (keyword == null || keyword.isEmpty()) {
            return jdbcTemplate.query(
                    """
                    select id, name, description, status, published_at, created_at, updated_at
                    from curricula
                    where created_by = ?
                    order by updated_at desc
                    """,
                    this::mapCurriculumResponse,
                    mentorId
            );
        }
        String like = "%" + keyword + "%";
        return jdbcTemplate.query(
                """
                select id, name, description, status, published_at, created_at, updated_at
                from curricula
                where created_by = ?
                  and (lower(name) like lower(concat('%', ?, '%'))
                    or lower(coalesce(description, '')) like lower(concat('%', ?, '%')))
                order by updated_at desc
                """,
                this::mapCurriculumResponse,
                mentorId,
                like,
                like
        );
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
                select id, curriculum_id, learning_material_id, sort_order, title, description, created_at, updated_at
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
        jdbcTemplate.update("delete from learning_materials where id = ? and curriculum_id = ?", materialId, curriculumId);
        pdfStorage.deleteIfExists(paths.get(0));
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
                    insert into task_templates (curriculum_id, learning_material_id, sort_order, title, description)
                    values (?, ?, ?, ?, ?)
                    """,
                    curriculumId,
                    request.learningMaterialId(),
                    order,
                    request.title(),
                    request.description()
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
                select id, curriculum_id, learning_material_id, sort_order, title, description, created_at, updated_at
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
                select id, curriculum_id, learning_material_id, sort_order, title, description, created_at, updated_at
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
        if (!"DRAFT".equals(rows.get(0))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Curriculum is not editable in its current status");
        }
    }

    private java.util.Optional<CurriculumResponse> loadCurriculumResponse(Long mentorId, Long curriculumId) {
        List<CurriculumResponse> rows = jdbcTemplate.query(
                """
                select id, name, description, status, published_at, created_at, updated_at
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
}
