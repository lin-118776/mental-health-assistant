package org.example.qispringboot.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.example.qispringboot.entity.User;
import org.example.qispringboot.mapper.UserMapper;
import org.example.qispringboot.DTO.UserLoginCommandDTO;
import org.example.qispringboot.service.UserService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PasswordTokenTests {
    private JwtUtil jwt;
    private JwtProperties properties;
    private JwtAuthenticationFilter filter;
    private User user;

    @BeforeEach
    void setUp() {
        properties = new JwtProperties();
        properties.setSecret("test-only-signing-key-with-at-least-32-bytes");
        jwt = new JwtUtil();
        ReflectionTestUtils.setField(jwt, "jwtProperties", properties);
        UserMapper users = mock(UserMapper.class);
        user = User.builder().id(7L).password("encoded-password-before").build();
        when(users.selectById(7L)).thenReturn(user);
        filter = new JwtAuthenticationFilter();
        ReflectionTestUtils.setField(filter, "jwtUtil", jwt);
        ReflectionTestUtils.setField(filter, "jwtProperties", properties);
        ReflectionTestUtils.setField(filter, "userMapper", users);
        ReflectionTestUtils.setField(filter, "objectMapper", new ObjectMapper());
    }

    @AfterEach
    void clear() { SecurityContextHolder.clearContext(); }

    @Test
    void currentTokenPassesAndDoesNotContainPasswordHash() throws Exception {
        String token = jwt.generateToken(7L, "owner", 1, user.getPassword());
        assertFalse(jwt.parseToken(token).values().contains(user.getPassword()));
        assertEquals(200, authenticate(token, true));
        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void changingPasswordRevokesAllOldTokensAndNewTokenWorks() throws Exception {
        String old = jwt.generateToken(7L, "owner", 1, user.getPassword());
        user.setPassword("encoded-password-after");
        assertEquals(401, authenticate(old, false));
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        String next = jwt.generateToken(7L, "owner", 1, user.getPassword());
        assertEquals(200, authenticate(next, true));
    }

    @Test
    void profileChangesDoNotRevokeToken() throws Exception {
        String token = jwt.generateToken(7L, "owner", 1, user.getPassword());
        user.setNickname("新昵称");
        user.setEmail("new@example.com");
        assertEquals(200, authenticate(token, true));
    }

    @Test
    void legacyTokensCannotBypassPasswordRevocation() throws Exception {
        String legacy = Jwts.builder().subject("owner").claim("userId", 7L).claim("userType", 1)
                .expiration(new Date(System.currentTimeMillis() + 60000))
                .signWith(Keys.hmacShaKeyFor(properties.getSecret().getBytes(StandardCharsets.UTF_8))).compact();
        assertEquals(401, authenticate(legacy, false));
    }

    @Test
    void realLoginIssuesPasswordVersionToken() {
        var users = mock(UserMapper.class);
        var encoder = new BCryptPasswordEncoder(4);
        User account = User.builder().id(7L).username("owner").status(1).userType(1)
                .password(encoder.encode("old-pass")).build();
        when(users.selectOne(org.mockito.ArgumentMatchers.any(com.baomidou.mybatisplus.core.conditions.Wrapper.class)))
                .thenReturn(account);
        var loginService = new UserService();
        ReflectionTestUtils.setField(loginService, "userMapper", users);
        ReflectionTestUtils.setField(loginService, "passwordEncoder", encoder);
        ReflectionTestUtils.setField(loginService, "jwtUtil", jwt);
        var command = new UserLoginCommandDTO();
        command.setUsername("owner");
        command.setPassword("old-pass");
        var response = loginService.login(command).getData();
        assertTrue(jwt.isPasswordCurrent(jwt.parseToken(response.getToken()), account.getPassword()));
    }

    private int authenticate(String token, boolean shouldPass) throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/user/me");
        request.addHeader("Authorization", "Bearer " + token);
        var response = new MockHttpServletResponse();
        var passed = new AtomicBoolean(false);
        filter.doFilter(request, response, (req, res) -> passed.set(true));
        assertEquals(shouldPass, passed.get());
        return response.getStatus();
    }
}
