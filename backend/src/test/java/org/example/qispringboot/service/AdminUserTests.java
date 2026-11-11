package org.example.qispringboot.service;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.example.qispringboot.exception.BusinessException;
import org.example.qispringboot.mapper.UserMapper;
import org.example.qispringboot.security.LoginUser;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AdminUserTests {
    private SqlSession sql;
    private UserMapper mapper;
    private AdminUserService service;

    @BeforeEach
    void setUp() throws Exception {
        var source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:adminusers;MODE=MySQL;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=USER;DB_CLOSE_DELAY=-1");
        try (var connection = source.getConnection(); var statement = connection.createStatement()) {
            statement.execute("DROP ALL OBJECTS");
            statement.execute("CREATE TABLE user (id BIGINT PRIMARY KEY, username VARCHAR(50), nickname VARCHAR(50), email VARCHAR(100), phone VARCHAR(20), password VARCHAR(255), avatar VARCHAR(255), gender INT, birthday DATE, user_type INT, status INT, created_at TIMESTAMP, updated_at TIMESTAMP)");
            statement.execute("INSERT INTO user (id, username, nickname, email, phone, password, user_type, status, created_at) VALUES (7, 'admin', '管理员', 'admin@example.com', '13800138007', 'secret-hash', 2, 1, '2026-11-01 10:00:00'), (10, 'ming-active', '小明', 'ming@example.com', '13800138010', 'hash10', 1, 1, '2026-11-02 10:00:00'), (11, 'ming-disabled', '明明', 'disabled@example.com', '13800138011', 'hash11', 1, 0, '2026-11-03 10:00:00'), (12, 'other', '小红', 'other@example.com', '13800138012', 'hash12', 1, 1, '2026-11-04 10:00:00')");
        }
        var config = new MybatisConfiguration();
        config.setEnvironment(new Environment("test", new JdbcTransactionFactory(), source));
        var pagination = new MybatisPlusInterceptor();
        pagination.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        config.addInterceptor(pagination);
        config.addMapper(UserMapper.class);
        sql = new MybatisSqlSessionFactoryBuilder().build(config).openSession();
        mapper = sql.getMapper(UserMapper.class);
        service = new AdminUserService(mapper);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new LoginUser(7L, "admin", 2), null, List.of()));
    }

    @AfterEach
    void close() {
        SecurityContextHolder.clearContext();
        if (sql != null) sql.close();
    }

    @Test
    void paginatesAndNeverSerializesPassword() throws Exception {
        var first = service.page(null, null, 1, 2);
        assertEquals(4, first.getTotal());
        assertEquals(12L, first.getRecords().get(0).id());
        assertEquals(11L, first.getRecords().get(1).id());
        var second = service.page(null, null, 2, 2);
        assertEquals(10L, second.getRecords().get(0).id());
        var json = new ObjectMapper().findAndRegisterModules().writeValueAsString(second);
        assertFalse(json.contains("password"));
        assertFalse(json.contains("secret-hash"));
        var bounded = service.page(null, null, -1, 10000);
        assertEquals(1, bounded.getCurrent());
        assertEquals(100, bounded.getSize());
    }

    @Test
    void combinesStatusWithGroupedKeywordSearch() {
        var result = service.page(" ming ", 0, 1, 10);
        assertEquals(1, result.getTotal());
        assertEquals(11L, result.getRecords().get(0).id());
        assertEquals(10L, service.page("小明", null, 1, 10).getRecords().get(0).id());
        assertEquals(12L, service.page("other@example.com", null, 1, 10).getRecords().get(0).id());
        assertEquals(10L, service.page("13800138010", null, 1, 10).getRecords().get(0).id());
        assertEquals(0, service.page("' OR 1=1 --", null, 1, 10).getTotal());
    }

    @Test
    void changesOnlyNormalUsersStatusAndPreservesOtherAccountData() {
        service.updateStatus(10L, 0);
        var user = mapper.selectById(10L);
        assertEquals(0, user.getStatus());
        assertEquals("hash10", user.getPassword());
        assertEquals("ming-active", user.getUsername());
        assertEquals(1, user.getUserType());
        assertNotNull(user.getUpdatedAt());
        assertEquals(4, service.page(null, null, 1, 10).getTotal());
        service.updateStatus(10L, 1);
        assertEquals(1, mapper.selectById(10L).getStatus());
    }

    @Test
    void preventsSelfAndOtherAdministratorChanges() throws Exception {
        assertThrows(BusinessException.class, () -> service.updateStatus(7L, 0));
        try (var statement = sql.getConnection().createStatement()) {
            statement.execute("UPDATE user SET user_type=2 WHERE id=12");
        }
        sql.clearCache();
        assertThrows(BusinessException.class, () -> service.updateStatus(12L, 0));
        assertEquals(1, mapper.selectById(7L).getStatus());
        assertEquals(1, mapper.selectById(12L).getStatus());
    }

    @Test
    void rejectsInvalidFiltersAndMissingUsers() {
        assertThrows(BusinessException.class, () -> service.page(null, 2, 1, 10));
        assertThrows(BusinessException.class, () -> service.page("x".repeat(101), null, 1, 10));
        assertThrows(BusinessException.class, () -> service.updateStatus(999L, 0));
        assertThrows(BusinessException.class, () -> service.updateStatus(10L, 2));
        assertThrows(BusinessException.class, () -> service.updateStatus(10L, null));
    }

    @Test
    void requiresLoginForStatusChanges() {
        SecurityContextHolder.clearContext();
        assertThrows(BusinessException.class, () -> service.updateStatus(10L, 0));
        assertEquals(1, mapper.selectById(10L).getStatus());
    }
}
