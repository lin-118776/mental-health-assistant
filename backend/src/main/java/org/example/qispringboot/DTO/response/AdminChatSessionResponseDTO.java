package org.example.qispringboot.DTO.response;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class AdminChatSessionResponseDTO {
    private Long id;
    private Long userId;
    private String userNickname;
    private String userAvatar;
    private String sessionTitle;
    private Long messageCount;
    private String lastMessageContent;
    private LocalDateTime lastMessageTime;
}
