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
import org.example.qispringboot.mapper.ChatSessionMapper;
import org.example.qispringboot.security.LoginUser;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.*;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ChatSessionPaginationTests {
    private SqlSession sql;
    private ChatService service;

    @BeforeEach
    void setUp() throws Exception {
        var source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:personalchatpages;MODE=MySQL;DB_CLOSE_DELAY=-1");
        try (var connection = source.getConnection(); var statement = connection.createStatement()) {
            statement.execute("DROP ALL OBJECTS");
            statement.execute("CREATE TABLE chat_session (id BIGINT PRIMARY KEY, user_id BIGINT, session_title VARCHAR(200), last_emotion_analysis TEXT, last_emotion_updated_at TIMESTAMP, created_at TIMESTAMP, updated_at TIMESTAMP)");
            for (int id = 1; id <= 23; id++)
                statement.execute("INSERT INTO chat_session (id,user_id,session_title,created_at) VALUES (" + id + ",1,'我的会话','2026-11-12 10:00:00')");
            statement.execute("INSERT INTO chat_session (id,user_id,session_title,created_at) VALUES (99,2,'其他用户会话','2026-11-12 11:00:00')");
        }
        var config = new MybatisConfiguration();
        config.setEnvironment(new Environment("test",new JdbcTransactionFactory(),source));
        var interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        config.addInterceptor(interceptor);
        config.addMapper(ChatSessionMapper.class);
        sql = new MybatisSqlSessionFactoryBuilder().build(config).openSession();
        var builder = mock(ChatClient.Builder.class);
        when(builder.build()).thenReturn(mock(ChatClient.class));
        service = new ChatService(builder,new ObjectMapper());
        ReflectionTestUtils.setField(service,"chatSessionMapper",sql.getMapper(ChatSessionMapper.class));
        login(1L);
    }

    @AfterEach
    void close() {
        SecurityContextHolder.clearContext();
        if (sql != null) sql.close();
    }

    @Test
    void pagesReachAllOwnedSessionsWithStableOrderingWhenCreationTimesMatch() {
        var seen = new HashSet<Long>();
        for (long page = 1; page <= 3; page++) {
            var result = service.pageSessions(page,10);
            assertEquals(23,result.getTotal());
            assertEquals(page == 3 ? 3 : 10,result.getRecords().size());
            assertEquals(23 - (page - 1) * 10,result.getRecords().get(0).getId());
            for (var record : result.getRecords()) {
                assertEquals(1L,record.getUserId());
                assertTrue(seen.add(record.getId()),"会话不能跨页重复");
            }
        }
        assertEquals(23,seen.size());
        assertFalse(seen.contains(99L));
        assertTrue(service.pageSessions(4,10).getRecords().isEmpty());
    }

    @Test
    void eachUserSeesOnlyTheirOwnTotalAndRecordsAndAnonymousAccessIsRejected() {
        login(2L);
        var result = service.pageSessions(1,10);
        assertEquals(1,result.getTotal());
        assertEquals(99L,result.getRecords().get(0).getId());
        login(3L);
        assertEquals(0,service.pageSessions(1,10).getTotal());
        SecurityContextHolder.clearContext();
        assertThrows(BusinessException.class,() -> service.pageSessions(1,10));
    }

    @Test
    void invalidPageAndSizeAreBoundedBeforeExecutingQuery() {
        var small = service.pageSessions(-1,0);
        assertEquals(1,small.getCurrent());
        assertEquals(1,small.getSize());
        var large = service.pageSessions(1,10000);
        assertEquals(100,large.getSize());
        assertEquals(23,large.getRecords().size());
    }

    private void login(Long userId) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new LoginUser(userId,"test",1),null,List.of()));
    }
}
