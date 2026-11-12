package org.example.qispringboot.DTO.response;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ChatSessionSummaryResponseDTO {
    private Long id;
    private Long userId;
    private String sessionTitle;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long messageCount;
    private String lastMessageContent;
    private LocalDateTime lastMessageTime;
    // 首末消息的时间跨度，不表示实际在线或持续咨询时长。
    private Long durationMinutes;
}
