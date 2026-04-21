package com.example.training_platform.reporting;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import com.example.training_platform.dao.AssignmentDao;
import com.example.training_platform.dao.DailyReportDao;
import com.example.training_platform.dao.DailyReportResourceDao;
import com.example.training_platform.dao.DailyReportTaskHourDao;
import com.example.training_platform.dao.TaskDao;
import com.example.training_platform.dao.UserDao;
import com.example.training_platform.dao.WeeklyPerformanceSummaryDao;
import com.example.training_platform.entity.DailyReportEntity;
import com.example.training_platform.entity.DailyReportResourceEntity;
import com.example.training_platform.entity.DailyReportTaskHourEntity;
import com.example.training_platform.entity.WeeklyPerformanceSummaryEntity;
import com.example.training_platform.reporting.dto.DailyReportResponse;
import com.example.training_platform.reporting.dto.DailyReportResourceInputRequest;
import com.example.training_platform.reporting.dto.DailyReportResourceResponse;
import com.example.training_platform.reporting.dto.DailyReportTaskHourInputRequest;
import com.example.training_platform.reporting.dto.DailyReportTaskHourResponse;
import com.example.training_platform.reporting.dto.ReviewWeeklySummaryRequest;
import com.example.training_platform.reporting.dto.SaveDailyReportRequest;
import com.example.training_platform.reporting.dto.WeeklySummaryGenerationPlaceholderResponse;
import com.example.training_platform.reporting.dto.WeeklySummaryResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ReportingService {

    private static final String DAILY_DRAFT = "DRAFT";
    private static final String DAILY_SUBMITTED = "SUBMITTED";
    private static final String WEEKLY_PENDING = "PENDING";
    private static final String WEEKLY_REVIEWED = "REVIEWED";

    private final AssignmentDao assignmentDao;
    private final UserDao userDao;
    private final TaskDao taskDao;
    private final DailyReportDao dailyReportDao;
    private final DailyReportResourceDao dailyReportResourceDao;
    private final DailyReportTaskHourDao dailyReportTaskHourDao;
    private final WeeklyPerformanceSummaryDao weeklyPerformanceSummaryDao;

    public ReportingService(
            AssignmentDao assignmentDao,
            UserDao userDao,
            TaskDao taskDao,
            DailyReportDao dailyReportDao,
            DailyReportResourceDao dailyReportResourceDao,
            DailyReportTaskHourDao dailyReportTaskHourDao,
            WeeklyPerformanceSummaryDao weeklyPerformanceSummaryDao
    ) {
        this.assignmentDao = assignmentDao;
        this.userDao = userDao;
        this.taskDao = taskDao;
        this.dailyReportDao = dailyReportDao;
        this.dailyReportResourceDao = dailyReportResourceDao;
        this.dailyReportTaskHourDao = dailyReportTaskHourDao;
        this.weeklyPerformanceSummaryDao = weeklyPerformanceSummaryDao;
    }

    public List<DailyReportResponse> listDailyReportsByWeek(Long traineeId, Long assignmentId, LocalDate weekStart) {
        assertAssignmentBelongsToTrainee(traineeId, assignmentId);
        LocalDate normalizedWeekStart = normalizeWeekStart(weekStart);
        LocalDate weekEnd = normalizedWeekStart.plusDays(6);
        return dailyReportDao.listByAssignmentAndDateRange(assignmentId, normalizedWeekStart, weekEnd)
                .stream()
                .map(this::mapDailyReport)
                .toList();
    }

    public Optional<DailyReportResponse> getDailyReport(Long traineeId, Long assignmentId, LocalDate reportDate) {
        assertAssignmentBelongsToTrainee(traineeId, assignmentId);
        return dailyReportDao.selectByAssignmentAndReportDate(assignmentId, reportDate)
                .map(this::mapDailyReport);
    }

    @Transactional
    public DailyReportResponse saveDailyReportDraft(
            Long traineeId,
            Long assignmentId,
            LocalDate reportDate,
            SaveDailyReportRequest request
    ) {
        assertAssignmentBelongsToTrainee(traineeId, assignmentId);
        ensureWeekEditable(assignmentId, reportDate);
        DailyReportEntity report = upsertDailyReport(assignmentId, reportDate, request, DAILY_DRAFT);
        replaceResources(report.getId(), request.resources());
        replaceTaskHours(report.getId(), assignmentId, request.taskHours());
        return mapDailyReport(report);
    }

    @Transactional
    public DailyReportResponse submitDailyReport(
            Long traineeId,
            Long assignmentId,
            LocalDate reportDate,
            SaveDailyReportRequest request
    ) {
        assertAssignmentBelongsToTrainee(traineeId, assignmentId);
        ensureWeekEditable(assignmentId, reportDate);
        DailyReportEntity report = upsertDailyReport(assignmentId, reportDate, request, DAILY_SUBMITTED);
        replaceResources(report.getId(), request.resources());
        replaceTaskHours(report.getId(), assignmentId, request.taskHours());
        return mapDailyReport(report);
    }

    public List<WeeklySummaryResponse> listWeeklySummariesForTrainee(Long traineeId, Long assignmentId) {
        assertAssignmentBelongsToTrainee(traineeId, assignmentId);
        return weeklyPerformanceSummaryDao.listByAssignment(assignmentId).stream().map(this::mapWeeklySummary).toList();
    }

    public List<WeeklySummaryResponse> listWeeklySummariesForMentor(Long mentorId, Long traineeId, Long assignmentId) {
        assertMentorCanAccessAssignment(mentorId, traineeId, assignmentId);
        return weeklyPerformanceSummaryDao.listByAssignment(assignmentId).stream().map(this::mapWeeklySummary).toList();
    }

    public List<DailyReportResponse> listDailyReportsForMentor(
            Long mentorId,
            Long traineeId,
            Long assignmentId,
            LocalDate fromDate,
            LocalDate toDate
    ) {
        assertMentorCanAccessAssignment(mentorId, traineeId, assignmentId);
        if (fromDate.isAfter(toDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "fromDate must be on or before toDate");
        }
        return dailyReportDao.listByAssignmentAndDateRange(assignmentId, fromDate, toDate)
                .stream()
                .map(this::mapDailyReport)
                .toList();
    }

    @Transactional
    public WeeklySummaryResponse reviewWeeklySummary(
            Long mentorId,
            Long traineeId,
            Long assignmentId,
            LocalDate weekStart,
            ReviewWeeklySummaryRequest request
    ) {
        assertMentorCanAccessAssignment(mentorId, traineeId, assignmentId);
        LocalDate normalizedWeekStart = normalizeWeekStart(weekStart);
        WeeklyPerformanceSummaryEntity summary = weeklyPerformanceSummaryDao
                .selectByAssignmentAndWeekStart(assignmentId, normalizedWeekStart)
                .orElseGet(() -> createWeeklySummarySkeleton(assignmentId, normalizedWeekStart));

        boolean isNew = summary.getId() == null;
        summary.setReviewStatus(WEEKLY_REVIEWED);
        summary.setMentorGrade(request.mentorGrade());
        summary.setMentorFeedback(request.mentorFeedback().trim());
        summary.setReviewedAt(LocalDateTime.now());
        if (Boolean.TRUE.equals(request.finalizeWeek())) {
            summary.setFinalizedAt(LocalDateTime.now());
        }

        if (isNew) {
            weeklyPerformanceSummaryDao.insert(summary);
        } else {
            weeklyPerformanceSummaryDao.update(summary);
        }
        return mapWeeklySummary(summary);
    }

    public WeeklySummaryGenerationPlaceholderResponse generateWeeklySummaryPlaceholder(
            Long mentorId,
            Long traineeId,
            Long assignmentId
    ) {
        assertMentorCanAccessAssignment(mentorId, traineeId, assignmentId);
        return new WeeklySummaryGenerationPlaceholderResponse(
                "COMING_SOON",
                "Tinh nang se duoc cap nhat trong thoi gian toi."
        );
    }

    private WeeklyPerformanceSummaryEntity createWeeklySummarySkeleton(Long assignmentId, LocalDate weekStart) {
        WeeklyPerformanceSummaryEntity summary = new WeeklyPerformanceSummaryEntity();
        summary.setAssignmentId(assignmentId);
        summary.setWeekStart(weekStart);
        summary.setWeekEnd(weekStart.plusDays(6));
        summary.setSummaryText("Weekly auto-generation is coming soon.");
        summary.setReviewStatus(WEEKLY_PENDING);
        return summary;
    }

    private DailyReportEntity upsertDailyReport(
            Long assignmentId,
            LocalDate reportDate,
            SaveDailyReportRequest request,
            String targetStatus
    ) {
        DailyReportEntity report = dailyReportDao.selectByAssignmentAndReportDate(assignmentId, reportDate)
                .orElseGet(() -> {
                    DailyReportEntity entity = new DailyReportEntity();
                    entity.setAssignmentId(assignmentId);
                    entity.setReportDate(reportDate);
                    entity.setStatus(DAILY_DRAFT);
                    return entity;
                });

        report.setFresherLabel(request.fresherLabel().trim());
        report.setTrainingDayIndex(request.trainingDayIndex());
        report.setWhatDone(request.whatDone().trim());
        report.setPlannedTomorrow(request.plannedTomorrow().trim());
        report.setBlockers(request.blockers().trim());

        if (DAILY_SUBMITTED.equals(targetStatus)) {
            report.setStatus(DAILY_SUBMITTED);
            report.setSubmittedAt(LocalDateTime.now());
        } else {
            report.setStatus(DAILY_DRAFT);
            report.setSubmittedAt(null);
        }

        if (report.getId() == null) {
            dailyReportDao.insert(report);
        } else {
            dailyReportDao.update(report);
        }
        return report;
    }

    private void replaceTaskHours(Long dailyReportId, Long assignmentId, List<DailyReportTaskHourInputRequest> inputs) {
        List<DailyReportTaskHourInputRequest> safeInputs = inputs == null ? List.of() : inputs;
        List<DailyReportTaskHourEntity> existing = dailyReportTaskHourDao.listByDailyReportId(dailyReportId);
        if (!existing.isEmpty()) {
            dailyReportTaskHourDao.batchDelete(existing);
        }
        for (DailyReportTaskHourInputRequest input : safeInputs) {
            if (taskDao.selectByAssignmentAndTaskId(assignmentId, input.taskId()).isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Task does not belong to assignment");
            }
            DailyReportTaskHourEntity entity = new DailyReportTaskHourEntity();
            entity.setDailyReportId(dailyReportId);
            entity.setTaskId(input.taskId());
            entity.setHours(input.hours());
            entity.setNotes(trimToNull(input.notes()));
            dailyReportTaskHourDao.insert(entity);
        }
    }

    private void replaceResources(Long dailyReportId, List<DailyReportResourceInputRequest> resources) {
        List<DailyReportResourceInputRequest> safeResources = resources == null ? List.of() : resources;
        Set<String> seenTypes = new java.util.HashSet<>();
        for (DailyReportResourceInputRequest input : safeResources) {
            String normalizedType = normalizeResourceType(input.type());
            if (!seenTypes.add(normalizedType)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Duplicate resource type in daily report");
            }
        }

        List<DailyReportResourceEntity> existing = dailyReportResourceDao.listByDailyReportId(dailyReportId);
        if (!existing.isEmpty()) {
            dailyReportResourceDao.batchDelete(existing);
        }

        for (DailyReportResourceInputRequest input : safeResources) {
            DailyReportResourceEntity entity = new DailyReportResourceEntity();
            entity.setDailyReportId(dailyReportId);
            entity.setResourceType(normalizeResourceType(input.type()));
            entity.setResourceLabel(trimToNull(input.label()));
            entity.setResourceUrl(input.url().trim());
            dailyReportResourceDao.insert(entity);
        }
    }

    private DailyReportResponse mapDailyReport(DailyReportEntity report) {
        List<DailyReportResourceResponse> resources = dailyReportResourceDao.listByDailyReportId(report.getId())
                .stream()
                .map(item -> new DailyReportResourceResponse(
                        item.getId(),
                        item.getResourceType(),
                        item.getResourceLabel(),
                        item.getResourceUrl()
                ))
                .toList();
        List<DailyReportTaskHourResponse> taskHours = dailyReportTaskHourDao.listByDailyReportId(report.getId())
                .stream()
                .map(item -> new DailyReportTaskHourResponse(
                        item.getId(),
                        item.getTaskId(),
                        item.getHours(),
                        item.getNotes()
                ))
                .toList();
        return new DailyReportResponse(
                report.getId(),
                report.getAssignmentId(),
                report.getReportDate(),
                report.getStatus(),
                report.getFresherLabel(),
                report.getTrainingDayIndex(),
                report.getWhatDone(),
                report.getPlannedTomorrow(),
                report.getBlockers(),
                resources,
                report.getSubmittedAt(),
                report.getReviewedAt(),
                taskHours
        );
    }

    private WeeklySummaryResponse mapWeeklySummary(WeeklyPerformanceSummaryEntity summary) {
        return new WeeklySummaryResponse(
                summary.getId(),
                summary.getAssignmentId(),
                summary.getWeekStart(),
                summary.getWeekEnd(),
                summary.getSummaryText(),
                summary.getCompletionRate(),
                summary.getAverageDailyHours(),
                summary.getReviewStatus(),
                summary.getMentorFeedback(),
                summary.getMentorGrade(),
                summary.getReviewedAt(),
                summary.getFinalizedAt(),
                summary.getGeneratedAt()
        );
    }

    private void ensureWeekEditable(Long assignmentId, LocalDate reportDate) {
        LocalDate weekStart = normalizeWeekStart(reportDate);
        boolean locked = weeklyPerformanceSummaryDao.selectByAssignmentAndWeekStart(assignmentId, weekStart)
                .map(summary -> summary.getFinalizedAt() != null)
                .orElse(false);
        if (locked) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "This week is locked and daily reports can no longer be edited"
            );
        }
    }

    private void assertAssignmentBelongsToTrainee(Long traineeId, Long assignmentId) {
        if (assignmentDao.countByIdAndTrainee(assignmentId, traineeId) == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Assignment not found");
        }
    }

    private void assertMentorCanAccessAssignment(Long mentorId, Long traineeId, Long assignmentId) {
        if (userDao.countTraineeByMentor(mentorId, traineeId) == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Trainee not found");
        }
        assertAssignmentBelongsToTrainee(traineeId, assignmentId);
    }

    private static LocalDate normalizeWeekStart(LocalDate inputDate) {
        return inputDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    private static String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String normalizeResourceType(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "resource type is required");
        }
        return value.toUpperCase(Locale.ROOT);
    }
}
