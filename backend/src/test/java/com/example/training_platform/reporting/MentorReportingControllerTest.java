package com.example.training_platform.reporting;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

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

@WebMvcTest(MentorReportingController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(ApiExceptionHandler.class)
class MentorReportingControllerTest {

    private static final long TRAINEE_ID = 11L;
    private static final long ASSIGNMENT_ID = 77L;

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ReportingService reportingService;

    @MockBean
    private JwtService jwtService;

    @Test
    void listWeeklySummariesReturnsEnvelope() throws Exception {
        WeeklySummaryResponse summary = new WeeklySummaryResponse(
                1L,
                ASSIGNMENT_ID,
                LocalDate.of(2025, 1, 6),
                LocalDate.of(2025, 1, 12),
                List.of("Done A"),
                List.of("Blocked A"),
                "t",
                null,
                null,
                "REVIEWED",
                "good",
                9.0,
                LocalDateTime.now(),
                null,
                null
        );
        when(reportingService.listWeeklySummariesForMentor(5L, TRAINEE_ID, ASSIGNMENT_ID))
                .thenReturn(List.of(summary));

        mockMvc.perform(get("/api/mentor/trainees/{tid}/assignments/{aid}/weekly-summaries", TRAINEE_ID, ASSIGNMENT_ID)
                        .principal(auth(5L, "m@local", "MENTOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("Weekly summaries fetched"))
                .andExpect(jsonPath("$.data[0].mentorGrade").value(9.0));

        verify(reportingService).listWeeklySummariesForMentor(5L, TRAINEE_ID, ASSIGNMENT_ID);
    }

    @Test
    void listDailyReportsReturnsEnvelope() throws Exception {
        LocalDate from = LocalDate.of(2025, 1, 1);
        LocalDate to = LocalDate.of(2025, 1, 7);
        DailyReportResponse row = new DailyReportResponse(
                3L,
                ASSIGNMENT_ID,
                from,
                "SUBMITTED",
                "F",
                1,
                "a",
                "b",
                "c",
                List.of(),
                LocalDateTime.now(),
                null,
                List.of()
        );
        when(reportingService.listDailyReportsForMentor(5L, TRAINEE_ID, ASSIGNMENT_ID, from, to))
                .thenReturn(List.of(row));

        mockMvc.perform(get("/api/mentor/trainees/{tid}/assignments/{aid}/daily-reports", TRAINEE_ID, ASSIGNMENT_ID)
                        .param("fromDate", "2025-01-01")
                        .param("toDate", "2025-01-07")
                        .principal(auth(5L, "m@local", "MENTOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].status").value("SUBMITTED"));

        verify(reportingService).listDailyReportsForMentor(5L, TRAINEE_ID, ASSIGNMENT_ID, from, to);
    }

    @Test
    void reviewWeeklySummaryReturnsEnvelope() throws Exception {
        WeeklySummaryResponse reviewed = new WeeklySummaryResponse(
                4L,
                ASSIGNMENT_ID,
                LocalDate.of(2025, 1, 6),
                LocalDate.of(2025, 1, 12),
                List.of("Done B"),
                List.of(),
                "t",
                null,
                null,
                "REVIEWED",
                "nice",
                8.0,
                LocalDateTime.now(),
                null,
                null
        );
        when(reportingService.reviewWeeklySummary(
                eq(5L),
                eq(TRAINEE_ID),
                eq(ASSIGNMENT_ID),
                eq(LocalDate.of(2025, 1, 6)),
                any()
        )).thenReturn(reviewed);

        mockMvc.perform(put("/api/mentor/trainees/{tid}/assignments/{aid}/weekly-summaries/{ws}/review",
                        TRAINEE_ID, ASSIGNMENT_ID, "2025-01-06")
                        .principal(auth(5L, "m@local", "MENTOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mentorGrade\":8,\"mentorFeedback\":\"nice\",\"finalizeWeek\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("Weekly summary reviewed"))
                .andExpect(jsonPath("$.data.id").value(4));

        verify(reportingService).reviewWeeklySummary(
                eq(5L),
                eq(TRAINEE_ID),
                eq(ASSIGNMENT_ID),
                eq(LocalDate.of(2025, 1, 6)),
                any()
        );
    }

    @Test
    void generateWeeklySummaryReturnsEnvelope() throws Exception {
        WeeklySummaryResponse generated = new WeeklySummaryResponse(
                55L,
                ASSIGNMENT_ID,
                LocalDate.of(2025, 1, 6),
                LocalDate.of(2025, 1, 12),
                List.of("Done C"),
                List.of("Blocked C"),
                "Weekly summary",
                0.8,
                2.5,
                "PENDING",
                null,
                null,
                null,
                null,
                LocalDateTime.now()
        );
        when(reportingService.generateWeeklySummaryForMentor(
                eq(5L),
                eq(TRAINEE_ID),
                eq(ASSIGNMENT_ID),
                eq(LocalDate.of(2025, 1, 6))
        )).thenReturn(generated);

        mockMvc.perform(post("/api/mentor/trainees/{tid}/assignments/{aid}/weekly-summaries/generate",
                        TRAINEE_ID, ASSIGNMENT_ID)
                        .param("weekStart", "2025-01-06")
                        .principal(auth(5L, "m@local", "MENTOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("Weekly summary generated"))
                .andExpect(jsonPath("$.data.id").value(55));

        verify(reportingService).generateWeeklySummaryForMentor(
                5L,
                TRAINEE_ID,
                ASSIGNMENT_ID,
                LocalDate.of(2025, 1, 6)
        );
    }

    private static Authentication auth(Long userId, String email, String role) {
        return new UsernamePasswordAuthenticationToken(new AuthenticatedUser(userId, email, role), null, List.of());
    }
}
