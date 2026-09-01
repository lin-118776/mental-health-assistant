package org.example.qispringboot.DTO.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * AI流式咨询命令DTO
 * 接收前端流式对话请求参数
 */
@Data
public class AiChatCommandDTO {

    // 会话ID（可选，为空时自动新建会话）
    private Long sessionId;

    // 用户消息内容
    @NotBlank(message = "消息内容不能为空")
    @Size(max = 4000, message = "消息内容不能超过4000个字符")
    private String content;
}