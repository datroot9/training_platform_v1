package com.example.training_platform.reporting;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import com.example.training_platform.auth.AuthenticatedUser;
import com.example.training_platform.auth.JwtService;
import com.example.training_platform.common.ApiExceptionHandler;
import com.example.training_platform.reporting.dto.DailyReportResponse;
import com.example.training_platform.reporting.dto.WeeklySummaryResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(TraineeReportingController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(ApiExceptionHandler.class)
class TraineeReportingControllerTest {

    private static final long ASSIGNMENT_ID = 77L;

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ReportingService reportingService;

    @MockBean
    private JwtService jwtService;

    @Test
    void listDailyReportsByWeekReturnsEnvelope() throws Exception {
        DailyReportResponse row = new DailyReportResponse(
                1L,
                ASSIGNMENT_ID,
                LocalDate.of(2025, 1, 6),
                "DRAFT",
                "Fresher",
                1,
                "a",
                "b",
                "c",
                List.of(),
                null,
                null,
                List.of()
        );
        when(reportingService.listDailyReportsByWeek(9L, ASSIGNMENT_ID, LocalDate.of(2025, 1, 6)))
                .thenReturn(List.of(row));

        mockMvc.perform(get("/api/trainee/assignments/{id}/daily-reports", ASSIGNMENT_ID)
                        .param("weekStart", "2025-01-06")
                        .principal(auth(9L, "t@local", "TRAINEE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("Daily reports fetched"))
                .andExpect(jsonPath("$.data[0].id").value(1));

        verify(reportingService).listDailyReportsByWeek(9L, ASSIGNMENT_ID, LocalDate.of(2025, 1, 6));
    }

    @Test
    void getDailyReportReturns404WhenMissing() throws Exception {
        when(reportingService.getDailyReport(9L, ASSIGNMENT_ID, LocalDate.of(2025, 1, 7)))
                .thenReturn(Optional.empty());

        mockMvc.perform(get("/api/trainee/assignments/{id}/daily-reports/{date}", ASSIGNMENT_ID, "2025-01-07")
                        .principal(auth(9L, "t@local", "TRAINEE")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    void saveDailyReportDraftReturnsEnvelope() throws Exception {
        DailyReportResponse saved = new DailyReportResponse(
                2L,
                ASSIGNMENT_ID,
                LocalDate.of(2025, 1, 8),
                "DRAFT",
                "Fresher",
                2,
                "x",
                "y",
                "z",
                List.of(),
                null,
                null,
                List.of()
        );
        when(reportingService.saveDailyReportDraft(eq(9L), eq(ASSIGNMENT_ID), eq(LocalDate.of(2025, 1, 8)), any()))
                .thenReturn(saved);

        mockMvc.perform(put("/api/trainee/assignments/{id}/daily-reports/{date}", ASSIGNMENT_ID, "2025-01-08")
                        .principal(auth(9L, "t@local", "TRAINEE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fresherLabel":"F","trainingDayIndex":2,
                                "whatDone":"x","plannedTomorrow":"y","blockers":"z",
                                "resources":[],"taskHours":[]}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("Daily report draft saved"))
                .andExpect(jsonPath("$.data.id").value(2));

        verify(reportingService).saveDailyReportDraft(eq(9L), eq(ASSIGNMENT_ID), eq(LocalDate.of(2025, 1, 8)), any());
    }

    @Test
    void submitDailyReportReturnsEnvelope() throws Exception {
        DailyReportResponse submitted = new DailyReportResponse(
                3L,
                ASSIGNMENT_ID,
                LocalDate.of(2025, 1, 8),
                "SUBMITTED",
                "Fresher",
                2,
                "x",
                "y",
                "z",
                List.of(),
                LocalDateTime.now(),
                null,
                List.of()
        );
        when(reportingService.submitDailyReport(eq(9L), eq(ASSIGNMENT_ID), eq(LocalDate.of(2025, 1, 8)), any()))
                .thenReturn(submitted);

        mockMvc.perform(post("/api/trainee/assignments/{id}/daily-reports/{date}/submit", ASSIGNMENT_ID, "2025-01-08")
                        .principal(auth(9L, "t@local", "TRAINEE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fresherLabel":"F","trainingDayIndex":2,
                                "whatDone":"x","plannedTomorrow":"y","blockers":"z",
                                "resources":[],"taskHours":[]}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("Daily report submitted"));

        verify(reportingService).submitDailyReport(eq(9L), eq(ASSIGNMENT_ID), eq(LocalDate.of(2025, 1, 8)), any());
    }

    @Test
    void listWeeklySummariesReturnsEnvelope() throws Exception {
        WeeklySummaryResponse summary = new WeeklySummaryResponse(
                10L,
                ASSIGNMENT_ID,
                LocalDate.of(2025, 1, 6),
                LocalDate.of(2025, 1, 12),
                "text",
                0.5,
                1.0,
                "PENDING",
                null,
                null,
                null,
                null,
                null
        );
        when(reportingService.listWeeklySummariesForTrainee(9L, ASSIGNMENT_ID)).thenReturn(List.of(summary));

        mockMvc.perform(get("/api/trainee/assignments/{id}/weekly-summaries", ASSIGNMENT_ID)
                        .principal(auth(9L, "t@local", "TRAINEE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].id").value(10));

        verify(reportingService).listWeeklySummariesForTrainee(9L, ASSIGNMENT_ID);
    }

    @Test
    void saveDailyReportDraftReturns400WhenBodyInvalid() throws Exception {
        mockMvc.perform(put("/api/trainee/assignments/{id}/daily-reports/{date}", ASSIGNMENT_ID, "2025-01-08")
                        .principal(auth(9L, "t@local", "TRAINEE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));

        verifyNoInteractions(reportingService);
    }

    private static Authentication auth(Long userId, String email, String role) {
        return new UsernamePasswordAuthenticationToken(new AuthenticatedUser(userId, email, role), null, List.of());
    }
}
