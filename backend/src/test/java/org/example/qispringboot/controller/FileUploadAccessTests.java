package org.example.qispringboot.controller;

import org.example.qispringboot.common.GlobarExceptionHandler;
import org.example.qispringboot.common.ResultCode;
import org.example.qispringboot.mapper.SysFileInfoMapper;
import org.example.qispringboot.security.LoginUser;
import org.example.qispringboot.service.FileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.*;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class FileUploadAccessTests {
    private AnnotationConfigApplicationContext context;
    private FileService service;
    private MockMvc mvc;

    @Configuration
    @EnableMethodSecurity(proxyTargetClass = true)
    static class Config {
        @Bean SysFileInfoMapper fileInfoMapper() { return mock(SysFileInfoMapper.class); }
        @Bean FileService fileService() { return mock(FileService.class); }
        @Bean FileController fileController(FileService service) {
            var controller = new FileController();
            ReflectionTestUtils.setField(controller, "fileService", service);
            return controller;
        }
    }

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigApplicationContext(Config.class);
        service = context.getBean(FileService.class);
        mvc = MockMvcBuilders.standaloneSetup(context.getBean(FileController.class))
                .setControllerAdvice(new GlobarExceptionHandler()).build();
    }

    @AfterEach
    void close() { SecurityContextHolder.clearContext(); context.close(); }

    @Test
    void administratorCanUploadAndUploadOwnerComesFromLogin() throws Exception {
        login("ADMIN");
        mvc.perform(multipart("/api/file/upload").file(file()).param("businessType", "ARTICLE")
                .param("businessId", "draft").param("businessField", "cover").param("uploadUserId", "999"))
                .andExpect(jsonPath("$.code").value("200"));
        verify(service).upload(any(MultipartFile.class), eq("ARTICLE"), eq("draft"), eq("cover"), eq(7L));
    }

    @Test
    void ordinaryAndAnonymousUsersCannotUpload() throws Exception {
        login("USER");
        mvc.perform(multipart("/api/file/upload").file(file()))
                .andExpect(jsonPath("$.code").value(ResultCode.ACCESS_UNAUTHORIZED.getCode()));
        SecurityContextHolder.clearContext();
        mvc.perform(multipart("/api/file/upload").file(file()))
                .andExpect(jsonPath("$.code").value(ResultCode.UNAUTHORIZED.getCode()));
        verifyNoInteractions(service);
    }

    @Test
    void multipartSizeErrorGetsFileErrorAndHttp413() throws Exception {
        login("ADMIN");
        when(service.upload(any(), any(), any(), any(), any())).thenThrow(new MaxUploadSizeExceededException(5 * 1024 * 1024));
        mvc.perform(multipart("/api/file/upload").file(file())).andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value(ResultCode.FILE_SIZE_EXCEEDED.getCode()));
    }

    private MockMultipartFile file() { return new MockMultipartFile("file", "test.png", "image/png", new byte[] {1}); }
    private void login(String role) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new LoginUser(7L, "test", role.equals("ADMIN") ? 2 : 1), null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }
}
