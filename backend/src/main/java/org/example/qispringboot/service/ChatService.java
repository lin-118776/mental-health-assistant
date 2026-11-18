package org.example.qispringboot.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.example.qispringboot.AiService.PromptManage;
import org.example.qispringboot.DTO.command.AiChatCommandDTO;
import org.example.qispringboot.DTO.command.ChatMessageCommandDTO;
import org.example.qispringboot.DTO.response.ChatReplyResponseDTO;
import org.example.qispringboot.DTO.response.ChatSessionSummaryResponseDTO;
import org.example.qispringboot.common.ResultCode;
import org.example.qispringboot.entity.ChatMessage;
import org.example.qispringboot.entity.ChatSession;
import org.example.qispringboot.entity.User;
import org.example.qispringboot.exception.BusinessException;
import org.example.qispringboot.mapper.ChatMessageMapper;
import org.example.qispringboot.mapper.ChatSessionMapper;
import org.example.qispringboot.mapper.UserMapper;
import org.example.qispringboot.security.LoginUser;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.SignalType;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 心理咨询服务类
 * 提供会话创建、消息保存、流式AI对话、会话查询等业务逻辑
 */
@Slf4j
@Service
public class ChatService {

    // 发送者类型：用户
    private static final int SENDER_USER = 1;

    // 发送者类型：AI助手
    private static final int SENDER_AI = 2;

    // 消息类型：文本
    private static final int MESSAGE_TYPE_TEXT = 1;

    private static final int MAX_HISTORY_MESSAGES = 20;
    private static final int MAX_HISTORY_CHARACTERS = 16000;

    @Resource
    private ChatSessionMapper chatSessionMapper;

    @Resource
    private ChatMessageMapper chatMessageMapper;

    @Resource
    private UserMapper userMapper;

    @Resource
    private EmotionAnalysisService emotionAnalysisService;

    @org.springframework.beans.factory.annotation.Autowired(required=false)
    private org.example.qispringboot.rag.RagService ragService;

    // Spring AI聊天客户端（默认使用硅基流动配置）
    private final ChatClient chatClient;

    // JSON序列化器（用于组装流式分段JSON）
    private final ObjectMapper objectMapper;

    /**
     * 构造注入ChatClient.Builder（Spring AI自动配置）与ObjectMapper
     */
    public ChatService(ChatClient.Builder chatClientBuilder, ObjectMapper objectMapper) {
        this.chatClient = chatClientBuilder.build();
        this.objectMapper = objectMapper;
    }

    /**
     * 从Security上下文获取当前登录用户
     */
    private LoginUser getCurrentLoginUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        // 未登录或认证主体不是LoginUser时抛出未授权异常
        if (authentication == null || !(authentication.getPrincipal() instanceof LoginUser loginUser)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        return loginUser;
    }

    /**
     * 校验用户账号状态（账号被禁用时禁止发起咨询）
     *
     * @param userId 用户ID
     */
    private void checkUserActive(Long userId) {
        // 查询最新用户信息
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_EXIST);
        }
        // 账号被禁用（封禁）时抛出业务异常
        if (user.isDisabled()) {
            throw new BusinessException(ResultCode.ACCOUNT_DISABLED);
        }
    }

    /**
     * 创建心理咨询会话（绑定当前登录用户ID）
     *
     * @param userId 用户ID
     * @param title  会话标题
     * @return 创建成功的会话实体
     */
    public ChatSession createSession(Long userId, String title) {
        ChatSession session = ChatSession.builder()
                .userId(userId)
                .sessionTitle(title)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        chatSessionMapper.insert(session);
        return session;
    }

    /**
     * 保存聊天消息到消息表
     *
     * @param sessionId  会话ID
     * @param senderType 发送者类型 1:用户 2:AI助手
     * @param content    消息内容
     * @return 保存成功的消息实体
     */
    public ChatMessage saveMessage(Long sessionId, Integer senderType, String content) {
        ChatMessage message = ChatMessage.builder()
                .sessionId(sessionId)
                .senderType(senderType)
                .messageType(MESSAGE_TYPE_TEXT)
                .content(content)
                .createdAt(LocalDateTime.now())
                .build();
        chatMessageMapper.insert(message);
        return message;
    }

    /**
     * 查询当前登录用户拥有的会话（校验存在性与归属）
     *
     * @param userId    用户ID
     * @param sessionId 会话ID
     * @return 会话实体
     */
    private ChatSession getOwnedSession(Long userId, Long sessionId) {
        ChatSession session = chatSessionMapper.selectById(sessionId);
        if (session == null) {
            throw new BusinessException("会话不存在");
        }
        if (!session.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.ACCESS_UNAUTHORIZED);
        }
        return session;
    }

    /**
     * 解析会话：未传sessionId时自动新建会话，否则校验会话存在性与归属
     *
     * @param userId    当前登录用户ID
     * @param sessionId 会话ID（可为空）
     * @param content   用户消息内容（用于自动生成会话标题）
     * @return 会话实体
     */
    private ChatSession resolveSession(Long userId, Long sessionId, String content) {
        if (sessionId == null) {
            // 自动新建会话，标题取消息内容前20个字符
            String title = content.length() > 20 ? content.substring(0, 20) : content;
            return createSession(userId, title);
        }
        return getOwnedSession(userId, sessionId);
    }

    /**
     * 分页查询当前登录用户的会话列表
     *
     * @param pageNum  页码（从1开始）
     * @param pageSize 每页条数
     * @return 分页结果（records/total）
     */
    public Page<ChatSessionSummaryResponseDTO> pageSessions(long pageNum, long pageSize) {
        Long userId = getCurrentLoginUser().getUserId();
        return chatSessionMapper.selectOwnedSummaries(
                new Page<>(Math.max(1, pageNum), Math.max(1, Math.min(100, pageSize))), userId);
    }

    /**
     * 查询会话消息列表（校验归属）
     *
     * @param sessionId 会话ID
     * @return 消息列表（按创建时间正序）
     */
    public List<ChatMessage> listMessages(Long sessionId) {
        Long userId = getCurrentLoginUser().getUserId();
        getOwnedSession(userId, sessionId);
        return chatMessageMapper.selectList(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getSessionId, sessionId)
                .orderByAsc(ChatMessage::getId));
    }

    /**
     * 修改当前用户的会话标题，更新条件同时包含会话ID和用户ID。
     *
     * @param sessionId 会话ID
     */
    public void renameSession(Long sessionId, String title) {
        Long userId = getCurrentLoginUser().getUserId();
        if (title == null || title.length() > 200 || title.strip().isBlank())
            throw new BusinessException("会话标题须为1到200个字符");
        int changed = chatSessionMapper.update(null, new LambdaUpdateWrapper<ChatSession>()
                .eq(ChatSession::getId, sessionId).eq(ChatSession::getUserId, userId)
                .set(ChatSession::getSessionTitle, title.strip())
                .set(ChatSession::getUpdatedAt, LocalDateTime.now()));
        if (changed != 1) throw new BusinessException("会话不存在或无权修改");
    }

    /** 删除会话及其消息，任一步失败时整体回滚。 */
    @Transactional(rollbackFor = Exception.class)
    public void deleteSession(Long sessionId) {
        Long userId = getCurrentLoginUser().getUserId();
        getOwnedSession(userId, sessionId);
        // 先删除会话下的消息，再删除会话
        chatMessageMapper.delete(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getSessionId, sessionId));
        int changed = chatSessionMapper.delete(new LambdaQueryWrapper<ChatSession>()
                .eq(ChatSession::getId, sessionId).eq(ChatSession::getUserId, userId));
        if (changed != 1) throw new BusinessException("会话不存在或已删除，请刷新后重试");
    }

    /**
     * 获取会话情绪分析
     * 根据会话内用户的实际发言分析情绪，先校验会话归属。
     *
     * @param sessionId 会话ID
     * @return 情绪分析结果
     */
    public Map<String, Object> getSessionEmotion(Long sessionId) {
        Long userId = getCurrentLoginUser().getUserId();
        ChatSession session = getOwnedSession(userId, sessionId);
        return emotionAnalysisService.analyze(session);
    }

    /**
     * 开始咨询会话（显式创建会话，并校验账号状态）
     *
     * @param title 会话标题（可为空）
     * @return 新会话ID
     */
    public Long startSession(String title) {
        // 1. 获取当前登录用户
        LoginUser loginUser = getCurrentLoginUser();
        // 2. 校验账号状态（禁用禁止发起咨询）
        checkUserActive(loginUser.getUserId());
        // 3. 标题为空时使用默认标题
        String sessionTitle = StringUtils.hasText(title) ? title : "新的心理咨询会话";
        // 4. 创建会话
        return createSession(loginUser.getUserId(), sessionTitle).getId();
    }

    /**
     * 发起咨询（简单模式）
     * 自动新建会话（未传sessionId时），并将用户问题与AI回复分别存入聊天消息表
     *
     * @param commandDTO 咨询请求参数
     * @return 回复响应（含会话ID、消息ID与AI回复内容）
     */
    public ChatReplyResponseDTO chat(ChatMessageCommandDTO commandDTO) {
        // 1. 获取当前登录用户并校验账号状态（禁用禁止发起AI咨询）
        LoginUser loginUser = getCurrentLoginUser();
        checkUserActive(loginUser.getUserId());
        Long userId = loginUser.getUserId();
        // 2. 解析会话：未传sessionId时自动新建会话
        ChatSession session = resolveSession(userId, commandDTO.getSessionId(), commandDTO.getContent());
        var sources = ragService==null ? java.util.List.<org.example.qispringboot.rag.RagService.Source>of() : ragService.search(commandDTO.getContent());
        Prompt prompt = buildConversationPrompt(session.getId(), commandDTO.getContent(),sources);
        // 3. 保存用户问题消息（发送者=用户）
        ChatMessage userMessage = saveMessage(session.getId(), SENDER_USER, commandDTO.getContent());
        // 4. 生成AI回复并保存消息（发送者=AI助手）
        String reply = chatClient.prompt(prompt).call().content();
        if(ragService!=null) reply=reply+ragService.citations(sources);
        ChatMessage aiMessage = saveMessage(session.getId(), SENDER_AI, reply);
        // 5. 组装响应返回
        return ChatReplyResponseDTO.builder()
                .sessionId(session.getId())
                .userMessageId(userMessage.getId())
                .aiMessageId(aiMessage.getId())
                .reply(reply)
                .build();
    }

    /**
     * 流式心理咨询（对接硅基流动大模型）
     * 通过Flux获取大模型分段流式返回，用户提问先入库，AI回答在流式结束后（或断开时）入库
     *
     * @param commandDTO 流式咨询请求参数
     * @return 流式分段数据（每段为JSON：{"sessionId":..,"content":".."}）
     */
    public Flux<String> streamChat(AiChatCommandDTO commandDTO) {
        // 1. 获取当前登录用户并校验账号状态（禁用禁止发起AI咨询）
        LoginUser loginUser = getCurrentLoginUser();
        checkUserActive(loginUser.getUserId());
        Long userId = loginUser.getUserId();
        // 2. 解析会话：未传sessionId时自动新建会话
        ChatSession session = resolveSession(userId, commandDTO.getSessionId(), commandDTO.getContent());
        // 在本次问题入库前读取历史，避免重复加入本次问题。
        var sources = ragService==null ? java.util.List.<org.example.qispringboot.rag.RagService.Source>of() : ragService.search(commandDTO.getContent());
        Prompt prompt = buildConversationPrompt(session.getId(), commandDTO.getContent(),sources);
        // 3. 实时保存用户提问到聊天消息表
        saveMessage(session.getId(), SENDER_USER, commandDTO.getContent());
        // 5. 调用大模型流式接口，逐段处理
        StringBuilder aiContent = new StringBuilder();
        String citations=ragService==null ? "" : ragService.citations(sources);
        return chatClient.prompt(prompt).stream().content()
                .concatWith(citations.isEmpty() ? Flux.empty() : Flux.just(citations))
                // 6. 累积每个分段内容，并包装为带会话ID的JSON分段
                .doOnNext(aiContent::append)
                .map(segment -> wrapStreamSegment(session.getId(), segment))
                // 7. 记录调用异常（不中断SSE，由onErrorResume兜底）
                .doOnError(e -> log.error("AI流式调用失败，sessionId={}", session.getId(), e))
                // 8. 连接异常断开处理：正常完成/出错/客户端断开时，均保存已生成的AI回答
                .doFinally(signal -> {
                    if (aiContent.length() > 0) {
                        saveMessage(session.getId(), SENDER_AI, aiContent.toString());
                    }
                    if (signal == SignalType.CANCEL) {
                        log.warn("客户端连接断开，sessionId={}，已保存部分AI回答", session.getId());
                    }
                })
                // 9. 连接异常时向前端推送友好提示，保证SSE正常结束
                .onErrorResume(e -> Flux.just(
                        wrapStreamSegment(session.getId(), "（AI服务连接异常，请稍后重试）")));
    }

    /**
     * 将流式分段包装为带会话ID的JSON字符串
     *
     * @param sessionId 会话ID
     * @param content   分段内容
     * @return JSON字符串
     */
    private String wrapStreamSegment(Long sessionId, String content) {
        try {
            return objectMapper.writeValueAsString(Map.of("sessionId", sessionId, "content", content));
        } catch (JsonProcessingException e) {
            log.error("流式分段序列化失败，sessionId={}", sessionId, e);
            return "{\"sessionId\":" + sessionId + ",\"content\":\"\"}";
        }
    }

    /**
     * 为已校验归属的会话构建上下文。历史保存在数据库中，刷新页面后仍可继续对话。
     * 只取最近的文本消息，并按消息数和字符数限制历史长度；保留完整消息。
     */
    private Prompt buildConversationPrompt(Long sessionId, String question, List<org.example.qispringboot.rag.RagService.Source> sources) {
        List<ChatMessage> recentMessages = chatMessageMapper.selectList(
                new LambdaQueryWrapper<ChatMessage>()
                        .eq(ChatMessage::getSessionId, sessionId)
                        .eq(ChatMessage::getMessageType, MESSAGE_TYPE_TEXT)
                        .in(ChatMessage::getSenderType, SENDER_USER, SENDER_AI)
                        .orderByDesc(ChatMessage::getId)
                        .last("LIMIT " + MAX_HISTORY_MESSAGES));
        List<Message> history = new ArrayList<>();
        int characters = 0;
        for (ChatMessage message : recentMessages) {
            String content = message.getContent();
            if (!StringUtils.hasText(content)) {
                continue;
            }
            if (characters + content.length() > MAX_HISTORY_CHARACTERS) {
                break;
            }
            if (Integer.valueOf(SENDER_USER).equals(message.getSenderType())) {
                history.add(new UserMessage(content));
            } else if (Integer.valueOf(SENDER_AI).equals(message.getSenderType())) {
                history.add(new AssistantMessage(content));
            }
            characters += content.length();
        }
        Collections.reverse(history);
        // 窗口可能从某轮回答开始，移除缺少对应提问的开头回答。
        while (!history.isEmpty() && history.get(0) instanceof AssistantMessage) {
            history.remove(0);
        }
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(PromptManage.PSYCHOLOGICAL_SUPPORT_SYSTEM_PROMPT));
        if(ragService!=null) {
            String context=ragService.context(sources);
            if(!context.isBlank()) messages.add(new UserMessage(context));
        }
        messages.addAll(history);
        messages.add(new UserMessage(question));
        return new Prompt(messages);
    }
}
