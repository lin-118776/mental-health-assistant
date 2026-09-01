package org.example.qispringboot.DTO;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 用户登录命令DTO
 * 接收前端登录请求参数
 */
@Data
public class UserLoginCommandDTO {

    // 用户名或邮箱
    @NotBlank(message = "用户名或邮箱不能为空")
    @Size(max = 100, message = "用户名或邮箱最多100个字符")
    private String username;

    // 密码
    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 50, message = "密码长度必须在6到50个字符之间")
    private String password;
}