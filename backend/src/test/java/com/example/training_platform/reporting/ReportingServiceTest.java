package com.example.training_platform.reporting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import com.example.training_platform.dao.AssignmentDao;
import com.example.training_platform.dao.projection.AssignmentProjection;
import com.example.training_platform.dao.DailyReportDao;
import com.example.training_platform.dao.DailyReportResourceDao;
import com.example.training_platform.dao.DailyReportTaskHourDao;
import com.example.training_platform.dao.TaskDao;
import com.example.training_platform.dao.UserDao;
import com.example.training_platform.dao.WeeklyPerformanceSummaryDao;
import com.example.training_platform.entity.DailyReportEntity;
import com.example.training_platform.entity.WeeklyPerformanceSummaryEntity;
import com.example.training_platform.reporting.dto.DailyReportResourceInputRequest;
import com.example.training_platform.reporting.dto.DailyReportTaskHourInputRequest;
import com.example.training_platform.reporting.dto.ReviewWeeklySummaryRequest;
import com.example.training_platform.reporting.dto.SaveDailyReportRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class ReportingServiceTest {

    private static final long TRAINEE_ID = 10L;
    private static final long MENTOR_ID = 5L;
    private static final long ASSIGNMENT_ID = 100L;

    @Mock
    private AssignmentDao assignmentDao;
    @Mock
    private UserDao userDao;
    @Mock
    private TaskDao taskDao;
    @Mock
    private DailyReportDao dailyReportDao;
    @Mock
    private DailyReportResourceDao dailyReportResourceDao;
    @Mock
    private DailyReportTaskHourDao dailyReportTaskHourDao;
    @Mock
    private WeeklyPerformanceSummaryDao weeklyPerformanceSummaryDao;

    private ReportingService reportingService;

    @BeforeEach
    void setUp() {
        reportingService = new ReportingService(
                assignmentDao,
                userDao,
                taskDao,
                dailyReportDao,
                dailyReportResourceDao,
                dailyReportTaskHourDao,
                weeklyPerformanceSummaryDao
        );
    }

    @Test
    void listDailyReportsByWeek_throwsWhenAssignmentNotOwned() {
        when(assignmentDao.countByIdAndTrainee(ASSIGNMENT_ID, TRAINEE_ID)).thenReturn(0L);

        assertThatThrownBy(() -> reportingService.listDailyReportsByWeek(TRAINEE_ID, ASSIGNMENT_ID, LocalDate.of(2025, 1, 6)))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode().value()).isEqualTo(HttpStatus.NOT_FOUND.value()));

        verify(dailyReportDao, never()).listByAssignmentAndDateRange(anyLong(), any(), any());
    }

    @Test
    void listDailyReportsByWeek_normalizesWeekStartToMonday() {
        stubTraineeOwnsAssignment();
        LocalDate wednesday = LocalDate.of(2025, 1, 8);
        LocalDate monday = LocalDate.of(2025, 1, 6);
        when(dailyReportDao.listByAssignmentAndDateRange(eq(ASSIGNMENT_ID), eq(monday), eq(monday.plusDays(6))))
                .thenReturn(List.of());

        reportingService.listDailyReportsByWeek(TRAINEE_ID, ASSIGNMENT_ID, wednesday);

        verify(dailyReportDao).listByAssignmentAndDateRange(ASSIGNMENT_ID, monday, monday.plusDays(6));
    }

    @Test
    void listDailyReportsForTrainee_rejectsInvertedDateRange() {
        assertThatThrownBy(() -> reportingService.listDailyReportsForTrainee(
                TRAINEE_ID,
                null,
                LocalDate.of(2025, 2, 10),
                LocalDate.of(2025, 2, 1)
        ))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode().value()).isEqualTo(HttpStatus.BAD_REQUEST.value()));
    }

    @Test
    void listDailyReportsForTrainee_filtersByAssignmentAndDate() {
        stubTraineeOwnsAssignment();
        DailyReportEntity row = new DailyReportEntity();
        row.setId(900L);
        row.setAssignmentId(ASSIGNMENT_ID);
        row.setReportDate(LocalDate.of(2025, 1, 9));
        row.setStatus("SUBMITTED");
        row.setFresherLabel("F");
        row.setTrainingDayIndex(4);
        row.setWhatDone("done");
        row.setPlannedTomorrow("next");
        row.setBlockers("none");
        row.setSubmittedAt(LocalDateTime.now());

        when(dailyReportDao.listByTraineeWithFilters(
                TRAINEE_ID,
                ASSIGNMENT_ID,
                LocalDate.of(2025, 1, 1),
                LocalDate.of(2025, 1, 31)
        )).thenReturn(List.of(row));
        when(dailyReportResourceDao.listByDailyReportId(900L)).thenReturn(List.of());
        when(dailyReportTaskHourDao.listByDailyReportId(900L)).thenReturn(List.of());

        var result = reportingService.listDailyReportsForTrainee(
                TRAINEE_ID,
                ASSIGNMENT_ID,
                LocalDate.of(2025, 1, 1),
                LocalDate.of(2025, 1, 31)
        );

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo(900L);
        verify(dailyReportDao).listByTraineeWithFilters(
                TRAINEE_ID,
                ASSIGNMENT_ID,
                LocalDate.of(2025, 1, 1),
                LocalDate.of(2025, 1, 31)
        );
    }

    @Test
    void saveDailyReportDraft_throwsConflictWhenAssignmentNotActive() {
        when(assignmentDao.countByIdAndTrainee(ASSIGNMENT_ID, TRAINEE_ID)).thenReturn(1L);
        AssignmentProjection cancelled = new AssignmentProjection();
        cancelled.setStatus("CANCELLED");
        when(assignmentDao.selectAssignmentProjectionByIdAndTrainee(ASSIGNMENT_ID, TRAINEE_ID)).thenReturn(Optional.of(cancelled));

        assertThatThrownBy(() -> reportingService.saveDailyReportDraft(
                TRAINEE_ID,
                ASSIGNMENT_ID,
                LocalDate.of(2025, 1, 8),
                sampleSaveRequest()
        ))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode().value()).isEqualTo(HttpStatus.CONFLICT.value()));

        verify(dailyReportDao, never()).insert(any(DailyReportEntity.class));
    }

    @Test
    void saveDailyReportDraft_throwsConflictWhenWeekFinalized() {
        stubTraineeOwnsActiveAssignment();
        LocalDate reportDate = LocalDate.of(2025, 1, 8);
        LocalDate weekStart = LocalDate.of(2025, 1, 6);
        WeeklyPerformanceSummaryEntity locked = new WeeklyPerformanceSummaryEntity();
        locked.setFinalizedAt(LocalDateTime.now());
        when(weeklyPerformanceSummaryDao.selectByAssignmentAndWeekStart(ASSIGNMENT_ID, weekStart))
                .thenReturn(Optional.of(locked));

        assertThatThrownBy(() -> reportingService.saveDailyReportDraft(
                TRAINEE_ID,
                ASSIGNMENT_ID,
                reportDate,
                sampleSaveRequest()
        ))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode().value()).isEqualTo(HttpStatus.CONFLICT.value()));
    }

    @Test
    void saveDailyReportDraft_insertsNewReportAndReturnsMappedResponse() {
        stubTraineeOwnsActiveAssignment();
        LocalDate reportDate = LocalDate.of(2025, 1, 8);
        LocalDate weekStart = LocalDate.of(2025, 1, 6);
        when(weeklyPerformanceSummaryDao.selectByAssignmentAndWeekStart(ASSIGNMENT_ID, weekStart))
                .thenReturn(Optional.empty());

        when(dailyReportDao.selectByAssignmentAndReportDate(ASSIGNMENT_ID, reportDate)).thenReturn(Optional.empty());
        doAnswer(invocation -> {
            DailyReportEntity e = invocation.getArgument(0);
            e.setId(500L);
            return null;
        }).when(dailyReportDao).insert(any(DailyReportEntity.class));

        when(dailyReportResourceDao.listByDailyReportId(500L)).thenReturn(List.of());
        when(dailyReportTaskHourDao.listByDailyReportId(500L)).thenReturn(List.of());

        var response = reportingService.saveDailyReportDraft(
                TRAINEE_ID,
                ASSIGNMENT_ID,
                reportDate,
                sampleSaveRequest()
        );

        assertThat(response.id()).isEqualTo(500L);
        assertThat(response.status()).isEqualTo("DRAFT");
        verify(dailyReportDao).insert(any(DailyReportEntity.class));
    }

    @Test
    void saveDailyReportDraft_rejectsDuplicateResourceTypes() {
        stubTraineeOwnsActiveAssignment();
        stubWeekOpen();
        when(dailyReportDao.selectByAssignmentAndReportDate(ASSIGNMENT_ID, LocalDate.of(2025, 1, 8)))
                .thenReturn(Optional.empty());
        doAnswer(invocation -> {
            DailyReportEntity e = invocation.getArgument(0);
            e.setId(1L);
            return null;
        }).when(dailyReportDao).insert(any(DailyReportEntity.class));

        var request = new SaveDailyReportRequest(
                "F",
                1,
                "a",
                "b",
                "c",
                List.of(
                        new DailyReportResourceInputRequest("trello", null, "https://trello.com/x"),
                        new DailyReportResourceInputRequest("TRELLO", null, "https://trello.com/y")
                ),
                List.of()
        );

        assertThatThrownBy(() -> reportingService.saveDailyReportDraft(TRAINEE_ID, ASSIGNMENT_ID, LocalDate.of(2025, 1, 8), request))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode().value()).isEqualTo(HttpStatus.BAD_REQUEST.value()));
    }

    @Test
    void saveDailyReportDraft_rejectsBlankResourceType() {
        stubTraineeOwnsActiveAssignment();
        stubWeekOpen();
        when(dailyReportDao.selectByAssignmentAndReportDate(ASSIGNMENT_ID, LocalDate.of(2025, 1, 8)))
                .thenReturn(Optional.empty());
        doAnswer(invocation -> {
            DailyReportEntity e = invocation.getArgument(0);
            e.setId(1L);
            return null;
        }).when(dailyReportDao).insert(any(DailyReportEntity.class));

        var request = new SaveDailyReportRequest(
                "F",
                1,
                "a",
                "b",
                "c",
                List.of(new DailyReportResourceInputRequest("   ", null, "https://x.com")),
                List.of()
        );

        assertThatThrownBy(() -> reportingService.saveDailyReportDraft(TRAINEE_ID, ASSIGNMENT_ID, LocalDate.of(2025, 1, 8), request))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode().value()).isEqualTo(HttpStatus.BAD_REQUEST.value()));
    }

    @Test
    void saveDailyReportDraft_rejectsTaskNotInAssignment() {
        stubTraineeOwnsActiveAssignment();
        stubWeekOpen();
        when(dailyReportDao.selectByAssignmentAndReportDate(ASSIGNMENT_ID, LocalDate.of(2025, 1, 8)))
                .thenReturn(Optional.empty());
        doAnswer(invocation -> {
            DailyReportEntity e = invocation.getArgument(0);
            e.setId(1L);
            return null;
        }).when(dailyReportDao).insert(any(DailyReportEntity.class));
        when(dailyReportResourceDao.listByDailyReportId(1L)).thenReturn(List.of());
        when(dailyReportTaskHourDao.listByDailyReportId(1L)).thenReturn(List.of());
        when(taskDao.selectByAssignmentAndTaskId(ASSIGNMENT_ID, 999L)).thenReturn(Optional.empty());

        var request = new SaveDailyReportRequest(
                "F",
                1,
                "a",
                "b",
                "c",
                List.of(),
                List.of(new DailyReportTaskHourInputRequest(999L, 1.0, null))
        );

        assertThatThrownBy(() -> reportingService.saveDailyReportDraft(TRAINEE_ID, ASSIGNMENT_ID, LocalDate.of(2025, 1, 8), request))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode().value()).isEqualTo(HttpStatus.BAD_REQUEST.value()));
    }

    @Test
    void listDailyReportsForMentor_rejectsInvertedDateRange() {
        stubMentorAccess();
        assertThatThrownBy(() -> reportingService.listDailyReportsForMentor(
                MENTOR_ID,
                TRAINEE_ID,
                ASSIGNMENT_ID,
                LocalDate.of(2025, 2, 10),
                LocalDate.of(2025, 2, 1)
        ))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode().value()).isEqualTo(HttpStatus.BAD_REQUEST.value()));
    }

    @Test
    void listDailyReportsForMentor_throwsWhenTraineeNotLinkedToMentor() {
        when(userDao.countTraineeByMentor(MENTOR_ID, TRAINEE_ID)).thenReturn(0L);

        assertThatThrownBy(() -> reportingService.listDailyReportsForMentor(
                MENTOR_ID,
                TRAINEE_ID,
                ASSIGNMENT_ID,
                LocalDate.of(2025, 1, 1),
                LocalDate.of(2025, 1, 7)
        ))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode().value()).isEqualTo(HttpStatus.NOT_FOUND.value()));
    }

    @Test
    void generateWeeklySummaryForAssignment_createsNewSummaryWhenMissing() {
        LocalDate weekStart = LocalDate.of(2025, 1, 6);
        DailyReportEntity report = new DailyReportEntity();
        report.setId(10L);
        report.setAssignmentId(ASSIGNMENT_ID);
        report.setReportDate(LocalDate.of(2025, 1, 8));
        report.setStatus("SUBMITTED");
        report.setBlockers("Need review");
        when(dailyReportDao.listByAssignmentAndDateRange(ASSIGNMENT_ID, weekStart, weekStart.plusDays(6)))
                .thenReturn(List.of(report));
        when(dailyReportTaskHourDao.listByDailyReportId(10L)).thenReturn(List.of());
        when(weeklyPerformanceSummaryDao.selectByAssignmentAndWeekStart(ASSIGNMENT_ID, weekStart)).thenReturn(Optional.empty());
        doAnswer(invocation -> {
            WeeklyPerformanceSummaryEntity entity = invocation.getArgument(0);
            entity.setId(500L);
            return null;
        }).when(weeklyPerformanceSummaryDao).insert(any(WeeklyPerformanceSummaryEntity.class));

        var generated = reportingService.generateWeeklySummaryForAssignment(ASSIGNMENT_ID, weekStart);

        assertThat(generated.id()).isEqualTo(500L);
        assertThat(generated.assignmentId()).isEqualTo(ASSIGNMENT_ID);
        assertThat(generated.reviewStatus()).isEqualTo("PENDING");
        assertThat(generated.summaryText())
                .contains("What was accomplished:", "No work logged for this week.")
                .contains("Difficulties / blockers:", "- Need review")
                .doesNotContain("Submission rate", "Total daily reports");
        assertThat(generated.accomplishments()).isEmpty();
        assertThat(generated.difficulties()).containsExactly("Need review");
        verify(weeklyPerformanceSummaryDao).insert(any(WeeklyPerformanceSummaryEntity.class));
    }

    @Test
    void generateWeeklySummaryForAssignment_mergesWhatDoneLinesSortedByDateAndDedupes() {
        LocalDate weekStart = LocalDate.of(2025, 1, 6);
        DailyReportEntity later = new DailyReportEntity();
        later.setId(20L);
        later.setAssignmentId(ASSIGNMENT_ID);
        later.setReportDate(LocalDate.of(2025, 1, 9));
        later.setStatus("SUBMITTED");
        later.setWhatDone("Shared task");
        later.setBlockers(null);
        DailyReportEntity earlier = new DailyReportEntity();
        earlier.setId(21L);
        earlier.setAssignmentId(ASSIGNMENT_ID);
        earlier.setReportDate(LocalDate.of(2025, 1, 7));
        earlier.setStatus("SUBMITTED");
        earlier.setWhatDone("First line\nShared task\nSecond line");
        earlier.setBlockers("Env issue");
        when(dailyReportDao.listByAssignmentAndDateRange(ASSIGNMENT_ID, weekStart, weekStart.plusDays(6)))
                .thenReturn(List.of(later, earlier));
        when(dailyReportTaskHourDao.listByDailyReportId(20L)).thenReturn(List.of());
        when(dailyReportTaskHourDao.listByDailyReportId(21L)).thenReturn(List.of());
        when(weeklyPerformanceSummaryDao.selectByAssignmentAndWeekStart(ASSIGNMENT_ID, weekStart)).thenReturn(Optional.empty());
        doAnswer(invocation -> {
            WeeklyPerformanceSummaryEntity entity = invocation.getArgument(0);
            entity.setId(501L);
            return null;
        }).when(weeklyPerformanceSummaryDao).insert(any(WeeklyPerformanceSummaryEntity.class));

        var generated = reportingService.generateWeeklySummaryForAssignment(ASSIGNMENT_ID, weekStart);

        String text = generated.summaryText();
        assertThat(text.indexOf("- First line")).isLessThan(text.indexOf("- Shared task"));
        assertThat(text.indexOf("- Shared task")).isLessThan(text.indexOf("- Second line"));
        assertThat(text).contains("- Env issue").doesNotContain("Submission rate");
        assertThat(generated.accomplishments()).containsExactly("First line", "Shared task", "Second line");
        assertThat(generated.difficulties()).containsExactly("Env issue");
    }

    @Test
    void listWeeklySummariesForTrainee_fallsBackToParsingSummaryTextWhenReportsMissing() {
        stubTraineeOwnsAssignment();
        LocalDate weekStart = LocalDate.of(2025, 1, 6);
        WeeklyPerformanceSummaryEntity existing = new WeeklyPerformanceSummaryEntity();
        existing.setId(88L);
        existing.setAssignmentId(ASSIGNMENT_ID);
        existing.setWeekStart(weekStart);
        existing.setWeekEnd(weekStart.plusDays(6));
        existing.setReviewStatus("PENDING");
        existing.setSummaryText("""
                Weekly summary (2025-01-06 – 2025-01-12)

                What was accomplished:
                - Parsed done one
                - Parsed done two

                Difficulties / blockers:
                - Parsed blocker
                """);
        when(weeklyPerformanceSummaryDao.listByAssignment(ASSIGNMENT_ID)).thenReturn(List.of(existing));
        when(dailyReportDao.listByAssignmentAndDateRange(ASSIGNMENT_ID, weekStart, weekStart.plusDays(6)))
                .thenReturn(List.of());

        var rows = reportingService.listWeeklySummariesForTrainee(TRAINEE_ID, ASSIGNMENT_ID);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).accomplishments()).containsExactly("Parsed done one", "Parsed done two");
        assertThat(rows.get(0).difficulties()).containsExactly("Parsed blocker");
    }

    @Test
    void generateWeeklySummaryForAssignment_updatesExistingSummaryIdempotently() {
        LocalDate weekStart = LocalDate.of(2025, 1, 6);
        DailyReportEntity report = new DailyReportEntity();
        report.setId(11L);
        report.setAssignmentId(ASSIGNMENT_ID);
        report.setReportDate(LocalDate.of(2025, 1, 9));
        report.setStatus("DRAFT");
        when(dailyReportDao.listByAssignmentAndDateRange(ASSIGNMENT_ID, weekStart, weekStart.plusDays(6)))
                .thenReturn(List.of(report));
        when(dailyReportTaskHourDao.listByDailyReportId(11L)).thenReturn(List.of());
        WeeklyPerformanceSummaryEntity existing = new WeeklyPerformanceSummaryEntity();
        existing.setId(77L);
        existing.setAssignmentId(ASSIGNMENT_ID);
        existing.setWeekStart(weekStart);
        existing.setWeekEnd(weekStart.plusDays(6));
        existing.setReviewStatus("REVIEWED");
        when(weeklyPerformanceSummaryDao.selectByAssignmentAndWeekStart(ASSIGNMENT_ID, weekStart))
                .thenReturn(Optional.of(existing));

        var generated = reportingService.generateWeeklySummaryForAssignment(ASSIGNMENT_ID, weekStart);

        assertThat(generated.id()).isEqualTo(77L);
        verify(weeklyPerformanceSummaryDao).update(existing);
        verify(weeklyPerformanceSummaryDao, never()).insert(any(WeeklyPerformanceSummaryEntity.class));
    }

    @Test
    void generatePreviousWeekForAllActiveAssignments_generatesForEachActiveAssignment() {
        LocalDate today = LocalDate.of(2025, 1, 15); // Wednesday
        LocalDate previousMonday = LocalDate.of(2025, 1, 6);
        when(assignmentDao.listActiveAssignmentIds()).thenReturn(List.of(100L, 200L));
        when(dailyReportDao.listByAssignmentAndDateRange(anyLong(), eq(previousMonday), eq(previousMonday.plusDays(6))))
                .thenReturn(List.of());
        when(weeklyPerformanceSummaryDao.selectByAssignmentAndWeekStart(eq(100L), eq(previousMonday))).thenReturn(Optional.empty());
        when(weeklyPerformanceSummaryDao.selectByAssignmentAndWeekStart(eq(200L), eq(previousMonday))).thenReturn(Optional.empty());

        reportingService.generatePreviousWeekForAllActiveAssignments(today);

        verify(assignmentDao).listActiveAssignmentIds();
        verify(weeklyPerformanceSummaryDao, times(2)).insert(any(WeeklyPerformanceSummaryEntity.class));
    }

    @Test
    void reviewWeeklySummary_insertsWhenNoRowExists() {
        stubMentorAccess();
        LocalDate weekStart = LocalDate.of(2025, 1, 6);
        when(weeklyPerformanceSummaryDao.selectByAssignmentAndWeekStart(ASSIGNMENT_ID, weekStart))
                .thenReturn(Optional.empty());
        doAnswer(invocation -> {
            WeeklyPerformanceSummaryEntity e = invocation.getArgument(0);
            e.setId(700L);
            return null;
        }).when(weeklyPerformanceSummaryDao).insert(any(WeeklyPerformanceSummaryEntity.class));

        var result = reportingService.reviewWeeklySummary(
                MENTOR_ID,
                TRAINEE_ID,
                ASSIGNMENT_ID,
                weekStart,
                new ReviewWeeklySummaryRequest(8.5, "  good  ", false)
        );

        assertThat(result.id()).isEqualTo(700L);
        assertThat(result.mentorGrade()).isEqualTo(8.5);
        assertThat(result.mentorFeedback()).isEqualTo("good");
        verify(weeklyPerformanceSummaryDao).insert(any(WeeklyPerformanceSummaryEntity.class));
        verify(weeklyPerformanceSummaryDao, never()).update(any(WeeklyPerformanceSummaryEntity.class));
    }

    @Test
    void reviewWeeklySummary_updatesExistingRow() {
        stubMentorAccess();
        LocalDate weekStart = LocalDate.of(2025, 1, 6);
        WeeklyPerformanceSummaryEntity existing = new WeeklyPerformanceSummaryEntity();
        existing.setId(55L);
        existing.setAssignmentId(ASSIGNMENT_ID);
        existing.setWeekStart(weekStart);
        existing.setWeekEnd(weekStart.plusDays(6));
        when(weeklyPerformanceSummaryDao.selectByAssignmentAndWeekStart(ASSIGNMENT_ID, weekStart))
                .thenReturn(Optional.of(existing));

        reportingService.reviewWeeklySummary(
                MENTOR_ID,
                TRAINEE_ID,
                ASSIGNMENT_ID,
                weekStart,
                new ReviewWeeklySummaryRequest(7.0, "ok", true)
        );

        verify(weeklyPerformanceSummaryDao).update(existing);
        verify(weeklyPerformanceSummaryDao, never()).insert(any(WeeklyPerformanceSummaryEntity.class));
        assertThat(existing.getFinalizedAt()).isNotNull();
    }

    @Test
    void generateWeeklySummaryPlaceholder_returnsComingSoon() {
        stubMentorAccess();
        var placeholder = reportingService.generateWeeklySummaryPlaceholder(MENTOR_ID, TRAINEE_ID, ASSIGNMENT_ID);
        assertThat(placeholder.status()).isEqualTo("COMING_SOON");
    }

    private void stubTraineeOwnsAssignment() {
        when(assignmentDao.countByIdAndTrainee(ASSIGNMENT_ID, TRAINEE_ID)).thenReturn(1L);
    }

    private void stubTraineeOwnsActiveAssignment() {
        stubTraineeOwnsAssignment();
        AssignmentProjection active = new AssignmentProjection();
        active.setStatus("ACTIVE");
        when(assignmentDao.selectAssignmentProjectionByIdAndTrainee(ASSIGNMENT_ID, TRAINEE_ID)).thenReturn(Optional.of(active));
    }

    private void stubWeekOpen() {
        LocalDate weekStart = LocalDate.of(2025, 1, 6);
        when(weeklyPerformanceSummaryDao.selectByAssignmentAndWeekStart(ASSIGNMENT_ID, weekStart))
                .thenReturn(Optional.empty());
    }

    private void stubMentorAccess() {
        when(userDao.countTraineeByMentor(MENTOR_ID, TRAINEE_ID)).thenReturn(1L);
        when(assignmentDao.countByIdAndTrainee(ASSIGNMENT_ID, TRAINEE_ID)).thenReturn(1L);
    }

    private static SaveDailyReportRequest sampleSaveRequest() {
        return new SaveDailyReportRequest("Fresher", 1, "done", "next", "none", List.of(), List.of());
    }
}
