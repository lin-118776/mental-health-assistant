package org.example.qispringboot.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.example.qispringboot.DTO.response.AdminChatDetailResponseDTO;
import org.example.qispringboot.DTO.response.AdminChatSessionResponseDTO;
import org.example.qispringboot.entity.ChatMessage;
import org.example.qispringboot.exception.BusinessException;
import org.example.qispringboot.mapper.AdminChatMapper;
import org.example.qispringboot.mapper.ChatMessageMapper;
import org.springframework.stereotype.Service;

@Service
public class AdminChatService {
    private final AdminChatMapper adminChatMapper;
    private final ChatMessageMapper chatMessageMapper;

    public AdminChatService(AdminChatMapper adminChatMapper, ChatMessageMapper chatMessageMapper) {
        this.adminChatMapper = adminChatMapper;
        this.chatMessageMapper = chatMessageMapper;
    }

    public Page<AdminChatSessionResponseDTO> page(long page, long size) {
        return adminChatMapper.selectSessions(new Page<>(Math.max(1, page), Math.max(1, Math.min(100, size))));
    }

    public AdminChatDetailResponseDTO detail(Long id) {
        var session = adminChatMapper.selectSession(id);
        if (session == null) throw new BusinessException("咨询会话不存在或已删除");
        var messages = chatMessageMapper.selectList(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getSessionId, id).orderByAsc(ChatMessage::getId));
        session.setMessageCount((long) messages.size());
        if (!messages.isEmpty()) {
            var latest = messages.get(messages.size() - 1);
            session.setLastMessageTime(latest.getCreatedAt());
            String content = latest.getContent();
            session.setLastMessageContent(content == null ? null : content.substring(0, Math.min(200, content.length())));
        }
        return new AdminChatDetailResponseDTO(session, messages);
    }
}
