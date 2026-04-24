package com.example.training_platform.assignment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import com.example.training_platform.assignment.dto.AssignmentResponse;
import com.example.training_platform.assignment.dto.AssignmentTaskResponse;
import com.example.training_platform.dao.AssignmentDao;
import com.example.training_platform.dao.CurriculumDao;
import com.example.training_platform.dao.LearningMaterialDao;
import com.example.training_platform.dao.TaskDao;
import com.example.training_platform.dao.TaskTemplateDao;
import com.example.training_platform.dao.UserDao;
import com.example.training_platform.dao.projection.AssignmentProjection;
import com.example.training_platform.dao.projection.AssignmentTaskProjection;
import com.example.training_platform.entity.CurriculumEntity;
import com.example.training_platform.entity.LearningMaterialEntity;
import com.example.training_platform.entity.TaskEntity;
import com.example.training_platform.entity.TaskTemplateEntity;
import com.example.training_platform.entity.TraineeCurriculumAssignmentEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class AssignmentServiceTest {

    @Mock
    private UserDao userDao;

    @Mock
    private CurriculumDao curriculumDao;

    @Mock
    private AssignmentDao assignmentDao;

    @Mock
    private TaskTemplateDao taskTemplateDao;

    @Mock
    private TaskDao taskDao;

    @Mock
    private LearningMaterialDao learningMaterialDao;

    @Mock
    private com.example.training_platform.curriculum.LocalPdfStorageService storageService;

    @TempDir
    Path tempDir;

    private AssignmentService assignmentService;

    @BeforeEach
    void setUp() {
        assignmentService = new AssignmentService(
                userDao,
                curriculumDao,
                assignmentDao,
                taskTemplateDao,
                taskDao,
                learningMaterialDao,
                storageService
        );
        lenient().when(storageService.root()).thenReturn(tempDir);
    }

    @Test
    void assignCurriculumSuccessWhenNoActiveAssignment() {
        CurriculumEntity curriculum = new CurriculumEntity();
        curriculum.setId(22L);
        curriculum.setName("Published Curriculum");
        curriculum.setStatus("PUBLISHED");

        TraineeCurriculumAssignmentEntity assignmentEntity = new TraineeCurriculumAssignmentEntity();
        assignmentEntity.setId(100L);

        when(userDao.countTraineeByMentor(5L, 11L)).thenReturn(1L);
        when(curriculumDao.selectByIdAndCreator(22L, 5L)).thenReturn(Optional.of(curriculum));
        when(assignmentDao.countActiveByTrainee(11L)).thenReturn(0L);
        when(taskTemplateDao.listByCurriculumId(22L)).thenReturn(List.of());
        doAnswer(invocation -> {
            TraineeCurriculumAssignmentEntity arg = invocation.getArgument(0);
            arg.setId(100L);
            return 1;
        }).when(assignmentDao).insert(org.mockito.ArgumentMatchers.any(TraineeCurriculumAssignmentEntity.class));
        when(assignmentDao.selectAssignmentProjectionByIdAndTrainee(100L, 11L)).thenReturn(Optional.empty());

        var result = assignmentService.assignCurriculum(5L, 11L, 22L);

        assertThat(result.traineeId()).isEqualTo(11L);
        assertThat(result.curriculumId()).isEqualTo(22L);
        assertThat(result.status()).isEqualTo("ACTIVE");
        assertThat(result.generatedTaskCount()).isEqualTo(0);
    }

    @Test
    void listAssignmentsForMentorThrowsWhenTraineeNotLinked() {
        when(userDao.countTraineeByMentor(5L, 11L)).thenReturn(0L);

        assertThatThrownBy(() -> assignmentService.listAssignmentsForMentor(5L, 11L))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void listAssignmentsForMentorReturnsMappedAssignments() {
        AssignmentProjection row = new AssignmentProjection();
        row.setId(77L);
        row.setTraineeId(11L);
        row.setCurriculumId(22L);
        row.setCurriculumName("Curriculum A");
        row.setCurriculumDescription("Desc");
        row.setCurriculumVersionLabel("1.0");
        row.setMentorName("Mentor");
        row.setMentorEmail("m@local");
        row.setStatus("CANCELLED");
        row.setAssignedAt(LocalDateTime.now().minusDays(5));
        row.setEndedAt(LocalDateTime.now().minusDays(1));

        when(userDao.countTraineeByMentor(5L, 11L)).thenReturn(1L);
        when(assignmentDao.listAssignmentProjectionsByTraineeId(11L)).thenReturn(List.of(row));
        when(taskDao.countByAssignmentId(77L)).thenReturn(2L);
        when(taskDao.sumEstimatedDaysByAssignmentId(77L)).thenReturn(5);

        List<AssignmentResponse> result = assignmentService.listAssignmentsForMentor(5L, 11L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo(77L);
        assertThat(result.get(0).status()).isEqualTo("CANCELLED");
        assertThat(result.get(0).curriculumName()).isEqualTo("Curriculum A");
        assertThat(result.get(0).generatedTaskCount()).isEqualTo(2);
    }

    @Test
    void listAssignmentsForTraineeReturnsMappedAssignments() {
        AssignmentProjection row = new AssignmentProjection();
        row.setId(88L);
        row.setTraineeId(11L);
        row.setCurriculumId(33L);
        row.setCurriculumName("Curriculum B");
        row.setCurriculumDescription(null);
        row.setCurriculumVersionLabel("2.0");
        row.setMentorName("Mentor");
        row.setMentorEmail("m@local");
        row.setStatus("ACTIVE");
        row.setAssignedAt(LocalDateTime.now());
        row.setEndedAt(null);

        when(assignmentDao.listAssignmentProjectionsByTraineeId(11L)).thenReturn(List.of(row));
        when(taskDao.countByAssignmentId(88L)).thenReturn(0L);
        when(taskDao.sumEstimatedDaysByAssignmentId(88L)).thenReturn(null);

        List<AssignmentResponse> result = assignmentService.listAssignmentsForTrainee(11L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo(88L);
        assertThat(result.get(0).status()).isEqualTo("ACTIVE");
    }

    @Test
    void assignCurriculumBlockedWhenActiveAssignmentExists() {
        CurriculumEntity curriculum = new CurriculumEntity();
        curriculum.setId(22L);
        curriculum.setName("Published Curriculum");
        curriculum.setStatus("PUBLISHED");
        when(userDao.countTraineeByMentor(5L, 11L)).thenReturn(1L);
        when(curriculumDao.selectByIdAndCreator(22L, 5L)).thenReturn(Optional.of(curriculum));
        when(assignmentDao.countActiveByTrainee(11L)).thenReturn(1L);

        assertThatThrownBy(() -> assignmentService.assignCurriculum(5L, 11L, 22L))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void replaceActiveAssignmentSuccessWhenCurrentActiveExists() {
        CurriculumEntity curriculum = new CurriculumEntity();
        curriculum.setId(22L);
        curriculum.setName("Published Curriculum");
        curriculum.setStatus("PUBLISHED");
        TraineeCurriculumAssignmentEntity active = new TraineeCurriculumAssignmentEntity();
        active.setId(88L);
        active.setStatus("ACTIVE");
        when(userDao.countTraineeByMentor(5L, 11L)).thenReturn(1L);
        when(curriculumDao.selectByIdAndCreator(22L, 5L)).thenReturn(Optional.of(curriculum));
        when(assignmentDao.selectActiveByTrainee(11L)).thenReturn(Optional.of(active));
        when(assignmentDao.update(active)).thenReturn(1);
        when(taskTemplateDao.listByCurriculumId(22L)).thenReturn(List.of());
        doAnswer(invocation -> {
            TraineeCurriculumAssignmentEntity arg = invocation.getArgument(0);
            if (arg.getId() == null) {
                arg.setId(101L);
            }
            return 1;
        }).when(assignmentDao).insert(org.mockito.ArgumentMatchers.any(TraineeCurriculumAssignmentEntity.class));
        when(assignmentDao.selectAssignmentProjectionByIdAndTrainee(101L, 11L)).thenReturn(Optional.empty());

        var result = assignmentService.replaceActiveAssignment(5L, 11L, 22L);

        assertThat(result.traineeId()).isEqualTo(11L);
        assertThat(result.curriculumId()).isEqualTo(22L);
        assertThat(result.status()).isEqualTo("ACTIVE");
    }

    @Test
    void replaceActiveAssignmentBlockedWhenNoActiveAssignment() {
        CurriculumEntity curriculum = new CurriculumEntity();
        curriculum.setId(22L);
        curriculum.setName("Published Curriculum");
        curriculum.setStatus("PUBLISHED");
        when(userDao.countTraineeByMentor(5L, 11L)).thenReturn(1L);
        when(curriculumDao.selectByIdAndCreator(22L, 5L)).thenReturn(Optional.of(curriculum));
        when(assignmentDao.selectActiveByTrainee(11L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> assignmentService.replaceActiveAssignment(5L, 11L, 22L))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void replaceActiveAssignmentBlockedWhenCurriculumInvalid() {
        when(userDao.countTraineeByMentor(5L, 11L)).thenReturn(1L);
        when(curriculumDao.selectByIdAndCreator(22L, 5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> assignmentService.replaceActiveAssignment(5L, 11L, 22L))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void loadMaterialBlockedWhenNotOwnedByActiveAssignment() {
        when(learningMaterialDao.selectAccessibleByTraineeAndMaterialId(11L, 9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> assignmentService.loadMaterialForTrainee(11L, 9L))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void loadMaterialReturnsNotFoundWhenPhysicalFileMissing() {
        LearningMaterialEntity material = new LearningMaterialEntity();
        material.setId(9L);
        material.setFileName("lesson.pdf");
        material.setStoragePath("seed/demo/lesson.pdf");
        when(learningMaterialDao.selectAccessibleByTraineeAndMaterialId(11L, 9L)).thenReturn(Optional.of(material));

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

        LearningMaterialEntity material = new LearningMaterialEntity();
        material.setId(9L);
        material.setFileName("lesson.pdf");
        material.setStoragePath(relative.toString().replace('\\', '/'));
        when(learningMaterialDao.selectAccessibleByTraineeAndMaterialId(11L, 9L)).thenReturn(Optional.of(material));

        var result = assignmentService.loadMaterialForTrainee(11L, 9L);
        assertThat(result.id()).isEqualTo(9L);
        assertThat(result.fileName()).isEqualTo("lesson.pdf");
    }

    @Test
    void updateTaskStatusBlockedWhenTransitionIsInvalid() throws Exception {
        TaskEntity task = new TaskEntity();
        task.setId(200L);
        task.setAssignmentId(100L);
        task.setStatus("DONE");
        when(assignmentDao.countByIdAndTrainee(100L, 11L)).thenReturn(1L);
        when(taskDao.selectByAssignmentAndTaskId(100L, 200L)).thenReturn(Optional.of(task));

        assertThatThrownBy(() -> assignmentService.updateTaskStatus(11L, 100L, 200L, "NOT_STARTED"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void updateTaskStatusSuccessWhenTransitionIsValid() {
        TaskEntity task = new TaskEntity();
        task.setId(200L);
        task.setAssignmentId(100L);
        task.setStatus("NOT_STARTED");
        AssignmentTaskProjection projection = new AssignmentTaskProjection();
        projection.setId(200L);
        projection.setAssignmentId(100L);
        projection.setTaskTemplateId(12L);
        projection.setSortOrder(1);
        projection.setTitle("Task A");
        projection.setDescription("Desc");
        projection.setEstimatedDays(3);
        projection.setStatus("IN_PROGRESS");
        when(assignmentDao.countByIdAndTrainee(100L, 11L)).thenReturn(1L);
        when(taskDao.selectByAssignmentAndTaskId(100L, 200L)).thenReturn(Optional.of(task));
        when(taskDao.listAssignmentTasks(100L)).thenReturn(List.of(projection));

        AssignmentTaskResponse result = assignmentService.updateTaskStatus(11L, 100L, 200L, "IN_PROGRESS");

        assertThat(result.id()).isEqualTo(200L);
        assertThat(result.status()).isEqualTo("IN_PROGRESS");
    }

    @Test
    void assignCurriculumBlockedWhenTraineeNotOwnedByMentor() {
        when(userDao.countTraineeByMentor(5L, 11L)).thenReturn(0L);

        assertThatThrownBy(() -> assignmentService.assignCurriculum(5L, 11L, 22L))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void assignCurriculumBlockedWhenCurriculumNotPublished() {
        CurriculumEntity curriculum = new CurriculumEntity();
        curriculum.setId(22L);
        curriculum.setName("Draft Curriculum");
        curriculum.setStatus("DRAFT");
        when(userDao.countTraineeByMentor(5L, 11L)).thenReturn(1L);
        when(curriculumDao.selectByIdAndCreator(22L, 5L)).thenReturn(Optional.of(curriculum));

        assertThatThrownBy(() -> assignmentService.assignCurriculum(5L, 11L, 22L))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void replaceActiveAssignmentBlockedWhenCancelUpdateReturnsZero() {
        CurriculumEntity curriculum = new CurriculumEntity();
        curriculum.setId(22L);
        curriculum.setName("Published Curriculum");
        curriculum.setStatus("PUBLISHED");
        TraineeCurriculumAssignmentEntity active = new TraineeCurriculumAssignmentEntity();
        active.setId(88L);
        active.setStatus("ACTIVE");
        when(userDao.countTraineeByMentor(5L, 11L)).thenReturn(1L);
        when(curriculumDao.selectByIdAndCreator(22L, 5L)).thenReturn(Optional.of(curriculum));
        when(assignmentDao.selectActiveByTrainee(11L)).thenReturn(Optional.of(active));
        when(assignmentDao.update(active)).thenReturn(0);

        assertThatThrownBy(() -> assignmentService.replaceActiveAssignment(5L, 11L, 22L))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void assignCurriculumGeneratesTasksFromTemplates() {
        CurriculumEntity curriculum = new CurriculumEntity();
        curriculum.setId(22L);
        curriculum.setName("Published Curriculum");
        curriculum.setStatus("PUBLISHED");
        TaskTemplateEntity t1 = new TaskTemplateEntity();
        t1.setId(1L);
        t1.setTitle("Task 1");
        t1.setDescription("Desc 1");
        t1.setEstimatedDays(2);
        TaskTemplateEntity t2 = new TaskTemplateEntity();
        t2.setId(2L);
        t2.setTitle("Task 2");
        t2.setDescription("Desc 2");
        t2.setEstimatedDays(3);

        when(userDao.countTraineeByMentor(5L, 11L)).thenReturn(1L);
        when(curriculumDao.selectByIdAndCreator(22L, 5L)).thenReturn(Optional.of(curriculum));
        when(assignmentDao.countActiveByTrainee(11L)).thenReturn(0L);
        when(taskTemplateDao.listByCurriculumId(22L)).thenReturn(List.of(t1, t2));
        doAnswer(invocation -> {
            TraineeCurriculumAssignmentEntity arg = invocation.getArgument(0);
            arg.setId(100L);
            return 1;
        }).when(assignmentDao).insert(any(TraineeCurriculumAssignmentEntity.class));
        when(assignmentDao.selectAssignmentProjectionByIdAndTrainee(100L, 11L)).thenReturn(Optional.empty());

        assignmentService.assignCurriculum(5L, 11L, 22L);

        verify(taskDao, times(2)).insert(any(TaskEntity.class));
        verify(taskDao).insert(argThat(task ->
                task.getAssignmentId().equals(100L)
                        && task.getTaskTemplateId().equals(1L)
                        && "Task 1".equals(task.getTitle())
                        && "NOT_STARTED".equals(task.getStatus())
        ));
        verify(taskDao).insert(argThat(task ->
                task.getAssignmentId().equals(100L)
                        && task.getTaskTemplateId().equals(2L)
                        && "Task 2".equals(task.getTitle())
                        && "NOT_STARTED".equals(task.getStatus())
        ));
    }

    @Test
    void getActiveAssignmentBlockedWhenMissing() {
        when(assignmentDao.selectActiveAssignmentProjectionByTrainee(11L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> assignmentService.getActiveAssignment(11L))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void getAssignmentTasksBlockedWhenAssignmentDoesNotBelongToTrainee() {
        when(assignmentDao.countByIdAndTrainee(100L, 11L)).thenReturn(0L);

        assertThatThrownBy(() -> assignmentService.getAssignmentTasks(11L, 100L))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void updateTaskStatusBlockedWhenStatusBlank() {
        when(assignmentDao.countByIdAndTrainee(100L, 11L)).thenReturn(1L);

        assertThatThrownBy(() -> assignmentService.updateTaskStatus(11L, 100L, 200L, "   "))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void updateTaskStatusTreatsLowercaseAsValidStatus() {
        TaskEntity task = new TaskEntity();
        task.setId(200L);
        task.setAssignmentId(100L);
        task.setStatus("NOT_STARTED");
        AssignmentTaskProjection projection = new AssignmentTaskProjection();
        projection.setId(200L);
        projection.setAssignmentId(100L);
        projection.setTaskTemplateId(12L);
        projection.setSortOrder(1);
        projection.setTitle("Task A");
        projection.setStatus("IN_PROGRESS");
        when(assignmentDao.countByIdAndTrainee(100L, 11L)).thenReturn(1L);
        when(taskDao.selectByAssignmentAndTaskId(100L, 200L)).thenReturn(Optional.of(task));
        when(taskDao.listAssignmentTasks(100L)).thenReturn(List.of(projection));

        AssignmentTaskResponse result = assignmentService.updateTaskStatus(11L, 100L, 200L, "in_progress");

        assertThat(result.status()).isEqualTo("IN_PROGRESS");
    }

    @Test
    void updateTaskStatusSkipsUpdateWhenStatusUnchanged() {
        TaskEntity task = new TaskEntity();
        task.setId(200L);
        task.setAssignmentId(100L);
        task.setStatus("IN_PROGRESS");
        AssignmentTaskProjection projection = new AssignmentTaskProjection();
        projection.setId(200L);
        projection.setAssignmentId(100L);
        projection.setTaskTemplateId(12L);
        projection.setSortOrder(1);
        projection.setTitle("Task A");
        projection.setStatus("IN_PROGRESS");
        when(assignmentDao.countByIdAndTrainee(100L, 11L)).thenReturn(1L);
        when(taskDao.selectByAssignmentAndTaskId(100L, 200L)).thenReturn(Optional.of(task));
        when(taskDao.listAssignmentTasks(100L)).thenReturn(List.of(projection));

        assignmentService.updateTaskStatus(11L, 100L, 200L, "IN_PROGRESS");

        verify(taskDao, never()).update(any(TaskEntity.class));
    }

    @Test
    void updateTaskStatusDoneToInProgressClearsCompletedAtAndKeepsStartedAt() {
        LocalDateTime startedAt = LocalDateTime.now().minusHours(3);
        LocalDateTime completedAt = LocalDateTime.now().minusMinutes(30);
        TaskEntity task = new TaskEntity();
        task.setId(200L);
        task.setAssignmentId(100L);
        task.setStatus("DONE");
        task.setStartedAt(startedAt);
        task.setCompletedAt(completedAt);
        AssignmentTaskProjection projection = new AssignmentTaskProjection();
        projection.setId(200L);
        projection.setAssignmentId(100L);
        projection.setTaskTemplateId(12L);
        projection.setSortOrder(1);
        projection.setTitle("Task A");
        projection.setStatus("IN_PROGRESS");
        when(assignmentDao.countByIdAndTrainee(100L, 11L)).thenReturn(1L);
        when(taskDao.selectByAssignmentAndTaskId(100L, 200L)).thenReturn(Optional.of(task));
        when(taskDao.listAssignmentTasks(100L)).thenReturn(List.of(projection));

        assignmentService.updateTaskStatus(11L, 100L, 200L, "IN_PROGRESS");

        verify(taskDao).update(argThat(updated ->
                "IN_PROGRESS".equals(updated.getStatus())
                        && startedAt.equals(updated.getStartedAt())
                        && updated.getCompletedAt() == null
        ));
    }

    @Test
    void loadMaterialBlockedWhenStoragePathEscapesRoot() throws Exception {
        Path outsideDir = tempDir.getParent().resolve("outside-materials");
        Files.createDirectories(outsideDir);
        Files.writeString(outsideDir.resolve("lesson.pdf"), "%PDF-1.4 test");

        LearningMaterialEntity material = new LearningMaterialEntity();
        material.setId(9L);
        material.setFileName("lesson.pdf");
        material.setStoragePath("../outside-materials/lesson.pdf");
        when(learningMaterialDao.selectAccessibleByTraineeAndMaterialId(11L, 9L)).thenReturn(Optional.of(material));

        assertThatThrownBy(() -> assignmentService.loadMaterialForTrainee(11L, 9L))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }
}
