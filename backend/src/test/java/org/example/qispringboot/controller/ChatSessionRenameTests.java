package org.example.qispringboot.controller;

import org.example.qispringboot.common.GlobarExceptionHandler;
import org.example.qispringboot.service.ChatService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ChatSessionRenameTests {
    private ChatService service;
    private MockMvc mvc;
    @BeforeEach
    void setUp() {
        service=mock(ChatService.class);
        var controller=new PsychologicalChatController();
        ReflectionTestUtils.setField(controller,"chatService",service);
        mvc=MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new GlobarExceptionHandler()).build();
    }
    @Test
    void renameUsesPathIdAndOnlyTitleFromPayload() throws Exception {
        mvc.perform(put("/api/psychological-chat/sessions/10/title").contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"我的新标题\",\"userId\":999,\"sessionId\":20}"))
                .andExpect(jsonPath("$.code").value("200"));
        verify(service).renameSession(10L,"我的新标题");
    }
    @Test
    void blankMissingAndOversizedTitlesFailValidationBeforeServiceCall() throws Exception {
        for (String body:List.of("{}","{\"title\":null}","{\"title\":\"   \"}","{\"title\":\""+"x".repeat(201)+"\"}"))
            mvc.perform(put("/api/psychological-chat/sessions/10/title").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(jsonPath("$.code").value("400"));
        verifyNoInteractions(service);
    }
}
