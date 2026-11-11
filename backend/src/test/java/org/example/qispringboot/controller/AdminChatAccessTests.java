package org.example.qispringboot.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.example.qispringboot.common.GlobarExceptionHandler;
import org.example.qispringboot.common.ResultCode;
import org.example.qispringboot.service.AdminChatService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.springframework.context.annotation.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AdminChatAccessTests {
    private AnnotationConfigApplicationContext context;
    private AdminChatService service;
    private MockMvc mvc;

    @Configuration
    @EnableMethodSecurity(proxyTargetClass = true)
    static class Config {
        @Bean AdminChatService adminChatService() { return mock(AdminChatService.class); }
        @Bean AdminChatController adminChatController(AdminChatService service) { return new AdminChatController(service); }
    }

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigApplicationContext(Config.class);
        service = context.getBean(AdminChatService.class);
        mvc = MockMvcBuilders.standaloneSetup(context.getBean(AdminChatController.class))
                .setControllerAdvice(new GlobarExceptionHandler()).build();
    }

    @AfterEach
    void close() {
        SecurityContextHolder.clearContext();
        context.close();
    }

    @Test
    void administratorCanReadListAndDetail() throws Exception {
        login("ADMIN");
        when(service.page(2, 10)).thenReturn(new Page<>(2, 10, 12));
        mvc.perform(get("/api/psychological-chat/admin/sessions").param("page", "2"))
                .andExpect(jsonPath("$.code").value("200")).andExpect(jsonPath("$.data.total").value(12));
        mvc.perform(get("/api/psychological-chat/admin/sessions/10"))
                .andExpect(jsonPath("$.code").value("200"));
        verify(service).page(2, 10);
        verify(service).detail(10L);
    }

    @Test
    void ordinaryUserCannotReadEitherAdminEndpoint() throws Exception {
        login("USER");
        mvc.perform(get("/api/psychological-chat/admin/sessions"))
                .andExpect(jsonPath("$.code").value(ResultCode.ACCESS_UNAUTHORIZED.getCode()));
        mvc.perform(get("/api/psychological-chat/admin/sessions/10"))
                .andExpect(jsonPath("$.code").value(ResultCode.ACCESS_UNAUTHORIZED.getCode()));
        verifyNoInteractions(service);
    }

    @Test
    void missingLoginCannotReadEitherAdminEndpoint() throws Exception {
        SecurityContextHolder.clearContext();
        mvc.perform(get("/api/psychological-chat/admin/sessions"))
                .andExpect(jsonPath("$.code").value(ResultCode.UNAUTHORIZED.getCode()));
        mvc.perform(get("/api/psychological-chat/admin/sessions/10"))
                .andExpect(jsonPath("$.code").value(ResultCode.UNAUTHORIZED.getCode()));
        verifyNoInteractions(service);
    }

    private void login(String role) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "test", null, List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }
}
