package com.example.training_platform.trainee;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import com.example.training_platform.auth.AuthenticatedUser;
import com.example.training_platform.auth.JwtService;
import com.example.training_platform.common.ApiExceptionHandler;
import com.example.training_platform.common.dto.PagedResponse;
import com.example.training_platform.trainee.dto.CreateTraineeResponse;
import com.example.training_platform.trainee.dto.ResetPasswordResponse;
import com.example.training_platform.trainee.dto.TraineeResponse;
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

@WebMvcTest(TraineeController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(ApiExceptionHandler.class)
class TraineeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TraineeService traineeService;

    @MockBean
    private JwtService jwtService;

    @Test
    void createReturnsEnvelope() throws Exception {
        CreateTraineeResponse body = new CreateTraineeResponse(8L, "n@local", "N", "TRAINEE", "temp", true);
        when(traineeService.create(eq(3L), any())).thenReturn(body);

        mockMvc.perform(post("/api/mentor/trainees")
                        .principal(auth(3L, "m@local", "MENTOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"n@local\",\"fullName\":\"N\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("Trainee created"))
                .andExpect(jsonPath("$.data.userId").value(8));

        verify(traineeService).create(eq(3L), any());
    }

    @Test
    void listReturnsEnvelope() throws Exception {
        TraineeResponse row = new TraineeResponse(
                1L, "t@local", "T", true, 3L, null, null, null, 0, 0
        );
        PagedResponse<TraineeResponse> page = PagedResponse.of(List.of(row), 0, 10, 1);
        when(traineeService.list(3L, null, null, null, null, null, null)).thenReturn(page);

        mockMvc.perform(get("/api/mentor/trainees")
                        .principal(auth(3L, "m@local", "MENTOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].email").value("t@local"));

        verify(traineeService).list(3L, null, null, null, null, null, null);
    }

    @Test
    void updateStatusReturnsEnvelope() throws Exception {
        mockMvc.perform(patch("/api/mentor/trainees/{id}/status", 12L)
                        .principal(auth(3L, "m@local", "MENTOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.active").value(false));

        verify(traineeService).setActive(3L, 12L, false);
    }

    @Test
    void resetPasswordReturnsEnvelope() throws Exception {
        ResetPasswordResponse body = new ResetPasswordResponse(12L, "t@local", "tmp", true);
        when(traineeService.resetPassword(3L, 12L)).thenReturn(body);

        mockMvc.perform(post("/api/mentor/trainees/{id}/reset-password", 12L)
                        .principal(auth(3L, "m@local", "MENTOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.temporaryPassword").value("tmp"));

        verify(traineeService).resetPassword(3L, 12L);
    }

    @Test
    void createReturns400WhenEmailInvalid() throws Exception {
        mockMvc.perform(post("/api/mentor/trainees")
                        .principal(auth(3L, "m@local", "MENTOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"bad\",\"fullName\":\"X\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(traineeService);
    }

    private static Authentication auth(Long userId, String email, String role) {
        return new UsernamePasswordAuthenticationToken(new AuthenticatedUser(userId, email, role), null, List.of());
    }
}
