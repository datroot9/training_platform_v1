package com.example.training_platform.assignment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

import com.example.training_platform.assignment.dto.AssignmentTaskResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class AssignmentServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private com.example.training_platform.curriculum.LocalPdfStorageService storageService;

    @TempDir
    Path tempDir;

    private AssignmentService assignmentService;

    @BeforeEach
    void setUp() {
        assignmentService = new AssignmentService(jdbcTemplate, storageService);
        lenient().when(storageService.root()).thenReturn(tempDir);
    }

    @Test
    void assignCurriculumSuccessWhenNoActiveAssignment() {
        when(jdbcTemplate.queryForObject(contains("from users"), eq(Integer.class), eq(11L), eq(5L))).thenReturn(1);
        when(jdbcTemplate.query(contains("from curricula"), any(RowMapper.class), eq(22L), eq(5L)))
                .thenReturn(List.of("Published Curriculum"));
        when(jdbcTemplate.queryForObject(contains("from trainee_curriculum_assignments"), eq(Integer.class), eq(11L)))
                .thenReturn(0);
        when(jdbcTemplate.update(contains("insert into trainee_curriculum_assignments"), eq(11L), eq(22L), eq(5L)))
                .thenReturn(1);
        when(jdbcTemplate.queryForObject("select last_insert_id()", Long.class)).thenReturn(100L);
        when(jdbcTemplate.query(contains("from task_templates"), any(RowMapper.class), eq(22L)))
                .thenReturn(List.of());
        when(jdbcTemplate.query(contains("from trainee_curriculum_assignments a"), any(RowMapper.class), eq(100L), eq(11L)))
                .thenReturn(List.of());

        var result = assignmentService.assignCurriculum(5L, 11L, 22L);

        assertThat(result.id()).isEqualTo(100L);
        assertThat(result.traineeId()).isEqualTo(11L);
        assertThat(result.curriculumId()).isEqualTo(22L);
        assertThat(result.curriculumName()).isEqualTo("Published Curriculum");
        assertThat(result.status()).isEqualTo("ACTIVE");
        assertThat(result.generatedTaskCount()).isEqualTo(0);
    }

    @Test
    void assignCurriculumBlockedWhenActiveAssignmentExists() {
        when(jdbcTemplate.queryForObject(contains("from users"), eq(Integer.class), eq(11L), eq(5L))).thenReturn(1);
        when(jdbcTemplate.query(contains("from curricula"), any(RowMapper.class), eq(22L), eq(5L)))
                .thenReturn(List.of("Published Curriculum"));
        when(jdbcTemplate.queryForObject(contains("from trainee_curriculum_assignments"), eq(Integer.class), eq(11L)))
                .thenReturn(1);

        assertThatThrownBy(() -> assignmentService.assignCurriculum(5L, 11L, 22L))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void replaceActiveAssignmentSuccessWhenCurrentActiveExists() {
        when(jdbcTemplate.queryForObject(contains("from users"), eq(Integer.class), eq(11L), eq(5L))).thenReturn(1);
        when(jdbcTemplate.query(contains("from curricula"), any(RowMapper.class), eq(22L), eq(5L)))
                .thenReturn(List.of("Published Curriculum"));
        when(jdbcTemplate.update(contains("set status = 'CANCELLED'"), eq(11L))).thenReturn(1);
        when(jdbcTemplate.update(contains("insert into trainee_curriculum_assignments"), eq(11L), eq(22L), eq(5L)))
                .thenReturn(1);
        when(jdbcTemplate.queryForObject("select last_insert_id()", Long.class)).thenReturn(101L);
        when(jdbcTemplate.query(contains("from task_templates"), any(RowMapper.class), eq(22L)))
                .thenReturn(List.of());
        when(jdbcTemplate.query(contains("from trainee_curriculum_assignments a"), any(RowMapper.class), eq(101L), eq(11L)))
                .thenReturn(List.of());

        var result = assignmentService.replaceActiveAssignment(5L, 11L, 22L);

        assertThat(result.id()).isEqualTo(101L);
        assertThat(result.traineeId()).isEqualTo(11L);
        assertThat(result.curriculumId()).isEqualTo(22L);
        assertThat(result.curriculumName()).isEqualTo("Published Curriculum");
        assertThat(result.status()).isEqualTo("ACTIVE");
    }

    @Test
    void replaceActiveAssignmentBlockedWhenNoActiveAssignment() {
        when(jdbcTemplate.queryForObject(contains("from users"), eq(Integer.class), eq(11L), eq(5L))).thenReturn(1);
        when(jdbcTemplate.query(contains("from curricula"), any(RowMapper.class), eq(22L), eq(5L)))
                .thenReturn(List.of("Published Curriculum"));
        when(jdbcTemplate.update(contains("set status = 'CANCELLED'"), eq(11L))).thenReturn(0);

        assertThatThrownBy(() -> assignmentService.replaceActiveAssignment(5L, 11L, 22L))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void replaceActiveAssignmentBlockedWhenCurriculumInvalid() {
        when(jdbcTemplate.queryForObject(contains("from users"), eq(Integer.class), eq(11L), eq(5L))).thenReturn(1);
        when(jdbcTemplate.query(contains("from curricula"), any(RowMapper.class), eq(22L), eq(5L)))
                .thenReturn(List.of());

        assertThatThrownBy(() -> assignmentService.replaceActiveAssignment(5L, 11L, 22L))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void loadMaterialBlockedWhenNotOwnedByActiveAssignment() {
        when(jdbcTemplate.query(contains("from learning_materials lm"), any(RowMapper.class), eq(9L), eq(11L)))
                .thenReturn(List.of());

        assertThatThrownBy(() -> assignmentService.loadMaterialForTrainee(11L, 9L))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void loadMaterialReturnsNotFoundWhenPhysicalFileMissing() {
        var material = new AssignmentService.DownloadableMaterial(9L, "lesson.pdf", "seed/demo/lesson.pdf");
        when(jdbcTemplate.query(contains("from learning_materials lm"), any(RowMapper.class), eq(9L), eq(11L)))
                .thenReturn(List.of(material));

        assertThatThrownBy(() -> assignmentService.loadMaterialForTrainee(11L, 9L))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void loadMaterialSuccessWhenPhysicalFileExists() throws Exception {
        Path relative = Path.of("seed/demo/lesson.pdf");
        Path absolute = tempDir.resolve(relative);
        Files.createDirectories(absolute.getParent());
        Files.writeString(absolute, "%PDF-1.4 test");

        var material = new AssignmentService.DownloadableMaterial(9L, "lesson.pdf", relative.toString().replace('\\', '/'));
        when(jdbcTemplate.query(contains("from learning_materials lm"), any(RowMapper.class), eq(9L), eq(11L)))
                .thenReturn(List.of(material));

        var result = assignmentService.loadMaterialForTrainee(11L, 9L);
        assertThat(result.id()).isEqualTo(9L);
        assertThat(result.fileName()).isEqualTo("lesson.pdf");
        verify(storageService, atLeastOnce()).root();
    }

    @Test
    void updateTaskStatusBlockedWhenTransitionIsInvalid() throws Exception {
        when(jdbcTemplate.queryForObject(contains("from trainee_curriculum_assignments"), eq(Integer.class), eq(100L), eq(11L)))
                .thenReturn(1);
        when(jdbcTemplate.query(contains("select status, started_at"), any(RowMapper.class), eq(200L), eq(100L)))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    RowMapper<Object> mapper = (RowMapper<Object>) invocation.getArgument(1);
                    ResultSet rs = org.mockito.Mockito.mock(ResultSet.class);
                    when(rs.getString("status")).thenReturn("DONE");
                    when(rs.getTimestamp("started_at")).thenReturn(Timestamp.valueOf(LocalDateTime.now().minusDays(2)));
                    return List.of(mapper.mapRow(rs, 0));
                });

        assertThatThrownBy(() -> assignmentService.updateTaskStatus(11L, 100L, 200L, "NOT_STARTED"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void updateTaskStatusSuccessWhenTransitionIsValid() throws Exception {
        when(jdbcTemplate.queryForObject(contains("from trainee_curriculum_assignments"), eq(Integer.class), eq(100L), eq(11L)))
                .thenReturn(1);
        when(jdbcTemplate.query(contains("select status, started_at"), any(RowMapper.class), eq(200L), eq(100L)))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    RowMapper<Object> mapper = (RowMapper<Object>) invocation.getArgument(1);
                    ResultSet rs = org.mockito.Mockito.mock(ResultSet.class);
                    when(rs.getString("status")).thenReturn("NOT_STARTED");
                    when(rs.getTimestamp("started_at")).thenReturn(null);
                    return List.of(mapper.mapRow(rs, 0));
                });
        when(jdbcTemplate.update(contains("update tasks"), eq("IN_PROGRESS"), any(), isNull(), eq(200L), eq(100L)))
                .thenReturn(1);
        when(jdbcTemplate.query(contains("from tasks t"), any(RowMapper.class), eq(200L), eq(100L)))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    RowMapper<AssignmentTaskResponse> mapper = (RowMapper<AssignmentTaskResponse>) invocation.getArgument(1);
                    ResultSet rs = org.mockito.Mockito.mock(ResultSet.class);
                    when(rs.getLong("id")).thenReturn(200L);
                    when(rs.getLong("assignment_id")).thenReturn(100L);
                    when(rs.getLong("task_template_id")).thenReturn(12L);
                    when(rs.getInt("sort_order")).thenReturn(1);
                    when(rs.getString("title")).thenReturn("Task A");
                    when(rs.getString("description")).thenReturn("Desc");
                    when(rs.getObject("estimated_days", Integer.class)).thenReturn(3);
                    when(rs.getString("status")).thenReturn("IN_PROGRESS");
                    Timestamp now = Timestamp.valueOf(LocalDateTime.now());
                    when(rs.getTimestamp("started_at")).thenReturn(now);
                    when(rs.getTimestamp("completed_at")).thenReturn(null);
                    when(rs.getTimestamp("created_at")).thenReturn(now);
                    when(rs.getTimestamp("updated_at")).thenReturn(now);
                    when(rs.getLong("learning_material_id")).thenReturn(0L);
                    when(rs.wasNull()).thenReturn(true);
                    when(rs.getString("learning_material_file_name")).thenReturn(null);
                    return List.of(mapper.mapRow(rs, 0));
                });

        AssignmentTaskResponse result = assignmentService.updateTaskStatus(11L, 100L, 200L, "IN_PROGRESS");

        assertThat(result.id()).isEqualTo(200L);
        assertThat(result.status()).isEqualTo("IN_PROGRESS");
    }
}
