package com.example.training_platform.assignment;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

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
import com.example.training_platform.curriculum.LocalPdfStorageService;
import com.example.training_platform.entity.CurriculumEntity;
import com.example.training_platform.entity.LearningMaterialEntity;
import com.example.training_platform.entity.TaskEntity;
import com.example.training_platform.entity.TaskTemplateEntity;
import com.example.training_platform.entity.TraineeCurriculumAssignmentEntity;
import org.seasar.doma.jdbc.UniqueConstraintException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AssignmentService {

    private final UserDao userDao;
    private final CurriculumDao curriculumDao;
    private final AssignmentDao assignmentDao;
    private final TaskTemplateDao taskTemplateDao;
    private final TaskDao taskDao;
    private final LearningMaterialDao learningMaterialDao;
    private final LocalPdfStorageService storageService;

    public AssignmentService(
            UserDao userDao,
            CurriculumDao curriculumDao,
            AssignmentDao assignmentDao,
            TaskTemplateDao taskTemplateDao,
            TaskDao taskDao,
            LearningMaterialDao learningMaterialDao,
            LocalPdfStorageService storageService
    ) {
        this.userDao = userDao;
        this.curriculumDao = curriculumDao;
        this.assignmentDao = assignmentDao;
        this.taskTemplateDao = taskTemplateDao;
        this.taskDao = taskDao;
        this.learningMaterialDao = learningMaterialDao;
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

    private AssignmentResponse createActiveAssignment(
            Long mentorId,
            Long traineeId,
            Long curriculumId,
            String curriculumName
    ) {

        try {
            TraineeCurriculumAssignmentEntity assignment = new TraineeCurriculumAssignmentEntity();
            assignment.setTraineeId(traineeId);
            assignment.setCurriculumId(curriculumId);
            assignment.setAssignedBy(mentorId);
            assignment.setStatus("ACTIVE");
            assignmentDao.insert(assignment);
            List<TaskTemplateEntity> templates = taskTemplateDao.listByCurriculumId(curriculumId);
            for (TaskTemplateEntity template : templates) {
                TaskEntity task = new TaskEntity();
                task.setAssignmentId(assignment.getId());
                task.setTaskTemplateId(template.getId());
                task.setTitle(template.getTitle());
                task.setDescription(template.getDescription());
                task.setEstimatedDays(template.getEstimatedDays());
                task.setStatus("NOT_STARTED");
                taskDao.insert(task);
            }
            return loadAssignmentById(traineeId, assignment.getId(), templates.size())
                    .orElseGet(() -> new AssignmentResponse(
                            assignment.getId(),
                            traineeId,
                            curriculumId,
                            curriculumName,
                            null,
                            null,
                            null,
                            null,
                            "ACTIVE",
                            LocalDateTime.now(),
                            null,
                            templates.size()
                    ));
        } catch (UniqueConstraintException ex) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Trainee already has an ACTIVE curriculum assignment"
            );
        }
    }

    private void cancelActiveAssignment(Long traineeId) {
        TraineeCurriculumAssignmentEntity active = assignmentDao.selectActiveByTrainee(traineeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No active assignment to replace"));
        active.setStatus("CANCELLED");
        active.setEndedAt(LocalDateTime.now());
        int updated = assignmentDao.update(active);
        if (updated < 1) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No active assignment to replace");
        }
    }

    public AssignmentResponse getActiveAssignment(Long traineeId) {
        AssignmentProjection row = assignmentDao.selectActiveAssignmentProjectionByTrainee(traineeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No active assignment found"));
        return mapAssignment(row);
    }

    public List<AssignmentTaskResponse> getAssignmentTasks(Long traineeId, Long assignmentId) {
        assertAssignmentBelongsToTrainee(traineeId, assignmentId);
        return taskDao.listAssignmentTasks(assignmentId).stream().map(this::mapAssignmentTask).toList();
    }

    @Transactional
    public AssignmentTaskResponse updateTaskStatus(Long traineeId, Long assignmentId, Long taskId, String targetStatusRaw) {
        assertAssignmentBelongsToTrainee(traineeId, assignmentId);
        String targetStatus = normalizeTaskStatus(targetStatusRaw);
        TaskEntity current = taskDao.selectByAssignmentAndTaskId(assignmentId, taskId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found"));
        if (current.getStatus().equals(targetStatus)) {
            return loadAssignmentTask(assignmentId, taskId);
        }
        if (!isTaskStatusTransitionAllowed(current.getStatus(), targetStatus)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid task status transition"
            );
        }
        current.setStatus(targetStatus);
        current.setStartedAt(resolveStartedAt(current.getStartedAt(), targetStatus));
        current.setCompletedAt(resolveCompletedAt(targetStatus));
        taskDao.update(current);

        return loadAssignmentTask(assignmentId, taskId);
    }

    private AssignmentTaskResponse loadAssignmentTask(Long assignmentId, Long taskId) {
        return taskDao.listAssignmentTasks(assignmentId).stream()
                .filter(task -> task.getId().equals(taskId))
                .findFirst()
                .map(this::mapAssignmentTask)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found"));
    }

    public DownloadableMaterial loadMaterialForTrainee(Long traineeId, Long materialId) {
        LearningMaterialEntity materialEntity = learningMaterialDao.selectAccessibleByTraineeAndMaterialId(traineeId, materialId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Material not found"));
        DownloadableMaterial material = new DownloadableMaterial(
                materialEntity.getId(),
                materialEntity.getFileName(),
                materialEntity.getStoragePath()
        );
        Path absolutePath = storageService.root().resolve(material.storagePath()).normalize();
        if (!absolutePath.startsWith(storageService.root()) || !Files.exists(absolutePath)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Material file not found");
        }
        return material;
    }

    private void assertTraineeBelongsToMentor(Long mentorId, Long traineeId) {
        if (userDao.countTraineeByMentor(mentorId, traineeId) == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Trainee not found");
        }
    }

    private String assertPublishedCurriculumOwnedByMentor(Long mentorId, Long curriculumId) {
        CurriculumEntity curriculum = curriculumDao.selectByIdAndCreator(curriculumId, mentorId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Curriculum must be PUBLISHED and owned by mentor"));
        if (!"PUBLISHED".equals(curriculum.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Curriculum must be PUBLISHED and owned by mentor");
        }
        return curriculum.getName();
    }

    private void assertNoActiveAssignment(Long traineeId) {
        if (assignmentDao.countActiveByTrainee(traineeId) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Trainee already has an ACTIVE curriculum assignment");
        }
    }

    private void assertAssignmentBelongsToTrainee(Long traineeId, Long assignmentId) {
        if (assignmentDao.countByIdAndTrainee(assignmentId, traineeId) == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Assignment not found");
        }
    }

    private static String normalizeTaskStatus(String rawStatus) {
        if (rawStatus == null || rawStatus.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "status is required");
        }
        String normalized = rawStatus.trim().toUpperCase(Locale.ROOT);
        if (!"NOT_STARTED".equals(normalized) && !"IN_PROGRESS".equals(normalized) && !"DONE".equals(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "status must be NOT_STARTED, IN_PROGRESS, or DONE");
        }
        return normalized;
    }

    private static boolean isTaskStatusTransitionAllowed(String current, String target) {
        return switch (current) {
            case "NOT_STARTED" -> "IN_PROGRESS".equals(target);
            case "IN_PROGRESS" -> "NOT_STARTED".equals(target) || "DONE".equals(target);
            case "DONE" -> "IN_PROGRESS".equals(target);
            default -> false;
        };
    }

    private static LocalDateTime resolveStartedAt(LocalDateTime currentStartedAt, String targetStatus) {
        return switch (targetStatus) {
            case "NOT_STARTED" -> null;
            case "IN_PROGRESS", "DONE" -> currentStartedAt == null ? LocalDateTime.now() : currentStartedAt;
            default -> currentStartedAt;
        };
    }

    private static LocalDateTime resolveCompletedAt(String targetStatus) {
        return switch (targetStatus) {
            case "DONE" -> LocalDateTime.now();
            case "NOT_STARTED", "IN_PROGRESS" -> null;
            default -> null;
        };
    }

    private java.util.Optional<AssignmentResponse> loadAssignmentById(Long traineeId, Long assignmentId, int generatedTaskCount) {
        return assignmentDao.selectAssignmentProjectionByIdAndTrainee(assignmentId, traineeId)
                .map(this::mapAssignment)
                .map(r -> new AssignmentResponse(
                        r.id(),
                        r.traineeId(),
                        r.curriculumId(),
                        r.curriculumName(),
                        r.curriculumDescription(),
                        r.mentorName(),
                        r.mentorEmail(),
                        r.totalEstimatedDays(),
                        r.status(),
                        r.assignedAt(),
                        r.endedAt(),
                        generatedTaskCount
                ));
    }

    private AssignmentResponse mapAssignment(AssignmentProjection row) {
        long taskCount = taskDao.countByAssignmentId(row.getId());
        Integer totalEstimatedDays = taskDao.sumEstimatedDaysByAssignmentId(row.getId());
        return new AssignmentResponse(
                row.getId(),
                row.getTraineeId(),
                row.getCurriculumId(),
                row.getCurriculumName(),
                row.getCurriculumDescription(),
                row.getMentorName(),
                row.getMentorEmail(),
                totalEstimatedDays,
                row.getStatus(),
                row.getAssignedAt(),
                row.getEndedAt(),
                (int) taskCount
        );
    }

    private AssignmentTaskResponse mapAssignmentTask(AssignmentTaskProjection row) {
        return new AssignmentTaskResponse(
                row.getId(),
                row.getAssignmentId(),
                row.getTaskTemplateId(),
                row.getSortOrder(),
                row.getTitle(),
                row.getDescription(),
                row.getEstimatedDays(),
                row.getStatus(),
                row.getStartedAt(),
                row.getCompletedAt(),
                row.getCreatedAt(),
                row.getUpdatedAt(),
                row.getLearningMaterialId(),
                row.getLearningMaterialFileName()
        );
    }

    public record DownloadableMaterial(Long id, String fileName, String storagePath) {
    }
}
