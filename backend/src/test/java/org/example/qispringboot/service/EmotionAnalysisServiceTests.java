package org.example.qispringboot.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.example.qispringboot.entity.ChatMessage;
import org.example.qispringboot.entity.ChatSession;
import org.example.qispringboot.exception.BusinessException;
import org.example.qispringboot.mapper.ChatMessageMapper;
import org.example.qispringboot.mapper.ChatSessionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class EmotionAnalysisServiceTests {
    private final ObjectMapper json = new ObjectMapper();
    private EmotionAnalysisService service;
    private ChatClient client;
    private ChatClient.CallResponseSpec call;
    private ChatMessageMapper messages;
    private ChatSessionMapper sessions;
    private ChatSession session;

    @BeforeEach
    void setUp() throws Exception {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "test"), ChatMessage.class);
        client = mock(ChatClient.class);
        var builder = mock(ChatClient.Builder.class);
        when(builder.build()).thenReturn(client);
        var request = mock(ChatClient.ChatClientRequestSpec.class);
        call = mock(ChatClient.CallResponseSpec.class);
        when(client.prompt(any(Prompt.class))).thenReturn(request);
        when(request.call()).thenReturn(call);
        when(call.content()).thenReturn(json.writeValueAsString(valid()));
        service = new EmotionAnalysisService(builder, json);
        messages = mock(ChatMessageMapper.class);
        sessions = mock(ChatSessionMapper.class);
        ReflectionTestUtils.setField(service, "chatMessageMapper", messages);
        ReflectionTestUtils.setField(service, "chatSessionMapper", sessions);
        session = ChatSession.builder().id(10L).userId(1L).build();
        when(messages.selectList(any(Wrapper.class))).thenReturn(List.of(message(2L, "明天考试，我很紧张"),
                message(1L, "最近复习压力大")));
        clearInvocations(client);
    }

    @Test
    void analyzesChronologicalUserContentAndPersistsResult() throws Exception {
        var result = service.analyze(session);
        assertEquals("焦虑", result.get("primaryEmotion"));
        assertEquals(35, result.get("emotionScore"));
        assertEquals(true, result.get("available"));
        ArgumentCaptor<Prompt> prompt = ArgumentCaptor.forClass(Prompt.class);
        verify(client).prompt(prompt.capture());
        assertEquals(List.of("最近复习压力大", "明天考试，我很紧张"), json.readValue(
                prompt.getValue().getInstructions().get(1).getText(), List.class));
        ArgumentCaptor<Wrapper<ChatMessage>> query = ArgumentCaptor.forClass(Wrapper.class);
        verify(messages).selectList(query.capture());
        String sql = query.getValue().getSqlSegment();
        assertTrue(sql.contains("session_id"));
        assertTrue(sql.contains("sender_type"));
        assertTrue(sql.contains("message_type"));
        assertTrue(sql.contains("LIMIT 20"));
        assertTrue(((LambdaQueryWrapper<ChatMessage>) query.getValue()).getParamNameValuePairs().containsValue(10L));
        var saved = savedSession();
        assertEquals(10L, saved.getId());
        assertNotNull(saved.getLastEmotionUpdatedAt());
        assertEquals(2L, json.readTree(saved.getLastEmotionAnalysis()).get("sourceMessageId").longValue());
    }

    @Test
    void reusesPersistedAnalysisUntilNewUserMessageArrives() {
        service.analyze(session);
        session.setLastEmotionAnalysis(savedSession().getLastEmotionAnalysis());
        clearInvocations(client, sessions);
        assertEquals("焦虑", service.analyze(session).get("primaryEmotion"));
        verifyNoInteractions(client, sessions);
        when(messages.selectList(any(Wrapper.class))).thenReturn(List.of(message(3L, "现在轻松多了")));
        service.analyze(session);
        verify(client).prompt(any(Prompt.class));
        verify(sessions).updateById(any(ChatSession.class));
    }

    @Test
    void emptyConversationHasNoInventedScoreOrRisk() {
        when(messages.selectList(any(Wrapper.class))).thenReturn(List.of());
        var result = service.analyze(session);
        assertEquals(false, result.get("available"));
        assertNull(result.get("emotionScore"));
        assertNull(result.get("riskLevel"));
        verifyNoInteractions(client, sessions);
    }

    @Test
    void acceptsJsonCodeFenceAndKeepsRiskNoticeVisible() throws Exception {
        var result = valid();
        result.put("riskLevel", 3);
        result.put("isNegative", false);
        result.put("riskDescription", "请立即联系身边可信任的人并寻求当地紧急服务帮助。");
        when(call.content()).thenReturn("```json\n" + json.writeValueAsString(result) + "\n```");
        var analysis = service.analyze(session);
        assertEquals(3, analysis.get("riskLevel"));
        assertEquals(true, analysis.get("isNegative"));
    }

    @Test
    void rejectsMalformedIncompleteAndOutOfRangeResponsesWithoutSaving() throws Exception {
        for (String invalid : List.of("not json", "{}", "null", "{} trailing")) {
            when(call.content()).thenReturn(invalid);
            assertThrows(BusinessException.class, () -> service.analyze(session));
        }
        for (Map.Entry<String, Object> invalid : List.<Map.Entry<String, Object>>of(
                Map.entry("emotionScore", 101), Map.entry("emotionScore", -1),
                Map.entry("emotionScore", "50"), Map.entry("riskLevel", 4),
                Map.entry("riskLevel", 2), Map.entry("isNegative", "false"),
                Map.entry("improvementSuggestions", List.of()), Map.entry("primaryEmotion", ""))) {
            var response = valid();
            response.put(invalid.getKey(), invalid.getValue());
            when(call.content()).thenReturn(json.writeValueAsString(response));
            assertThrows(BusinessException.class, () -> service.analyze(session));
        }
        verifyNoInteractions(sessions);
    }

    @Test
    void modelFailureDoesNotOverwritePreviousAnalysis() {
        session.setLastEmotionAnalysis("old-format-cache");
        when(call.content()).thenThrow(new RuntimeException("unavailable"));
        assertThrows(BusinessException.class, () -> service.analyze(session));
        verifyNoInteractions(sessions);
        assertEquals("old-format-cache", session.getLastEmotionAnalysis());
    }

    @Test
    void limitsInputWithoutCuttingMessages() throws Exception {
        when(messages.selectList(any(Wrapper.class))).thenReturn(List.of(
                message(3L, "最新发言"), message(2L, "旧".repeat(16000)), message(1L, "更早")));
        service.analyze(session);
        ArgumentCaptor<Prompt> prompt = ArgumentCaptor.forClass(Prompt.class);
        verify(client).prompt(prompt.capture());
        assertEquals(List.of("最新发言"), json.readValue(prompt.getValue().getInstructions().get(1).getText(), List.class));
    }

    private ChatSession savedSession() {
        var saved = ArgumentCaptor.forClass(ChatSession.class);
        verify(sessions).updateById(saved.capture());
        return saved.getValue();
    }

    private ChatMessage message(long id, String content) {
        return ChatMessage.builder().id(id).sessionId(10L).senderType(1).messageType(1).content(content).build();
    }

    private Map<String, Object> valid() {
        return new LinkedHashMap<>(Map.of("primaryEmotion", "焦虑", "emotionScore", 35, "isNegative", true,
                "riskLevel", 1, "suggestion", "给自己留一点休息时间", "improvementSuggestions", List.of("散步十分钟"),
                "riskDescription", ""));
    }
}
