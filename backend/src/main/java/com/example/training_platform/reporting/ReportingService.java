package com.example.training_platform.reporting;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;

import com.example.training_platform.dao.AssignmentDao;
import com.example.training_platform.dao.projection.AssignmentProjection;
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
    private static final Pattern NEWLINE_SPLITTER = Pattern.compile("\\R");

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

    public List<DailyReportResponse> listDailyReportsForTrainee(
            Long traineeId,
            Long assignmentId,
            LocalDate fromDate,
            LocalDate toDate
    ) {
        if (assignmentId != null) {
            assertAssignmentBelongsToTrainee(traineeId, assignmentId);
        }
        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "fromDate must be on or before toDate");
        }
        return dailyReportDao.listByTraineeWithFilters(traineeId, assignmentId, fromDate, toDate)
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
        assertAssignmentActiveForTraineeWrites(traineeId, assignmentId);
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
        assertAssignmentActiveForTraineeWrites(traineeId, assignmentId);
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
    public WeeklySummaryResponse generateWeeklySummaryForMentor(
            Long mentorId,
            Long traineeId,
            Long assignmentId,
            LocalDate weekStart
    ) {
        assertMentorCanAccessAssignment(mentorId, traineeId, assignmentId);
        return generateWeeklySummaryForAssignment(assignmentId, weekStart);
    }

    @Transactional
    public WeeklySummaryResponse generateWeeklySummaryForAssignment(Long assignmentId, LocalDate weekStart) {
        LocalDate normalizedWeekStart = normalizeWeekStart(weekStart);
        LocalDate weekEnd = normalizedWeekStart.plusDays(6);
        List<DailyReportEntity> reports = dailyReportDao.listByAssignmentAndDateRange(assignmentId, normalizedWeekStart, weekEnd);

        int reportCount = reports.size();
        long submittedCount = reports.stream().filter(r -> DAILY_SUBMITTED.equals(r.getStatus())).count();
        double completionRate = reportCount == 0 ? 0.0 : round2((double) submittedCount / reportCount);
        double averageDailyHours = computeAverageDailyHours(reports, reportCount);
        String summaryText = buildWeeklySummaryText(normalizedWeekStart, weekEnd, reports);

        WeeklyPerformanceSummaryEntity summary = weeklyPerformanceSummaryDao
                .selectByAssignmentAndWeekStart(assignmentId, normalizedWeekStart)
                .orElseGet(() -> {
                    WeeklyPerformanceSummaryEntity entity = new WeeklyPerformanceSummaryEntity();
                    entity.setAssignmentId(assignmentId);
                    entity.setWeekStart(normalizedWeekStart);
                    entity.setWeekEnd(weekEnd);
                    entity.setReviewStatus(WEEKLY_PENDING);
                    return entity;
                });
        boolean isNew = summary.getId() == null;

        summary.setWeekEnd(weekEnd);
        summary.setSummaryText(summaryText);
        summary.setCompletionRate(completionRate);
        summary.setAverageDailyHours(averageDailyHours);
        if (summary.getReviewStatus() == null || summary.getReviewStatus().isBlank()) {
            summary.setReviewStatus(WEEKLY_PENDING);
        }

        if (isNew) {
            weeklyPerformanceSummaryDao.insert(summary);
        } else {
            weeklyPerformanceSummaryDao.update(summary);
        }
        return mapWeeklySummary(summary);
    }

    @Transactional
    public void generatePreviousWeekForAllActiveAssignments(LocalDate today) {
        LocalDate weekStart = normalizeWeekStart(today).minusWeeks(1);
        for (Long assignmentId : assignmentDao.listActiveAssignmentIds()) {
            generateWeeklySummaryForAssignment(assignmentId, weekStart);
        }
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

    private String buildWeeklySummaryText(LocalDate weekStart, LocalDate weekEnd, List<DailyReportEntity> reports) {
        List<String> workLines = mergeWeeklyTextLines(reports, DailyReportEntity::getWhatDone);
        List<String> blockerLines = mergeWeeklyTextLines(reports, DailyReportEntity::getBlockers);
        StringBuilder sb = new StringBuilder();
        sb.append("Weekly summary (%s – %s)\n\n".formatted(weekStart, weekEnd));
        sb.append("What was accomplished:\n");
        if (workLines.isEmpty()) {
            sb.append("No work logged for this week.\n");
        } else {
            for (String line : workLines) {
                sb.append("- ").append(line).append('\n');
            }
        }
        sb.append("\nDifficulties / blockers:\n");
        if (blockerLines.isEmpty()) {
            sb.append("No difficulties noted for this week.\n");
        } else {
            for (String line : blockerLines) {
                sb.append("- ").append(line).append('\n');
            }
        }
        return sb.toString().trim();
    }

    private static List<String> mergeWeeklyTextLines(List<DailyReportEntity> reports, Function<DailyReportEntity, String> field) {
        List<DailyReportEntity> sorted = reports.stream()
                .sorted(Comparator.comparing(DailyReportEntity::getReportDate, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        LinkedHashSet<String> ordered = new LinkedHashSet<>();
        for (DailyReportEntity report : sorted) {
            String trimmed = trimToNull(field.apply(report));
            if (trimmed == null) {
                continue;
            }
            trimmed.lines().map(String::trim).filter(s -> !s.isEmpty()).forEach(ordered::add);
        }
        return List.copyOf(ordered);
    }

    private double computeAverageDailyHours(List<DailyReportEntity> reports, int reportCount) {
        if (reportCount == 0) return 0.0;
        double totalHours = reports.stream()
                .mapToDouble(report -> dailyReportTaskHourDao.listByDailyReportId(report.getId()).stream()
                        .mapToDouble(item -> item.getHours() == null ? 0.0 : item.getHours())
                        .sum())
                .sum();
        return round2(totalHours / reportCount);
    }

    private static double round2(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
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
        List<DailyReportEntity> reports = dailyReportDao.listByAssignmentAndDateRange(
                summary.getAssignmentId(),
                summary.getWeekStart(),
                summary.getWeekEnd()
        );
        List<String> accomplishments = mergeWeeklyTextLines(reports, DailyReportEntity::getWhatDone);
        List<String> difficulties = mergeWeeklyTextLines(reports, DailyReportEntity::getBlockers);
        if (accomplishments.isEmpty() && difficulties.isEmpty()) {
            ParsedSummarySections parsed = parseSummaryTextSections(summary.getSummaryText());
            accomplishments = parsed.accomplishments();
            difficulties = parsed.difficulties();
        }
        return new WeeklySummaryResponse(
                summary.getId(),
                summary.getAssignmentId(),
                summary.getWeekStart(),
                summary.getWeekEnd(),
                accomplishments,
                difficulties,
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

    private static ParsedSummarySections parseSummaryTextSections(String summaryText) {
        String body = trimToNull(summaryText);
        if (body == null) return new ParsedSummarySections(List.of(), List.of());
        String[] rawLines = NEWLINE_SPLITTER.split(body);
        LinkedHashSet<String> accomplishments = new LinkedHashSet<>();
        LinkedHashSet<String> difficulties = new LinkedHashSet<>();
        Section current = Section.NONE;
        for (String raw : rawLines) {
            String line = raw == null ? "" : raw.trim();
            if (line.isEmpty()) {
                continue;
            }
            if ("What was accomplished:".equalsIgnoreCase(line)) {
                current = Section.ACCOMPLISHMENTS;
                continue;
            }
            if ("Difficulties / blockers:".equalsIgnoreCase(line)) {
                current = Section.DIFFICULTIES;
                continue;
            }
            if (current == Section.NONE) {
                continue;
            }
            String value = line.startsWith("-") ? trimToNull(line.substring(1)) : trimToNull(line);
            if (value == null) {
                continue;
            }
            if (current == Section.ACCOMPLISHMENTS) {
                accomplishments.add(value);
            } else {
                difficulties.add(value);
            }
        }
        return new ParsedSummarySections(List.copyOf(accomplishments), List.copyOf(difficulties));
    }

    private enum Section {
        NONE,
        ACCOMPLISHMENTS,
        DIFFICULTIES
    }

    private record ParsedSummarySections(List<String> accomplishments, List<String> difficulties) {}

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

    private void assertAssignmentActiveForTraineeWrites(Long traineeId, Long assignmentId) {
        AssignmentProjection row = assignmentDao.selectAssignmentProjectionByIdAndTrainee(assignmentId, traineeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Assignment not found"));
        if (!"ACTIVE".equals(row.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "This curriculum assignment is no longer active; daily reports are read only."
            );
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
