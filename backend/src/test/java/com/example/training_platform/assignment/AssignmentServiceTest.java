package com.example.training_platform.assignment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

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
}
