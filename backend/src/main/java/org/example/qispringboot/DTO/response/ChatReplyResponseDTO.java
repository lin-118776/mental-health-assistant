package org.example.qispringboot.DTO.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 心理咨询回复响应DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatReplyResponseDTO {

    // 会话ID
    private Long sessionId;

    // 用户问题消息ID
    private Long userMessageId;

    // AI回复消息ID
    private Long aiMessageId;

    // AI回复内容
    private String reply;
}