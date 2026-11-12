package org.example.qispringboot.controller;

import org.example.qispringboot.DTO.response.EmotionDiaryResponseDTO;
import org.example.qispringboot.common.GlobarExceptionHandler;
import org.example.qispringboot.common.ResultCode;
import org.example.qispringboot.exception.BusinessException;
import org.example.qispringboot.mapper.EmotionDiaryMapper;
import org.example.qispringboot.mapper.UserMapper;
import org.example.qispringboot.security.LoginUser;
import org.example.qispringboot.service.EmotionDiaryService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AdminDiaryDetailAccessTests {
    private AnnotationConfigApplicationContext context;
    private EmotionDiaryService service;
    private MockMvc mvc;
    @Configuration
    @EnableMethodSecurity(proxyTargetClass = true)
    static class Config {
        @Bean EmotionDiaryMapper diaryMapper() { return mock(EmotionDiaryMapper.class); }
        @Bean UserMapper userMapper() { return mock(UserMapper.class); }
        @Bean EmotionDiaryService service() { return mock(EmotionDiaryService.class); }
        @Bean EmotionDiaryController controller(EmotionDiaryService service) {
            var controller = new EmotionDiaryController();
            ReflectionTestUtils.setField(controller,"emotionDiaryService",service);
            return controller;
        }
    }
    @BeforeEach
    void setUp() {
        context = new AnnotationConfigApplicationContext(Config.class);
        service = context.getBean(EmotionDiaryService.class);
        mvc = MockMvcBuilders.standaloneSetup(context.getBean(EmotionDiaryController.class))
                .setControllerAdvice(new GlobarExceptionHandler()).build();
    }
    @AfterEach
    void close() { SecurityContextHolder.clearContext(); context.close(); }
    @Test
    void adminCanReadAnotherUsersLatestDiary() throws Exception {
        login("ADMIN");
        when(service.adminDetail(11L)).thenReturn(EmotionDiaryResponseDTO.builder().id(11L).userId(7L)
                .diaryContent("最新内容").nickname("用户昵称").build());
        mvc.perform(get("/api/emotion-diary/admin/11"))
                .andExpect(jsonPath("$.code").value("200"))
                .andExpect(jsonPath("$.data.userId").value(7))
                .andExpect(jsonPath("$.data.diaryContent").value("最新内容"));
        verify(service).adminDetail(11L);
    }
    @Test
    void ordinaryAndAnonymousUsersCannotReadAdminDetail() throws Exception {
        login("USER");
        mvc.perform(get("/api/emotion-diary/admin/11").param("role","ADMIN"))
                .andExpect(jsonPath("$.code").value(ResultCode.ACCESS_UNAUTHORIZED.getCode()));
        SecurityContextHolder.clearContext();
        mvc.perform(get("/api/emotion-diary/admin/11"))
                .andExpect(jsonPath("$.code").value(ResultCode.UNAUTHORIZED.getCode()));
        verifyNoInteractions(service);
    }
    @Test
    void adminListAndDeletionUseProtectedEndpointsAndForwardSubmittedFilters() throws Exception {
        login("ADMIN");
        mvc.perform(get("/api/emotion-diary/admin/page").param("page", "2").param("size", "10")
                .param("userId", "7").param("moodScreRange", "1-3"))
                .andExpect(jsonPath("$.code").value("200"));
        verify(service).adminPage(2L,10L,7L,"1-3");
        mvc.perform(delete("/api/emotion-diary/admin/11")).andExpect(jsonPath("$.code").value("200"));
        verify(service).deleteDiary(11L);
    }
    @Test
    void ordinaryAndAnonymousUsersCannotListOrDeleteAdminDiaries() throws Exception {
        login("USER");
        mvc.perform(get("/api/emotion-diary/admin/page"))
                .andExpect(jsonPath("$.code").value(ResultCode.ACCESS_UNAUTHORIZED.getCode()));
        mvc.perform(delete("/api/emotion-diary/admin/11"))
                .andExpect(jsonPath("$.code").value(ResultCode.ACCESS_UNAUTHORIZED.getCode()));
        SecurityContextHolder.clearContext();
        mvc.perform(get("/api/emotion-diary/admin/page"))
                .andExpect(jsonPath("$.code").value(ResultCode.UNAUTHORIZED.getCode()));
        mvc.perform(delete("/api/emotion-diary/admin/11"))
                .andExpect(jsonPath("$.code").value(ResultCode.UNAUTHORIZED.getCode()));
        verifyNoInteractions(service);
    }
    @Test
    void deletedDiaryReturnsBusinessErrorRatherThanEmptySuccess() throws Exception {
        login("ADMIN");
        when(service.adminDetail(11L)).thenThrow(new BusinessException("日志不存在或已删除"));
        mvc.perform(get("/api/emotion-diary/admin/11"))
                .andExpect(jsonPath("$.code").value("BUSINESS_ERROR"))
                .andExpect(jsonPath("$.msg").value("日志不存在或已删除"));
    }
    private void login(String role) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new LoginUser(1L,"test",role.equals("ADMIN") ? 2 : 1),null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }
}
