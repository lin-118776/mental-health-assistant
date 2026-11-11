package org.example.qispringboot.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.example.qispringboot.common.GlobarExceptionHandler;
import org.example.qispringboot.common.ResultCode;
import org.example.qispringboot.service.AdminUserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.springframework.context.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AdminUserAccessTests {
    private AnnotationConfigApplicationContext context;
    private AdminUserService service;
    private MockMvc mvc;

    @Configuration
    @EnableMethodSecurity(proxyTargetClass = true)
    static class Config {
        @Bean AdminUserService adminUserService() { return mock(AdminUserService.class); }
        @Bean AdminUserController adminUserController(AdminUserService service) { return new AdminUserController(service); }
    }

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigApplicationContext(Config.class);
        service = context.getBean(AdminUserService.class);
        mvc = MockMvcBuilders.standaloneSetup(context.getBean(AdminUserController.class))
                .setControllerAdvice(new GlobarExceptionHandler()).build();
    }

    @AfterEach
    void close() {
        SecurityContextHolder.clearContext();
        context.close();
    }

    @Test
    void administratorCanSearchAndChangeStatus() throws Exception {
        login("ADMIN");
        when(service.page("ming", 0, 2, 10)).thenReturn(new Page<>(2, 10, 12));
        mvc.perform(get("/api/user/admin/page").param("keyword", "ming").param("status", "0").param("page", "2"))
                .andExpect(jsonPath("$.code").value("200")).andExpect(jsonPath("$.data.total").value(12));
        mvc.perform(put("/api/user/admin/10/status").contentType(MediaType.APPLICATION_JSON).content("{\"status\":0}"))
                .andExpect(jsonPath("$.code").value("200"));
        verify(service).page("ming", 0, 2, 10);
        verify(service).updateStatus(10L, 0);
    }

    @Test
    void ordinaryUserCannotReadOrChangeAccounts() throws Exception {
        login("USER");
        denied(ResultCode.ACCESS_UNAUTHORIZED.getCode());
    }

    @Test
    void missingLoginCannotReadOrChangeAccounts() throws Exception {
        SecurityContextHolder.clearContext();
        denied(ResultCode.UNAUTHORIZED.getCode());
    }

    @Test
    void invalidStatusIsRejectedBeforeService() throws Exception {
        login("ADMIN");
        for (String body : List.of("{}", "{\"status\":2}", "{\"status\":-1}")) {
            mvc.perform(put("/api/user/admin/10/status").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(jsonPath("$.code").value("400"));
        }
        verifyNoInteractions(service);
    }

    private void denied(String code) throws Exception {
        mvc.perform(get("/api/user/admin/page")).andExpect(jsonPath("$.code").value(code));
        mvc.perform(put("/api/user/admin/10/status").contentType(MediaType.APPLICATION_JSON).content("{\"status\":0}"))
                .andExpect(jsonPath("$.code").value(code));
        verifyNoInteractions(service);
    }

    private void login(String role) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "test", null, List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }
}
