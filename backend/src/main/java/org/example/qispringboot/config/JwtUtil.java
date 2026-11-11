package org.example.qispringboot.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import javax.crypto.Mac;
import java.security.MessageDigest;
import java.util.Base64;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT工具类
 * 提供生成token、解析token、校验是否过期等方法
 */
@Component
public class JwtUtil {

    // 自定义声明：用户ID
    private static final String CLAIM_USER_ID = "userId";

    // 自定义声明：用户类型（1:普通用户 2:管理员）
    private static final String CLAIM_USER_TYPE = "userType";

    @Resource
    private JwtProperties jwtProperties;

    /**
     * 根据配置的密钥生成HS256签名密钥对象
     */
    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 生成JWT令牌
     *
     * @param userId   用户ID
     * @param username 用户名
     * @param userType 用户类型（1:普通用户 2:管理员）
     * @return 生成的JWT令牌字符串
     */
    public String generateToken(Long userId, String username, Integer userType, String encodedPassword) {
        // 当前时间
        Date now = new Date();
        // 过期时间 = 当前时间 + 配置的过期时长
        Date expirationTime = new Date(now.getTime() + jwtProperties.getExpiration());
        return Jwts.builder()
                .subject(username)                    // 主题：用户名
                .claim(CLAIM_USER_ID, userId)         // 自定义声明：用户ID
                .claim(CLAIM_USER_TYPE, userType)     // 自定义声明：用户类型
                .claim("passwordVersion", passwordVersion(encodedPassword))
                .issuedAt(now)                        // 签发时间
                .expiration(expirationTime)           // 过期时间
                .signWith(getSigningKey())            // 使用HS256算法签名
                .compact();
    }

    // 凭证中仅存储带服务端密钥的指纹，不存储密码或BCrypt哈希。
    private String passwordVersion(String encodedPassword) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(getSigningKey());
            return Base64.getUrlEncoder().withoutPadding().encodeToString(
                    mac.doFinal(encodedPassword.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("无法生成登录凭证版本", e);
        }
    }

    public boolean isPasswordCurrent(Claims claims, String encodedPassword) {
        String version = claims.get("passwordVersion", String.class);
        return version != null && encodedPassword != null && MessageDigest.isEqual(
                version.getBytes(StandardCharsets.UTF_8), passwordVersion(encodedPassword).getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 解析token，获取Claims声明体（校验签名与有效期）
     *
     * @param token JWT令牌
     * @return Claims声明体
     * @throws JwtException 签名错误、token格式错误或已过期时抛出
     */
    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * 从token中获取用户ID
     */
    public Long getUserId(String token) {
        Number userId = parseToken(token).get(CLAIM_USER_ID, Number.class);
        return userId != null ? userId.longValue() : null;
    }

    /**
     * 从token中获取用户名
     */
    public String getUsername(String token) {
        return parseToken(token).getSubject();
    }

    /**
     * 从token中获取用户类型
     */
    public Integer getUserType(String token) {
        Number userType = parseToken(token).get(CLAIM_USER_TYPE, Number.class);
        return userType != null ? userType.intValue() : null;
    }

    /**
     * 校验token是否过期
     *
     * @param token JWT令牌
     * @return true:已过期或无效；false:未过期且有效
     */
    public boolean isExpired(String token) {
        try {
            Claims claims = parseToken(token);
            Date expiration = claims.getExpiration();
            return expiration != null && expiration.before(new Date());
        } catch (JwtException | IllegalArgumentException e) {
            // 解析失败视为无效token，按已过期处理
            return true;
        }
    }

    /**
     * 校验token是否有效（签名正确且未过期）
     */
    public boolean validateToken(String token) {
        return !isExpired(token);
    }
}
