package org.example.qispringboot.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.example.qispringboot.common.GlobarExceptionHandler;
import org.example.qispringboot.common.ResultCode;
import org.example.qispringboot.security.LoginUser;
import org.example.qispringboot.service.KnowledgeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class KnowledgeAccessTests {
    private AnnotationConfigApplicationContext context;
    private KnowledgeService service;
    private MockMvc mvc;

    @Configuration
    @EnableMethodSecurity(proxyTargetClass = true)
    static class Config {
        @Bean org.example.qispringboot.mapper.KnowledgeArticleMapper articleMapper() { return mock(org.example.qispringboot.mapper.KnowledgeArticleMapper.class); }
        @Bean org.example.qispringboot.mapper.KnowledgeCategoryMapper categoryMapper() { return mock(org.example.qispringboot.mapper.KnowledgeCategoryMapper.class); }
        @Bean org.example.qispringboot.mapper.UserMapper userMapper() { return mock(org.example.qispringboot.mapper.UserMapper.class); }
        @Bean KnowledgeService knowledgeService() { return mock(KnowledgeService.class); }
        @Bean KnowledgeController knowledgeController(KnowledgeService service) {
            var controller = new KnowledgeController();
            ReflectionTestUtils.setField(controller, "knowledgeService", service);
            return controller;
        }
    }

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigApplicationContext(Config.class);
        service = context.getBean(KnowledgeService.class);
        mvc = MockMvcBuilders.standaloneSetup(context.getBean(KnowledgeController.class))
                .setControllerAdvice(new GlobarExceptionHandler()).build();
    }

    @AfterEach
    void close() { SecurityContextHolder.clearContext(); context.close(); }

    @Test
    void readerCannotSelectDraftsByForgingStatusQuery() throws Exception {
        login("USER");
        when(service.pagePublishedArticles(1L, 10L, null, null, null, null)).thenReturn(new Page<>(1, 10));
        for (String status : List.of("0", "2", "999")) {
            mvc.perform(get("/api/knowledge/article/page").param("status", status))
                    .andExpect(jsonPath("$.code").value("200"));
        }
        verify(service, times(3)).pagePublishedArticles(1L, 10L, null, null, null, null);
        verify(service, never()).pageArticles(any(), any(), any(), any(), any(), any(), any());
        mvc.perform(get("/api/knowledge/article/published-id")).andExpect(jsonPath("$.code").value("200"));
        verify(service).getPublishedArticleDetail("published-id");
    }

    @Test
    void readerSearchPassesFiltersToPublishedOnlyQuery() throws Exception {
        login("USER");
        when(service.pagePublishedArticles(2L, 10L, "睡眠", 3L, "readCount", "desc"))
                .thenReturn(new Page<>(2, 10));
        mvc.perform(get("/api/knowledge/article/page").param("page", "2").param("size", "10")
                .param("title", "睡眠").param("categoryId", "3")
                .param("sortField", "readCount").param("sortDirection", "desc").param("status", "0"))
                .andExpect(jsonPath("$.code").value("200"));
        verify(service).pagePublishedArticles(2L, 10L, "睡眠", 3L, "readCount", "desc");
        verify(service, never()).pageArticles(any(), any(), any(), any(), any(), any(), any());
        mvc.perform(get("/api/knowledge/category/tree")).andExpect(jsonPath("$.code").value("200"));
        verify(service).listCategories();
    }

    @Test
    void ordinaryUserCannotPreviewDraftsOrWriteArticles() throws Exception {
        login("USER");
        assertDenied(ResultCode.ACCESS_UNAUTHORIZED.getCode());
        verifyNoInteractions(service);
    }

    @Test
    void anonymousUserCannotUseAdminEndpoints() throws Exception {
        SecurityContextHolder.clearContext();
        assertDenied(ResultCode.UNAUTHORIZED.getCode());
        verifyNoInteractions(service);
    }

    @Test
    void adminCanFilterAndPreviewDrafts() throws Exception {
        login("ADMIN");
        mvc.perform(get("/api/knowledge/admin/article/page").param("status", "0"))
                .andExpect(jsonPath("$.code").value("200"));
        verify(service).pageArticles(1L, 10L, null, null, 0, null, null);
        mvc.perform(get("/api/knowledge/admin/article/draft-id")).andExpect(jsonPath("$.code").value("200"));
        verify(service).getAdminArticleDetail("draft-id");
        mvc.perform(put("/api/knowledge/article/draft-id/status").contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"1\"}"))
                .andExpect(jsonPath("$.code").value("200"));
        verify(service).updateStatus("draft-id", "1");
    }

    @Test
    void invalidPublicationStatusIsRejectedBeforeSaving() throws Exception {
        login("ADMIN");
        for (String body : List.of("{}", "{\"status\":\"3\"}", "{\"status\":\"invalid\"}")) {
            mvc.perform(put("/api/knowledge/article/draft-id/status").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(jsonPath("$.code").value("400"));
        }
        mvc.perform(get("/api/knowledge/admin/article/page").param("status", "999"))
                .andExpect(jsonPath("$.msg").value("文章状态无效"));
        verifyNoInteractions(service);
    }

    private void assertDenied(String code) throws Exception {
        mvc.perform(get("/api/knowledge/admin/article/page")).andExpect(jsonPath("$.code").value(code));
        mvc.perform(get("/api/knowledge/admin/article/draft-id")).andExpect(jsonPath("$.code").value(code));
        mvc.perform(put("/api/knowledge/article/draft-id/status").contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"1\"}"))
                .andExpect(jsonPath("$.code").value(code));
        mvc.perform(delete("/api/knowledge/article/draft-id")).andExpect(jsonPath("$.code").value(code));
        mvc.perform(post("/api/knowledge/article").contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"test\",\"categoryId\":1}"))
                .andExpect(jsonPath("$.code").value(code));
    }

    private void login(String role) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new LoginUser(10L, "author", role.equals("ADMIN") ? 2 : 1), null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }
}
