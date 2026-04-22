package com.example.training_platform.assignment;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;

import com.example.training_platform.assignment.dto.AssignmentResponse;
import com.example.training_platform.assignment.dto.AssignmentTaskResponse;
import com.example.training_platform.auth.AuthenticatedUser;
import com.example.training_platform.auth.JwtService;
import com.example.training_platform.common.ApiExceptionHandler;
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

@WebMvcTest(MentorAssignmentController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(ApiExceptionHandler.class)
class MentorAssignmentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AssignmentService assignmentService;

    @MockBean
    private JwtService jwtService;

    @Test
    void getTraineeActiveAssignmentReturnsSuccessEnvelope() throws Exception {
        AssignmentResponse response = sampleAssignmentResponse(77L, 11L, 22L);
        when(assignmentService.getActiveAssignmentForMentor(5L, 11L)).thenReturn(response);

        mockMvc.perform(get("/api/mentor/trainees/11/assignments/active")
                        .principal(auth(5L, "mentor@local", "MENTOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("Active assignment fetched"))
                .andExpect(jsonPath("$.data.id").value(77))
                .andExpect(jsonPath("$.data.traineeId").value(11))
                .andExpect(jsonPath("$.data.curriculumId").value(22));

        verify(assignmentService).getActiveAssignmentForMentor(5L, 11L);
    }

    @Test
    void getTraineeAssignmentTasksReturnsSuccessEnvelope() throws Exception {
        AssignmentTaskResponse task = new AssignmentTaskResponse(
                200L,
                77L,
                33L,
                1,
                "Task title",
                "Task desc",
                2,
                "IN_PROGRESS",
                null,
                null,
                null,
                null,
                null,
                null
        );
        when(assignmentService.getAssignmentTasksForMentor(5L, 11L, 77L)).thenReturn(List.of(task));

        mockMvc.perform(get("/api/mentor/trainees/11/assignments/77/tasks")
                        .principal(auth(5L, "mentor@local", "MENTOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("Assignment tasks fetched"))
                .andExpect(jsonPath("$.data[0].id").value(200))
                .andExpect(jsonPath("$.data[0].status").value("IN_PROGRESS"));

        verify(assignmentService).getAssignmentTasksForMentor(5L, 11L, 77L);
    }

    @Test
    void assignCurriculumReturnsCreatedEnvelope() throws Exception {
        AssignmentResponse response = sampleAssignmentResponse(88L, 11L, 22L);
        when(assignmentService.assignCurriculum(5L, 11L, 22L)).thenReturn(response);

        mockMvc.perform(post("/api/mentor/trainees/11/assignments")
                        .principal(auth(5L, "mentor@local", "MENTOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"curriculumId\":22}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("Curriculum assigned successfully"))
                .andExpect(jsonPath("$.data.id").value(88));

        verify(assignmentService).assignCurriculum(5L, 11L, 22L);
    }

    @Test
    void replaceActiveAssignmentReturnsOkEnvelope() throws Exception {
        AssignmentResponse response = sampleAssignmentResponse(99L, 11L, 33L);
        when(assignmentService.replaceActiveAssignment(5L, 11L, 33L)).thenReturn(response);

        mockMvc.perform(put("/api/mentor/trainees/11/assignments/active")
                        .principal(auth(5L, "mentor@local", "MENTOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"curriculumId\":33}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("Active assignment replaced successfully"))
                .andExpect(jsonPath("$.data.curriculumId").value(33));

        verify(assignmentService).replaceActiveAssignment(5L, 11L, 33L);
    }

    @Test
    void assignCurriculumReturnsBadRequestWhenCurriculumIdMissing() throws Exception {
        mockMvc.perform(post("/api/mentor/trainees/11/assignments")
                        .principal(auth(5L, "mentor@local", "MENTOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));

        verifyNoInteractions(assignmentService);
    }

    private static Authentication auth(Long userId, String email, String role) {
        return new UsernamePasswordAuthenticationToken(new AuthenticatedUser(userId, email, role), null, List.of());
    }

    private static AssignmentResponse sampleAssignmentResponse(Long id, Long traineeId, Long curriculumId) {
        return new AssignmentResponse(
                id,
                traineeId,
                curriculumId,
                "Curriculum",
                "Desc",
                "1.0",
                "Mentor",
                "mentor@local",
                10,
                "ACTIVE",
                LocalDateTime.now(),
                null,
                3
        );
    }
}
