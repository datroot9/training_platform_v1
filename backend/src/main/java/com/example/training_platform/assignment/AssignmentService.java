package com.example.training_platform.assignment;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

import com.example.training_platform.assignment.dto.AssignmentResponse;
import com.example.training_platform.assignment.dto.AssignmentTaskResponse;
import com.example.training_platform.curriculum.LocalPdfStorageService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AssignmentService {

    private final JdbcTemplate jdbcTemplate;
    private final LocalPdfStorageService storageService;

    public AssignmentService(JdbcTemplate jdbcTemplate, LocalPdfStorageService storageService) {
        this.jdbcTemplate = jdbcTemplate;
        this.storageService = storageService;
    }

    @Transactional
    public AssignmentResponse assignCurriculum(Long mentorId, Long traineeId, Long curriculumId) {
        assertTraineeBelongsToMentor(mentorId, traineeId);
        String curriculumName = assertPublishedCurriculumOwnedByMentor(mentorId, curriculumId);
        assertNoActiveAssignment(traineeId);

        return createActiveAssignment(mentorId, traineeId, curriculumId, curriculumName);
    }

    @Transactional
    public AssignmentResponse replaceActiveAssignment(Long mentorId, Long traineeId, Long curriculumId) {
        assertTraineeBelongsToMentor(mentorId, traineeId);
        String curriculumName = assertPublishedCurriculumOwnedByMentor(mentorId, curriculumId);
        cancelActiveAssignment(traineeId);
        return createActiveAssignment(mentorId, traineeId, curriculumId, curriculumName);
    }

    private AssignmentResponse createActiveAssignment(Long mentorId, Long traineeId, Long curriculumId, String curriculumName) {

        try {
            jdbcTemplate.update(
                    """
                    insert into trainee_curriculum_assignments (trainee_id, curriculum_id, assigned_by, status)
                    values (?, ?, ?, 'ACTIVE')
                    """,
                    traineeId,
                    curriculumId,
                    mentorId
            );
        } catch (DuplicateKeyException ex) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Trainee already has an ACTIVE curriculum assignment"
            );
        }

        Long assignmentId = jdbcTemplate.queryForObject("select last_insert_id()", Long.class);
        List<TaskTemplateRow> templates = jdbcTemplate.query(
                """
                select id, title, description
                from task_templates
                where curriculum_id = ?
                order by sort_order asc, id asc
                """,
                (rs, rowNum) -> new TaskTemplateRow(
                        rs.getLong("id"),
                        rs.getString("title"),
                        rs.getString("description")
                ),
                curriculumId
        );

        for (TaskTemplateRow template : templates) {
            jdbcTemplate.update(
                    """
                    insert into tasks (assignment_id, task_template_id, title, description, status)
                    values (?, ?, ?, ?, 'NOT_STARTED')
                    """,
                    assignmentId,
                    template.id(),
                    template.title(),
                    template.description()
            );
        }

        return loadAssignmentById(traineeId, assignmentId, templates.size())
                .orElseGet(() -> new AssignmentResponse(
                        assignmentId,
                        traineeId,
                        curriculumId,
                        curriculumName,
                        "ACTIVE",
                        LocalDateTime.now(),
                        null,
                        templates.size()
                ));
    }

    private void cancelActiveAssignment(Long traineeId) {
        int updated = jdbcTemplate.update(
                """
                update trainee_curriculum_assignments
                set status = 'CANCELLED', ended_at = CURRENT_TIMESTAMP
                where trainee_id = ?
                  and status = 'ACTIVE'
                """,
                traineeId
        );
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No active assignment to replace");
        }
    }

    public AssignmentResponse getActiveAssignment(Long traineeId) {
        List<AssignmentResponse> rows = jdbcTemplate.query(
                """
                select a.id, a.trainee_id, a.curriculum_id, c.name as curriculum_name, a.status, a.assigned_at, a.ended_at
                from trainee_curriculum_assignments a
                join curricula c on c.id = a.curriculum_id
                where a.trainee_id = ?
                  and a.status = 'ACTIVE'
                order by a.assigned_at desc
                limit 1
                """,
                this::mapAssignment,
                traineeId
        );
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No active assignment found");
        }
        return rows.get(0);
    }

    public List<AssignmentTaskResponse> getAssignmentTasks(Long traineeId, Long assignmentId) {
        assertAssignmentBelongsToTrainee(traineeId, assignmentId);
        return jdbcTemplate.query(
                """
                select t.id, t.assignment_id, t.task_template_id, tt.sort_order, t.title, t.description, t.status,
                       t.started_at, t.completed_at, t.created_at, t.updated_at,
                       lm.id as learning_material_id, lm.file_name as learning_material_file_name
                from tasks t
                join task_templates tt on tt.id = t.task_template_id
                left join learning_materials lm on lm.id = tt.learning_material_id
                where t.assignment_id = ?
                order by tt.sort_order asc, t.id asc
                """,
                this::mapAssignmentTask,
                assignmentId
        );
    }

    public DownloadableMaterial loadMaterialForTrainee(Long traineeId, Long materialId) {
        List<DownloadableMaterial> rows = jdbcTemplate.query(
                """
                select lm.id, lm.file_name, lm.storage_path
                from learning_materials lm
                join trainee_curriculum_assignments a on a.curriculum_id = lm.curriculum_id
                where lm.id = ?
                  and a.trainee_id = ?
                  and a.status = 'ACTIVE'
                limit 1
                """,
                (rs, rowNum) -> new DownloadableMaterial(
                        rs.getLong("id"),
                        rs.getString("file_name"),
                        rs.getString("storage_path")
                ),
                materialId,
                traineeId
        );
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Material not found");
        }
        DownloadableMaterial material = rows.get(0);
        Path absolutePath = storageService.root().resolve(material.storagePath()).normalize();
        if (!absolutePath.startsWith(storageService.root()) || !Files.exists(absolutePath)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Material file not found");
        }
        return material;
    }

    private void assertTraineeBelongsToMentor(Long mentorId, Long traineeId) {
        Integer count = jdbcTemplate.queryForObject(
                """
                select count(*)
                from users
                where id = ?
                  and role = 'TRAINEE'
                  and mentor_id = ?
                """,
                Integer.class,
                traineeId,
                mentorId
        );
        if (count == null || count == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Trainee not found");
        }
    }

    private String assertPublishedCurriculumOwnedByMentor(Long mentorId, Long curriculumId) {
        List<String> rows = jdbcTemplate.query(
                """
                select name
                from curricula
                where id = ?
                  and created_by = ?
                  and status = 'PUBLISHED'
                """,
                (rs, rowNum) -> rs.getString("name"),
                curriculumId,
                mentorId
        );
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Curriculum must be PUBLISHED and owned by mentor");
        }
        return rows.get(0);
    }

    private void assertNoActiveAssignment(Long traineeId) {
        Integer activeCount = jdbcTemplate.queryForObject(
                """
                select count(*)
                from trainee_curriculum_assignments
                where trainee_id = ?
                  and status = 'ACTIVE'
                """,
                Integer.class,
                traineeId
        );
        if (activeCount != null && activeCount > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Trainee already has an ACTIVE curriculum assignment");
        }
    }

    private void assertAssignmentBelongsToTrainee(Long traineeId, Long assignmentId) {
        Integer count = jdbcTemplate.queryForObject(
                """
                select count(*)
                from trainee_curriculum_assignments
                where id = ?
                  and trainee_id = ?
                """,
                Integer.class,
                assignmentId,
                traineeId
        );
        if (count == null || count == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Assignment not found");
        }
    }

    private java.util.Optional<AssignmentResponse> loadAssignmentById(Long traineeId, Long assignmentId, int generatedTaskCount) {
        List<AssignmentResponse> rows = jdbcTemplate.query(
                """
                select a.id, a.trainee_id, a.curriculum_id, c.name as curriculum_name, a.status, a.assigned_at, a.ended_at
                from trainee_curriculum_assignments a
                join curricula c on c.id = a.curriculum_id
                where a.id = ?
                  and a.trainee_id = ?
                """,
                this::mapAssignment,
                assignmentId,
                traineeId
        );
        return rows.stream().findFirst()
                .map(r -> new AssignmentResponse(
                        r.id(),
                        r.traineeId(),
                        r.curriculumId(),
                        r.curriculumName(),
                        r.status(),
                        r.assignedAt(),
                        r.endedAt(),
                        generatedTaskCount
                ));
    }

    private AssignmentResponse mapAssignment(ResultSet rs, int rowNum) throws SQLException {
        Timestamp assignedAt = rs.getTimestamp("assigned_at");
        Timestamp endedAt = rs.getTimestamp("ended_at");
        Long assignmentId = rs.getLong("id");
        Integer taskCount = jdbcTemplate.queryForObject(
                "select count(*) from tasks where assignment_id = ?",
                Integer.class,
                assignmentId
        );
        return new AssignmentResponse(
                assignmentId,
                rs.getLong("trainee_id"),
                rs.getLong("curriculum_id"),
                rs.getString("curriculum_name"),
                rs.getString("status"),
                assignedAt == null ? null : assignedAt.toLocalDateTime(),
                endedAt == null ? null : endedAt.toLocalDateTime(),
                taskCount == null ? 0 : taskCount
        );
    }

    private AssignmentTaskResponse mapAssignmentTask(ResultSet rs, int rowNum) throws SQLException {
        Timestamp startedAt = rs.getTimestamp("started_at");
        Timestamp completedAt = rs.getTimestamp("completed_at");
        Timestamp createdAt = rs.getTimestamp("created_at");
        Timestamp updatedAt = rs.getTimestamp("updated_at");
        long materialIdRaw = rs.getLong("learning_material_id");
        Long learningMaterialId = rs.wasNull() ? null : materialIdRaw;
        return new AssignmentTaskResponse(
                rs.getLong("id"),
                rs.getLong("assignment_id"),
                rs.getLong("task_template_id"),
                rs.getInt("sort_order"),
                rs.getString("title"),
                rs.getString("description"),
                rs.getString("status"),
                startedAt == null ? null : startedAt.toLocalDateTime(),
                completedAt == null ? null : completedAt.toLocalDateTime(),
                createdAt == null ? null : createdAt.toLocalDateTime(),
                updatedAt == null ? null : updatedAt.toLocalDateTime(),
                learningMaterialId,
                rs.getString("learning_material_file_name")
        );
    }

    private record TaskTemplateRow(Long id, String title, String description) {
    }

    public record DownloadableMaterial(Long id, String fileName, String storagePath) {
    }
}
