package com.example.training_platform.assignment;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import com.example.training_platform.assignment.dto.AssignmentResponse;
import com.example.training_platform.assignment.dto.AssignmentTaskResponse;
import com.example.training_platform.auth.AuthenticatedUser;
import com.example.training_platform.auth.JwtService;
import com.example.training_platform.common.ApiExceptionHandler;
import com.example.training_platform.curriculum.LocalPdfStorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(TraineeAssignmentController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(ApiExceptionHandler.class)
class TraineeAssignmentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AssignmentService assignmentService;

    @MockBean
    private LocalPdfStorageService storageService;

    @MockBean
    private JwtService jwtService;

    @TempDir
    Path tempDir;

    @Test
    void listAssignmentsReturnsSuccessEnvelope() throws Exception {
        AssignmentResponse row = new AssignmentResponse(
                77L,
                11L,
                22L,
                "Curriculum",
                "Desc",
                "1.0",
                "Mentor",
                "mentor@local",
                10,
                "CANCELLED",
                LocalDateTime.now(),
                LocalDateTime.now(),
                2
        );
        when(assignmentService.listAssignmentsForTrainee(11L)).thenReturn(List.of(row));

        mockMvc.perform(get("/api/trainee/assignments").principal(auth(11L, "trainee@local", "TRAINEE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("Assignments fetched"))
                .andExpect(jsonPath("$.data[0].id").value(77))
                .andExpect(jsonPath("$.data[0].status").value("CANCELLED"));

        verify(assignmentService).listAssignmentsForTrainee(11L);
    }

    @Test
    void updateTaskStatusReturnsSuccessEnvelope() throws Exception {
        AssignmentTaskResponse response = new AssignmentTaskResponse(
                200L,
                77L,
                33L,
                1,
                "Task title",
                "Task desc",
                2,
                "IN_PROGRESS",
                LocalDateTime.now(),
                null,
                LocalDateTime.now(),
                LocalDateTime.now(),
                null,
                null
        );
        when(assignmentService.updateTaskStatus(11L, 77L, 200L, "IN_PROGRESS")).thenReturn(response);

        mockMvc.perform(patch("/api/trainee/assignments/77/tasks/200/status")
                        .principal(auth(11L, "trainee@local", "TRAINEE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"IN_PROGRESS\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("Task status updated"))
                .andExpect(jsonPath("$.data.id").value(200))
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"));

        verify(assignmentService).updateTaskStatus(11L, 77L, 200L, "IN_PROGRESS");
    }

    @Test
    void updateTaskStatusReturnsBadRequestWhenStatusInvalid() throws Exception {
        mockMvc.perform(patch("/api/trainee/assignments/77/tasks/200/status")
                        .principal(auth(11L, "trainee@local", "TRAINEE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"in_progress\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("status must be NOT_STARTED, IN_PROGRESS, or DONE"));
    }

    @Test
    void downloadMaterialReturnsPdfWithAttachmentHeaders() throws Exception {
        Path file = tempDir.resolve("seed/demo/lesson.pdf");
        Files.createDirectories(file.getParent());
        byte[] body = "%PDF-1.4 test".getBytes();
        Files.write(file, body);

        when(assignmentService.loadMaterialForTrainee(11L, 9L))
                .thenReturn(new AssignmentService.DownloadableMaterial(9L, "lesson.pdf", "seed/demo/lesson.pdf"));
        when(storageService.root()).thenReturn(tempDir);

        mockMvc.perform(get("/api/trainee/materials/9/download")
                        .principal(auth(11L, "trainee@local", "TRAINEE")))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"lesson.pdf\""))
                .andExpect(content().bytes(body));

        verify(assignmentService).loadMaterialForTrainee(11L, 9L);
    }

    private static Authentication auth(Long userId, String email, String role) {
        return new UsernamePasswordAuthenticationToken(new AuthenticatedUser(userId, email, role), null, List.of());
    }
}
