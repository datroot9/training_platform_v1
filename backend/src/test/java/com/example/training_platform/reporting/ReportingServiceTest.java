package com.example.training_platform.reporting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import com.example.training_platform.dao.AssignmentDao;
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
    void saveDailyReportDraft_throwsConflictWhenWeekFinalized() {
        stubTraineeOwnsAssignment();
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
        stubTraineeOwnsAssignment();
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
        stubTraineeOwnsAssignment();
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
        stubTraineeOwnsAssignment();
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
        stubTraineeOwnsAssignment();
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
