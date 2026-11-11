package org.example.qispringboot.DTO.response;

import org.example.qispringboot.entity.ChatMessage;
import java.util.List;

public record AdminChatDetailResponseDTO(AdminChatSessionResponseDTO session, List<ChatMessage> messages) {}
