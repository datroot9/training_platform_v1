package com.example.training_platform.curriculum;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;

import com.example.training_platform.auth.AuthenticatedUser;
import com.example.training_platform.auth.JwtService;
import com.example.training_platform.common.ApiExceptionHandler;
import com.example.training_platform.common.dto.PagedResponse;
import com.example.training_platform.curriculum.dto.CurriculumDetailResponse;
import com.example.training_platform.curriculum.dto.CurriculumResponse;
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

@WebMvcTest(CurriculumController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(ApiExceptionHandler.class)
class CurriculumControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CurriculumService curriculumService;

    @MockBean
    private JwtService jwtService;

    @Test
    void createReturnsCreatedEnvelope() throws Exception {
        CurriculumResponse body = sampleCurriculum(50L);
        when(curriculumService.create(eq(2L), any())).thenReturn(body);

        mockMvc.perform(post("/api/mentor/curricula")
                        .principal(auth(2L, "m@local", "MENTOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Spring\",\"description\":\"Boot\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("Curriculum created"))
                .andExpect(jsonPath("$.data.id").value(50));

        verify(curriculumService).create(eq(2L), any());
    }

    @Test
    void listReturnsEnvelope() throws Exception {
        PagedResponse<CurriculumResponse> page = PagedResponse.of(List.of(sampleCurriculum(1L)), 0, 10, 1);
        when(curriculumService.list(2L, null, null, null, null, null, null)).thenReturn(page);

        mockMvc.perform(get("/api/mentor/curricula")
                        .principal(auth(2L, "m@local", "MENTOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("Curriculum list fetched"))
                .andExpect(jsonPath("$.data.items[0].id").value(1));

        verify(curriculumService).list(2L, null, null, null, null, null, null);
    }

    @Test
    void getDetailReturnsEnvelope() throws Exception {
        CurriculumResponse head = sampleCurriculum(9L);
        var detail = new CurriculumDetailResponse(head, List.of(), List.of());
        when(curriculumService.getDetail(2L, 9L)).thenReturn(detail);

        mockMvc.perform(get("/api/mentor/curricula/{id}", 9L)
                        .principal(auth(2L, "m@local", "MENTOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.curriculum.id").value(9));

        verify(curriculumService).getDetail(2L, 9L);
    }

    @Test
    void createReturns400WhenNameMissing() throws Exception {
        mockMvc.perform(post("/api/mentor/curricula")
                        .principal(auth(2L, "m@local", "MENTOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"only\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));

        verifyNoInteractions(curriculumService);
    }

    private static Authentication auth(Long userId, String email, String role) {
        return new UsernamePasswordAuthenticationToken(new AuthenticatedUser(userId, email, role), null, List.of());
    }

    private static CurriculumResponse sampleCurriculum(Long id) {
        return new CurriculumResponse(
                id,
                1L,
                "1.0",
                "Title",
                "Desc",
                "DRAFT",
                null,
                LocalDateTime.now(),
                LocalDateTime.now()
        );
    }
}
