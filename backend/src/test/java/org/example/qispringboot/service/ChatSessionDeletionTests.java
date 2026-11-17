package org.example.qispringboot.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.mapping.Environment;
import org.example.qispringboot.exception.BusinessException;
import org.example.qispringboot.mapper.ChatMessageMapper;
import org.example.qispringboot.mapper.ChatSessionMapper;
import org.example.qispringboot.mapper.UserMapper;
import org.example.qispringboot.security.LoginUser;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import javax.sql.DataSource;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ChatSessionDeletionTests {
    private AnnotationConfigApplicationContext context;
    private ChatService service;
    private JdbcTemplate jdbc;

    @Configuration
    @EnableTransactionManagement(proxyTargetClass = true)
    static class Config {
        @Bean DataSource dataSource() {
            var source = new JdbcDataSource();
            source.setURL("jdbc:h2:mem:sessiondeletion;MODE=MySQL;DB_CLOSE_DELAY=-1");
            return source;
        }
        @Bean DataSourceTransactionManager transactionManager(DataSource source) {
            return new DataSourceTransactionManager(source);
        }
        @Bean SqlSessionTemplate template(DataSource source) {
            var config = new MybatisConfiguration();
            config.setEnvironment(new Environment("test",new SpringManagedTransactionFactory(),source));
            config.addMapper(ChatSessionMapper.class);
            config.addMapper(ChatMessageMapper.class);
            return new SqlSessionTemplate(new MybatisSqlSessionFactoryBuilder().build(config));
        }
        @Bean ChatSessionMapper chatSessionMapper(SqlSessionTemplate template) {return template.getMapper(ChatSessionMapper.class);}
        @Bean ChatMessageMapper chatMessageMapper(SqlSessionTemplate template) {return template.getMapper(ChatMessageMapper.class);}
        @Bean UserMapper userMapper() {return mock(UserMapper.class);}
        @Bean EmotionAnalysisService emotionAnalysisService() {return mock(EmotionAnalysisService.class);}
        @Bean ChatService chatService() {
            var builder = mock(ChatClient.Builder.class);
            when(builder.build()).thenReturn(mock(ChatClient.class));
            return new ChatService(builder,new ObjectMapper());
        }
    }

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigApplicationContext(Config.class);
        service = context.getBean(ChatService.class);
        jdbc = new JdbcTemplate(context.getBean(DataSource.class));
        jdbc.execute("DROP ALL OBJECTS");
        jdbc.execute("CREATE TABLE chat_session (id BIGINT PRIMARY KEY, user_id BIGINT, session_title VARCHAR(200), last_emotion_analysis TEXT, last_emotion_updated_at TIMESTAMP, created_at TIMESTAMP, updated_at TIMESTAMP)");
        jdbc.execute("CREATE TABLE chat_message (id BIGINT PRIMARY KEY, session_id BIGINT, content TEXT, FOREIGN KEY(session_id) REFERENCES chat_session(id))");
        jdbc.execute("CREATE TABLE delete_blocker (session_id BIGINT, FOREIGN KEY(session_id) REFERENCES chat_session(id))");
        jdbc.update("INSERT INTO chat_session (id,user_id) VALUES (10,1),(20,2),(30,1)");
        jdbc.update("INSERT INTO chat_message VALUES (100,10,'我的问题'),(101,10,'AI回答'),(200,20,'其他用户消息')");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new LoginUser(1L,"owner",1),null,List.of()));
    }
    @AfterEach
    void close() {SecurityContextHolder.clearContext();context.close();}

    @Test
    void renameChangesOnlyOwnedTitleAndTimestampWithoutTouchingMessages() {
        service.renameSession(10L,"  考试压力记录  ");
        assertEquals("考试压力记录",jdbc.queryForObject("SELECT session_title FROM chat_session WHERE id=10",String.class));
        assertNotNull(jdbc.queryForObject("SELECT updated_at FROM chat_session WHERE id=10",java.sql.Timestamp.class));
        assertEquals(1L,jdbc.queryForObject("SELECT user_id FROM chat_session WHERE id=10",Long.class));
        assertEquals(2,count("chat_message","session_id",10));
        assertNull(jdbc.queryForObject("SELECT session_title FROM chat_session WHERE id=20",String.class));
        service.renameSession(10L,"标".repeat(200));
        assertEquals(200,jdbc.queryForObject("SELECT session_title FROM chat_session WHERE id=10",String.class).length());
    }
    @Test
    void invalidForeignMissingAndAnonymousRenamesLeaveRecordsUntouched() {
        for (String title : List.of("", "   ", "\n\t", "标".repeat(201)))
            assertThrows(BusinessException.class,()->service.renameSession(10L,title));
        assertThrows(BusinessException.class,()->service.renameSession(10L,null));
        assertThrows(BusinessException.class,()->service.renameSession(20L,"不能修改"));
        assertThrows(BusinessException.class,()->service.renameSession(999L,"已删除会话"));
        SecurityContextHolder.clearContext();
        assertThrows(BusinessException.class,()->service.renameSession(10L,"未登录"));
        assertNull(jdbc.queryForObject("SELECT session_title FROM chat_session WHERE id=10",String.class));
        assertNull(jdbc.queryForObject("SELECT session_title FROM chat_session WHERE id=20",String.class));
    }
    @Test
    void successfulDeletionRemovesOnlyOwnedSessionAndItsMessages() {
        service.deleteSession(10L);
        assertEquals(0,count("chat_session","id",10));
        assertEquals(0,count("chat_message","session_id",10));
        assertEquals(1,count("chat_session","id",20));
        assertEquals(1,count("chat_message","session_id",20));
        service.deleteSession(30L);
        assertEquals(0,count("chat_session","id",30));
    }
    @Test
    void failedParentDeletionRollsBackEarlierMessageDeletion() {
        jdbc.update("INSERT INTO delete_blocker VALUES (10)");
        assertThrows(RuntimeException.class,()->service.deleteSession(10L));
        assertEquals(1,count("chat_session","id",10));
        assertEquals(2,count("chat_message","session_id",10));
        jdbc.update("DELETE FROM delete_blocker");
        assertDoesNotThrow(()->service.deleteSession(10L));
        assertEquals(0,count("chat_message","session_id",10));
    }
    @Test
    void foreignMissingAndUnauthenticatedDeletionCannotRemoveMessages() {
        assertThrows(BusinessException.class,()->service.deleteSession(20L));
        assertThrows(BusinessException.class,()->service.deleteSession(999L));
        SecurityContextHolder.clearContext();
        assertThrows(BusinessException.class,()->service.deleteSession(10L));
        assertEquals(2,count("chat_message","session_id",10));
        assertEquals(1,count("chat_message","session_id",20));
    }
    private int count(String table,String field,long id) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE " + field + " = ?",Integer.class,id);
    }
}
