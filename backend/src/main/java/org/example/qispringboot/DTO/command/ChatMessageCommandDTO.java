package org.example.qispringboot.DTO.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 发起心理咨询命令DTO
 * 接收用户发送的消息内容
 */
@Data
public class ChatMessageCommandDTO {

    // 会话ID（可选，为空时自动新建会话）
    private Long sessionId;

    // 消息内容
    @NotBlank(message = "消息内容不能为空")
    @Size(max = 4000, message = "消息内容不能超过4000个字符")
    private String content;
}