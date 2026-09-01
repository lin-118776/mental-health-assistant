package org.example.qispringboot.entity;

import lombok.Getter;

/**
 * 用户类型枚举
 * 1:普通用户 2:管理员
 */
@Getter
public enum UserType {

    // 普通用户
    USER(1, "普通用户"),
    // 管理员
    ADMIN(2, "管理员");

    // 类型代码
    private final Integer code;

    // 类型描述
    private final String description;

    UserType(Integer code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * 根据代码获取枚举
     */
    public static UserType fromCode(Integer code) {
        for (UserType type : UserType.values()) {
            if (type.getCode().equals(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("未知的用户类型代码: " + code);
    }

    /**
     * 验证用户类型代码是否有效
     */
    public static boolean isValidCode(Integer code) {
        for (UserType type : UserType.values()) {
            if (type.getCode().equals(code)) {
                return true;
            }
        }
        return false;
    }
}