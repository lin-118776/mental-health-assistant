package org.example.qispringboot.controller;
import org.example.qispringboot.rag.*;
import org.example.qispringboot.service.CategoryCache;
import org.example.qispringboot.security.LoginUser;
import org.example.qispringboot.common.GlobarExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.http.MediaType;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
class RagAccessTests {
    @Configuration @EnableMethodSecurity(proxyTargetClass=true)
    static class Config {
        @Bean RagService rag() {return mock(RagService.class);}
        @Bean CategoryCache cache() {return mock(CategoryCache.class);}
        @Bean RagController controller(RagService rag,CategoryCache cache) {return new RagController(rag,cache);}
    }
    AnnotationConfigApplicationContext context;MockMvc mvc;RagService rag;
    @BeforeEach void setup() {
        context=new AnnotationConfigApplicationContext(Config.class);rag=context.getBean(RagService.class);
        mvc=MockMvcBuilders.standaloneSetup(context.getBean(RagController.class)).setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver()).setControllerAdvice(new GlobarExceptionHandler()).build();
    }
    @AfterEach void close() {SecurityContextHolder.clearContext();context.close();}
    void login(String role) {SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(new LoginUser(7L,"test",role.equals("ADMIN")?2:1),null,List.of(new SimpleGrantedAuthority("ROLE_"+role))));}
    @Test void ordinaryAndAnonymousUsersCannotManageDocuments() throws Exception {
        login("USER");
        mvc.perform(get("/api/rag/admin/documents")).andExpect(jsonPath("$.code").value("A0301"));
        mvc.perform(delete("/api/rag/admin/documents/x")).andExpect(jsonPath("$.code").value("A0301"));
        SecurityContextHolder.clearContext();
        mvc.perform(get("/api/rag/admin/documents")).andExpect(jsonPath("$.code").value("401"));
        verifyNoInteractions(rag);
    }
    @Test void adminImportsUsingLoginOwnerAndCanRetryAndDisable() throws Exception {
        login("ADMIN");
        mvc.perform(post("/api/rag/admin/documents").contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"睡眠资料\",\"content\":\"正文\",\"createdBy\":999}"))
            .andExpect(jsonPath("$.code").value("200"));
        verify(rag).create("睡眠资料",null,"正文",7L);
        mvc.perform(post("/api/rag/admin/documents/x/retry")).andExpect(jsonPath("$.code").value("200"));verify(rag).retry("x");
        mvc.perform(delete("/api/rag/admin/documents/x")).andExpect(jsonPath("$.code").value("200"));verify(rag).disable("x");
    }
}
