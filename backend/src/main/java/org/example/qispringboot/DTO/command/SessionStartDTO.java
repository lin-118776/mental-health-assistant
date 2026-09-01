package org.example.qispringboot.DTO.command;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 开始咨询会话命令DTO
 */
@Data
public class SessionStartDTO {

    // 会话标题（可选）
    @Size(max = 200, message = "会话标题不能超过200个字符")
    private String title;
}