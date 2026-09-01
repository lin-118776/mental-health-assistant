package org.example.qispringboot.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * JWT配置属性类
 * 负责读取 application.yml 中 jwt.* 开头的配置项
 */
@Data
@Component
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {

    // 签名密钥（长度不少于32字节，用于HS256签名算法）
    private String secret;

    // token过期时间（毫秒），默认24小时
    private Long expiration = 86400000L;

    // 刷新token过期时间（毫秒），默认7天
    private Long refreshExpiration = 604800000L;

    // 存放token的请求头名称
    private String header = "Authorization";

    // token前缀
    private String tokenPrefix = "Bearer ";
}