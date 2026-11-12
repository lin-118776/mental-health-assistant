package org.example.qispringboot.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.example.qispringboot.DTO.UserRegisterCommandDTO;
import org.example.qispringboot.common.GlobarExceptionHandler;
import org.example.qispringboot.controller.UserController;
import org.example.qispringboot.entity.User;
import org.example.qispringboot.exception.BusinessException;
import org.example.qispringboot.mapper.UserMapper;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class UserRegistrationTests {
    private SqlSession sql;
    private UserMapper users;
    private UserService service;
    private MockMvc mvc;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);

    @BeforeEach
    void setUp() throws Exception {
        var source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:registration;MODE=MySQL;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=USER;DB_CLOSE_DELAY=-1");
        try (var connection = source.getConnection(); var statement = connection.createStatement()) {
            statement.execute("DROP ALL OBJECTS");
            String schema = Files.readString(Path.of(System.getProperty("basedir", "."), "sql/schema.sql"), StandardCharsets.UTF_8)
                    .replaceAll("(?im)^CREATE DATABASE[^;]*;\\s*", "").replaceAll("(?im)^USE [^;]*;\\s*", "")
                    .replaceAll("(?im)^SET NAMES [^;]*;\\s*", "").replaceAll("(?m)^\\) ENGINE=[^;]*;", ");");
            ScriptUtils.executeSqlScript(connection, new EncodedResource(new ByteArrayResource(schema.getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8));
        }
        var config = new MybatisConfiguration();
        config.setEnvironment(new Environment("test", new JdbcTransactionFactory(), source));
        config.addMapper(UserMapper.class);
        sql = new MybatisSqlSessionFactoryBuilder().build(config).openSession(true);
        users = sql.getMapper(UserMapper.class);
        service = new UserService();
        ReflectionTestUtils.setField(service, "userMapper", users);
        ReflectionTestUtils.setField(service, "passwordEncoder", encoder);
        var controller = new UserController();
        ReflectionTestUtils.setField(controller, "userService", service);
        mvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new GlobarExceptionHandler()).build();
    }

    @AfterEach
    void close() { if (sql != null) sql.close(); }

    @Test
    void registrationSavesNicknameAndEncryptedPasswordButIgnoresForgedPrivileges() throws Exception {
        mvc.perform(post("/api/user/add").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"new-user\",\"phone\":\"13800138000\",\"password\":\"new-pass\",\"nickname\":\"小明\",\"userType\":2,\"status\":0,\"id\":999}"))
                .andExpect(jsonPath("$.code").value("400")); // 用户名中的连字符不合法。
        mvc.perform(post("/api/user/add").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"new_user\",\"phone\":\"13800138000\",\"password\":\"new-pass\",\"nickname\":\"小明\",\"userType\":2,\"status\":0,\"id\":999}"))
                .andExpect(jsonPath("$.code").value("200"));
        var user = find("new_user");
        assertEquals("小明", user.getNickname());
        assertEquals(1, user.getUserType());
        assertEquals(1, user.getStatus());
        assertNotEquals(999L, user.getId());
        assertTrue(encoder.matches("new-pass", user.getPassword()));
        assertNotEquals("new-pass", user.getPassword());
    }

    @Test
    void duplicateUsernamePhoneAndEmailAreRejected() {
        service.register(dto("first", "13800138000", "used@example.com"));
        assertThrows(BusinessException.class, () -> service.register(dto("first", "13800138001", null)));
        assertThrows(BusinessException.class, () -> service.register(dto("second", "13800138000", null)));
        assertThrows(BusinessException.class, () -> service.register(dto("second", "13800138001", "used@example.com")));
        assertEquals(1, users.selectCount(null));
    }

    @Test
    void optionalEmailIsStoredAsNullAndMultipleUsersCanLeaveItEmpty() {
        var first = dto("first", "13800138000", "");
        first.setNickname("  昵称  ");
        service.register(first);
        service.register(dto("second", "13800138001", null));
        assertNull(find("first").getEmail());
        assertNull(find("second").getEmail());
        assertEquals("昵称", find("first").getNickname());
        assertEquals(2, users.selectCount(null));
    }

    @Test
    void invalidRegistrationFieldsNeverCreateUser() throws Exception {
        for (String body : List.of("{}", "{\"username\":\"valid_user\",\"password\":\"new-pass\"}",
                "{\"username\":\"valid_user\",\"phone\":\"13800138000\",\"password\":\"123\"}",
                "{\"username\":\"valid_user\",\"phone\":\"13800138000\",\"password\":\"new-pass\",\"email\":\"invalid\"}",
                "{\"username\":\"valid_user\",\"phone\":\"13800138000\",\"password\":\"new-pass\",\"nickname\":\"" + "字".repeat(51) + "\"}")) {
            mvc.perform(post("/api/user/add").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(jsonPath("$.code").value("400"));
        }
        assertEquals(0, users.selectCount(null));
    }

    @Test
    void longMultibytePasswordIsRejectedBeforeEncodingOrInserting() {
        var dto = dto("first", "13800138000", null);
        dto.setPassword("密".repeat(25));
        assertThrows(BusinessException.class, () -> service.register(dto));
        assertEquals(0, users.selectCount(null));
    }

    @Test
    void databaseUniqueEmailConstraintProtectsAgainstDuplicateInsertion() {
        service.register(dto("first", "13800138000", "used@example.com"));
        assertThrows(org.apache.ibatis.exceptions.PersistenceException.class, () -> users.insert(User.builder()
                .username("second").phone("13800138001").password("test-hash").email("used@example.com").build()));
    }

    @Test
    void concurrentDuplicateKeyFailureBecomesFriendlyRegistrationError() {
        var mapper = mock(UserMapper.class);
        when(mapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        when(mapper.insert(any(User.class))).thenThrow(new DuplicateKeyException("test duplicate"));
        ReflectionTestUtils.setField(service, "userMapper", mapper);
        var error = assertThrows(BusinessException.class, () -> service.register(dto("first", "13800138000", null)));
        assertTrue(error.getMessage().contains("已被注册"));
    }

    private User find(String username) {
        return users.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, username));
    }

    private UserRegisterCommandDTO dto(String username, String phone, String email) {
        var dto = new UserRegisterCommandDTO();
        dto.setUsername(username);
        dto.setPhone(phone);
        dto.setEmail(email);
        dto.setPassword("new-pass");
        return dto;
    }
}
