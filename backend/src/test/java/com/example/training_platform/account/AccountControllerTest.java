package com.example.training_platform.account;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.training_platform.auth.AuthenticatedUser;
import com.example.training_platform.auth.JwtService;
import com.example.training_platform.common.ApiExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@WebMvcTest(AccountController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(ApiExceptionHandler.class)
class AccountControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AccountService accountService;

    @MockBean
    private JwtService jwtService;

    @Test
    void changePasswordReturnsEnvelope() throws Exception {
        mockMvc.perform(post("/api/account/change-password")
                        .principal(auth(4L, "u@local", "MENTOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"oldPassword\":\"oldpass12\",\"newPassword\":\"newpass123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("Password changed successfully"));

        verify(accountService).changePassword(4L, "oldpass12", "newpass123");
    }

    @Test
    void changePasswordReturns400EnvelopeWhenServiceRejects() throws Exception {
        doThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Old password is incorrect"))
                .when(accountService).changePassword(eq(4L), eq("wrong"), eq("newpassword1"));

        mockMvc.perform(post("/api/account/change-password")
                        .principal(auth(4L, "u@local", "MENTOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"oldPassword\":\"wrong\",\"newPassword\":\"newpassword1\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("Old password is incorrect"));
    }

    @Test
    void changePasswordReturns400WhenNewPasswordTooShort() throws Exception {
        mockMvc.perform(post("/api/account/change-password")
                        .principal(auth(4L, "u@local", "MENTOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"oldPassword\":\"oldpass12\",\"newPassword\":\"short\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(accountService);
    }

    private static Authentication auth(Long userId, String email, String role) {
        return new UsernamePasswordAuthenticationToken(new AuthenticatedUser(userId, email, role), null, List.of());
    }
}
