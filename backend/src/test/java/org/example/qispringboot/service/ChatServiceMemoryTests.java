package org.example.qispringboot.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.example.qispringboot.DTO.command.AiChatCommandDTO;
import org.example.qispringboot.DTO.command.ChatMessageCommandDTO;
import org.example.qispringboot.entity.ChatMessage;
import org.example.qispringboot.entity.ChatSession;
import org.example.qispringboot.entity.User;
import org.example.qispringboot.exception.BusinessException;
import org.example.qispringboot.mapper.ChatMessageMapper;
import org.example.qispringboot.mapper.ChatSessionMapper;
import org.example.qispringboot.mapper.UserMapper;
import org.example.qispringboot.security.LoginUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Flux;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ChatServiceMemoryTests {
    private ChatService service;
    private ChatClient client;
    private ChatMessageMapper messages;
    private ChatSessionMapper sessions;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "test"),
                ChatMessage.class);
        client = mock(ChatClient.class);
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        when(builder.build()).thenReturn(client);
        service = new ChatService(builder, new ObjectMapper());
        messages = mock(ChatMessageMapper.class);
        sessions = mock(ChatSessionMapper.class);
        UserMapper users = mock(UserMapper.class);
        ReflectionTestUtils.setField(service, "chatMessageMapper", messages);
        ReflectionTestUtils.setField(service, "chatSessionMapper", sessions);
        ReflectionTestUtils.setField(service, "userMapper", users);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(new LoginUser(1L, "test", 1), null, List.of()));
        when(users.selectById(1L)).thenReturn(User.builder().id(1L).status(1).build());
        when(sessions.selectById(10L)).thenReturn(ChatSession.builder().id(10L).userId(1L).build());
        when(messages.selectList(any(Wrapper.class))).thenReturn(List.of());
        ChatClient.ChatClientRequestSpec request = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.StreamResponseSpec stream = mock(ChatClient.StreamResponseSpec.class);
        ChatClient.CallResponseSpec call = mock(ChatClient.CallResponseSpec.class);
        when(client.prompt(any(Prompt.class))).thenReturn(request);
        when(request.stream()).thenReturn(stream);
        when(request.call()).thenReturn(call);
        when(stream.content()).thenReturn(Flux.just("新的回答"));
        when(call.content()).thenReturn("新的回答");
        clearInvocations(client);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void streamIncludesOrderedHistoryWithRolesAndCurrentQuestionOnce() {
        when(messages.selectList(any(Wrapper.class))).thenReturn(List.of(
                message(2, "可以先做一次呼吸练习"), message(1, "我明天考试，很紧张")));
        stream(10L, "还有什么办法？");
        List<Message> prompt = capturedPrompt().getInstructions();
        assertEquals(List.of(MessageType.SYSTEM, MessageType.USER, MessageType.ASSISTANT, MessageType.USER),
                prompt.stream().map(Message::getMessageType).toList());
        assertEquals(List.of("我明天考试，很紧张", "可以先做一次呼吸练习", "还有什么办法？"),
                prompt.subList(1, prompt.size()).stream().map(Message::getText).toList());
        var order = inOrder(messages);
        order.verify(messages).selectList(any(Wrapper.class));
        order.verify(messages).insert(argThat((ChatMessage m) -> m.getSenderType() == 1));
        order.verify(messages).insert(argThat((ChatMessage m) -> m.getSenderType() == 2));
    }

    @Test
    void newSessionStartsWithoutOtherSessionHistory() {
        doAnswer(invocation -> {
            ((ChatSession) invocation.getArgument(0)).setId(20L);
            return 1;
        }).when(sessions).insert(any(ChatSession.class));
        stream(null, "第一次倾诉");
        assertEquals(2, capturedPrompt().getInstructions().size());
        ArgumentCaptor<Wrapper<ChatMessage>> query = ArgumentCaptor.forClass(Wrapper.class);
        verify(messages).selectList(query.capture());
        assertTrue(query.getValue().getSqlSegment().contains("LIMIT 20"));
        var params = ((com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ChatMessage>)
                query.getValue()).getParamNameValuePairs();
        assertTrue(params.containsValue(20L));
        assertFalse(params.containsValue(10L));
    }

    @Test
    void rejectsOtherUsersSessionBeforeReadingHistoryOrCallingModel() {
        when(sessions.selectById(10L)).thenReturn(ChatSession.builder().id(10L).userId(2L).build());
        assertThrows(BusinessException.class, () -> stream(10L, "继续"));
        verifyNoInteractions(messages, client);
    }

    @Test
    void emotionAnalysisChecksOwnershipBeforeCallingAnalysisService() {
        EmotionAnalysisService analysis = mock(EmotionAnalysisService.class);
        ReflectionTestUtils.setField(service, "emotionAnalysisService", analysis);
        when(sessions.selectById(10L)).thenReturn(ChatSession.builder().id(10L).userId(2L).build());
        assertThrows(BusinessException.class, () -> service.getSessionEmotion(10L));
        verifyNoInteractions(analysis);
    }

    @Test
    void historyIsLoadedAgainWhenContinuingExistingSession() {
        stream(10L, "第一次");
        when(messages.selectList(any(Wrapper.class))).thenReturn(List.of(
                message(2, "之前的回复"), message(1, "之前的问题")));
        clearInvocations(client);
        stream(10L, "继续之前的话题");
        assertEquals("之前的问题", capturedPrompt().getInstructions().get(1).getText());
        verify(messages, times(2)).selectList(any(Wrapper.class));
    }

    @Test
    void trimsOldHistoryWithoutTruncatingMessagesOrLeavingLeadingAssistant() {
        when(messages.selectList(any(Wrapper.class))).thenReturn(List.of(
                message(2, "最近回答"), message(1, "最近问题"),
                message(2, "更早回答"), message(1, "旧".repeat(16000))));
        stream(10L, "继续");
        assertEquals(List.of("最近问题", "最近回答", "继续"), capturedPrompt().getInstructions()
                .stream().skip(1).map(Message::getText).toList());
    }

    @Test
    void skipsBlankHistory() {
        when(messages.selectList(any(Wrapper.class))).thenReturn(List.of(
                message(2, " "), message(1, "历史问题")));
        stream(10L, "继续");
        assertEquals(3, capturedPrompt().getInstructions().size());
    }

    @Test
    void nonStreamingChatUsesTheSameConversationMemory() {
        when(messages.selectList(any(Wrapper.class))).thenReturn(List.of(
                message(2, "历史回答"), message(1, "历史问题")));
        ChatMessageCommandDTO command = new ChatMessageCommandDTO();
        command.setSessionId(10L);
        command.setContent("继续");
        assertEquals("新的回答", service.chat(command).getReply());
        assertEquals(4, capturedPrompt().getInstructions().size());
    }

    private void stream(Long sessionId, String question) {
        AiChatCommandDTO command = new AiChatCommandDTO();
        command.setSessionId(sessionId);
        command.setContent(question);
        List<String> response = service.streamChat(command).collectList().block();
        assertNotNull(response);
        assertEquals(1, response.size());
        assertTrue(response.get(0).contains("新的回答"));
    }

    private Prompt capturedPrompt() {
        ArgumentCaptor<Prompt> prompt = ArgumentCaptor.forClass(Prompt.class);
        verify(client).prompt(prompt.capture());
        return prompt.getValue();
    }

    private ChatMessage message(int sender, String content) {
        return ChatMessage.builder().senderType(sender).messageType(1).content(content).build();
    }
}
