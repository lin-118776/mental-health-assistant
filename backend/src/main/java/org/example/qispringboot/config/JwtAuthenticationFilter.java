package org.example.qispringboot.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.annotation.Resource;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.example.qispringboot.common.Result;
import org.example.qispringboot.common.ResultCode;
import org.example.qispringboot.security.LoginUser;
import org.example.qispringboot.mapper.UserMapper;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * JWT认证过滤器
 * 在Security过滤器链中校验请求头Token，解析用户信息并存入Security上下文
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    @Resource
    private JwtUtil jwtUtil;

    @Resource
    private UserMapper userMapper;

    @Resource
    private JwtProperties jwtProperties;

    @Resource
    private ObjectMapper objectMapper;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return "POST".equals(request.getMethod()) && List.of("/api/user/login", "/api/user/add", "/api/user/register")
                .contains(request.getServletPath());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        // 1. 从请求头中获取token（例如 Authorization: Bearer xxx）
        String header = request.getHeader(jwtProperties.getHeader());
        // 2. 未携带token或前缀不匹配时，放行交给Security的认证入口点处理（返回401 JSON）
        if (!StringUtils.hasText(header) || !header.startsWith(jwtProperties.getTokenPrefix())) {
            filterChain.doFilter(request, response);
            return;
        }
        // 3. 去掉token前缀，得到纯token字符串
        String token = header.substring(jwtProperties.getTokenPrefix().length());
        try {
            // 4. 解析token，校验签名与有效期
            Claims claims = jwtUtil.parseToken(token);
            // 5. 从token中解析用户信息
            Long userId = jwtUtil.getUserId(token);
            var user = userMapper.selectById(userId);
            if (user == null || !jwtUtil.isPasswordCurrent(claims, user.getPassword())) {
                SecurityContextHolder.clearContext();
                writeErrorResponse(response, ResultCode.TOKEN_INVALID);
                return;
            }
            if (user.isDisabled()) {
                SecurityContextHolder.clearContext();
                writeErrorResponse(response, ResultCode.ACCOUNT_DISABLED);
                return;
            }
            String username = user.getUsername();
            Integer userType = user.getUserType();
            // 6. 根据用户类型构建角色（2:管理员，其余为普通用户）
            String role = Integer.valueOf(2).equals(userType) ? "ROLE_ADMIN" : "ROLE_USER";
            // 7. 构建登录用户对象并封装为认证信息
            LoginUser loginUser = new LoginUser(userId, username, userType);
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(loginUser, null,
                            List.of(new SimpleGrantedAuthority(role)));
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            // 8. 将认证信息写入Security上下文
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (ExpiredJwtException e) {
            // token已过期：返回统一格式的JSON错误响应
            writeErrorResponse(response, ResultCode.TOKEN_EXPIRED);
            return;
        } catch (JwtException | IllegalArgumentException e) {
            // token无效（签名错误、格式错误等）：返回统一格式的JSON错误响应
            writeErrorResponse(response, ResultCode.TOKEN_INVALID);
            return;
        } catch (Exception e) {
            // 其他认证异常：统一返回未授权
            writeErrorResponse(response, ResultCode.UNAUTHORIZED);
            return;
        }
        // 9. 认证通过，继续执行后续过滤器链
        filterChain.doFilter(request, response);
    }

    /**
     * 以Result统一返回格式写出认证异常JSON响应
     */
    private void writeErrorResponse(HttpServletResponse response, ResultCode resultCode) throws IOException {
        // 设置HTTP状态码为401
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        // 设置响应内容类型为JSON
        response.setContentType("application/json;charset=UTF-8");
        // 使用Jackson将Result对象序列化为JSON字符串写入响应体
        response.getWriter().write(objectMapper.writeValueAsString(
                Result.error(resultCode.getCode(), resultCode.getMsg(), null)));
    }
}
