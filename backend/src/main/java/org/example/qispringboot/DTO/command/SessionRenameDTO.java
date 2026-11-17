package org.example.qispringboot.DTO.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SessionRenameDTO {
    @NotBlank(message = "会话标题不能为空")
    @Size(max = 200, message = "会话标题不能超过200个字符")
    private String title;
}
