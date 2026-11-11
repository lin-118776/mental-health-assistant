package org.example.qispringboot.controller;

import org.example.qispringboot.DTO.command.UserProfileUpdateDTO;
import org.example.qispringboot.DTO.command.UserPasswordUpdateDTO;
import org.example.qispringboot.common.GlobarExceptionHandler;
import org.example.qispringboot.service.UserProfileService;
import org.example.qispringboot.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class UserProfileControllerTests {
    private MockMvc mvc;
    private UserProfileService service;

    @BeforeEach
    void setUp() {
        service = mock(UserProfileService.class);
        var controller = new UserController();
        ReflectionTestUtils.setField(controller, "userProfileService", service);
        ReflectionTestUtils.setField(controller, "userService", mock(UserService.class));
        mvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new GlobarExceptionHandler()).build();
    }

    @Test
    void profileEndpointAcceptsOnlyProfileFields() throws Exception {
        mvc.perform(put("/api/user/me").contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\":999,\"userType\":2,\"status\":1,\"password\":\"overwrite\",\"nickname\":\"小明\",\"email\":\"ming@example.com\",\"phone\":\"13800138000\",\"gender\":1,\"birthday\":\"2000-01-01\"}"))
                .andExpect(jsonPath("$.code").value("200"));
        var dto = ArgumentCaptor.forClass(UserProfileUpdateDTO.class);
        verify(service).updateProfile(dto.capture());
        assertEquals("小明", dto.getValue().getNickname());
        assertFalse(List.of(UserProfileUpdateDTO.class.getDeclaredFields()).stream().anyMatch(field ->
                List.of("id", "userType", "password", "status", "username").contains(field.getName())));
    }

    @Test
    void rejectsInvalidProfileFields() throws Exception {
        for (String body : List.of("{\"email\":\"not-an-email\"}", "{\"phone\":\"123\"}",
                "{\"gender\":3}", "{\"birthday\":\"2999-01-01\"}", "{\"nickname\":\"" + "字".repeat(51) + "\"}")) {
            mvc.perform(put("/api/user/me").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(jsonPath("$.code").value("400"));
        }
        verifyNoInteractions(service);
    }

    @Test
    void passwordEndpointValidatesRequiredValuesAndLength() throws Exception {
        for (String body : List.of("{}", "{\"oldPassword\":\"old-pass\",\"newPassword\":\"123\"}",
                "{\"oldPassword\":\"\",\"newPassword\":\"new-pass\"}")) {
            mvc.perform(put("/api/user/password").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(jsonPath("$.code").value("400"));
        }
        verifyNoInteractions(service);
        mvc.perform(put("/api/user/password").contentType(MediaType.APPLICATION_JSON)
                .content("{\"oldPassword\":\"old-pass\",\"newPassword\":\"new-pass\"}"))
                .andExpect(jsonPath("$.code").value("200"));
        verify(service).updatePassword(any(UserPasswordUpdateDTO.class));
    }
}
