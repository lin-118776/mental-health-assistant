package org.example.qispringboot.controller;

import org.example.qispringboot.DTO.command.EmotionDiaryCreateDTO;
import org.example.qispringboot.common.GlobarExceptionHandler;
import org.example.qispringboot.common.ResultCode;
import org.example.qispringboot.security.LoginUser;
import org.example.qispringboot.service.EmotionDiaryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class EmotionDiaryOwnerControllerTests {
    private EmotionDiaryService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(EmotionDiaryService.class);
        var controller = new EmotionDiaryController();
        ReflectionTestUtils.setField(controller, "emotionDiaryService", service);
        mvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new GlobarExceptionHandler()).build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(new LoginUser(7L, "owner", 1), null, List.of()));
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void allRoutesUseAuthenticatedUserRatherThanSuppliedUserId() throws Exception {
        mvc.perform(get("/api/emotion-diary/mine").param("userId", "999")).andExpect(status().isOk());
        verify(service).myPage(7L, 1, 10);
        mvc.perform(get("/api/emotion-diary/mine/11").param("userId", "999")).andExpect(status().isOk());
        verify(service).myDetail(7L, 11L);
        mvc.perform(put("/api/emotion-diary/mine/11").contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":999,\"diaryDate\":\"2026-11-11\",\"moodScore\":8}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("200"));
        verify(service).updateMine(eq(7L), eq(11L), any(EmotionDiaryCreateDTO.class));
        mvc.perform(delete("/api/emotion-diary/mine/11").param("userId", "999")).andExpect(status().isOk());
        verify(service).deleteMine(7L, 11L);
    }

    @Test
    void rejectsInvalidUpdateBeforeServiceCall() throws Exception {
        for (String body : List.of("{\"diaryDate\":\"2026-11-11\",\"moodScore\":11}",
                "{\"moodScore\":8}", "{\"diaryDate\":\"2026-11-11\",\"moodScore\":8,\"sleepQuality\":6}")) {
            mvc.perform(put("/api/emotion-diary/mine/11").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(jsonPath("$.code").value(ResultCode.ERROR.getCode()));
        }
        verifyNoInteractions(service);
    }

    @Test
    void allOwnDiaryRoutesRejectMissingLogin() throws Exception {
        SecurityContextHolder.clearContext();
        mvc.perform(get("/api/emotion-diary/mine")).andExpect(jsonPath("$.code").value(ResultCode.UNAUTHORIZED.getCode()));
        mvc.perform(get("/api/emotion-diary/mine/11")).andExpect(jsonPath("$.code").value(ResultCode.UNAUTHORIZED.getCode()));
        mvc.perform(delete("/api/emotion-diary/mine/11")).andExpect(jsonPath("$.code").value(ResultCode.UNAUTHORIZED.getCode()));
        mvc.perform(put("/api/emotion-diary/mine/11").contentType(MediaType.APPLICATION_JSON)
                .content("{\"diaryDate\":\"2026-11-11\",\"moodScore\":8}"))
                .andExpect(jsonPath("$.code").value(ResultCode.UNAUTHORIZED.getCode()));
        verifyNoInteractions(service);
    }
}
