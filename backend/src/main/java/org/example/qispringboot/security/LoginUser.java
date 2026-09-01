package org.example.qispringboot.security;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 登录用户信息
 * 认证成功后作为Authentication的主体（principal）存入SecurityContext
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginUser {

    // 用户ID
    private Long userId;

    // 用户名
    private String username;

    // 用户类型（1:普通用户 2:管理员）
    private Integer userType;
}