package org.example.qispringboot.service;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.example.qispringboot.exception.BusinessException;
import org.example.qispringboot.mapper.AdminChatMapper;
import org.example.qispringboot.mapper.ChatMessageMapper;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

class AdminChatTests {
    private SqlSession sqlSession;
    private AdminChatService service;

    @BeforeEach
    void setUp() throws Exception {
        var dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:adminchat;MODE=MySQL;DB_CLOSE_DELAY=-1");
        try (var connection = dataSource.getConnection(); var sql = connection.createStatement()) {
            sql.execute("DROP ALL OBJECTS");
            sql.execute("CREATE TABLE `user` (id BIGINT PRIMARY KEY, nickname VARCHAR(50), username VARCHAR(50), avatar VARCHAR(255))");
            sql.execute("CREATE TABLE chat_session (id BIGINT PRIMARY KEY, user_id BIGINT, session_title VARCHAR(200), created_at TIMESTAMP)");
            sql.execute("CREATE TABLE chat_message (id BIGINT PRIMARY KEY, session_id BIGINT, sender_type INT, message_type INT, content VARCHAR(1000), emotion_tag VARCHAR(50), ai_model VARCHAR(50), created_at TIMESTAMP)");
            sql.execute("INSERT INTO `user` VALUES (1, '小明', 'ming', 'avatar.png'), (2, '', 'hong', NULL)");
            sql.execute("INSERT INTO chat_session VALUES (10, 1, '考试压力', '2026-11-10 09:00:00'), (20, 2, '生活记录', '2026-11-11 09:00:00'), (30, 99, '空会话', '2026-11-11 10:00:00')");
            sql.execute("INSERT INTO chat_message VALUES (100, 10, 1, 1, '我很紧张', NULL, NULL, '2026-11-10 09:00:00'), (101, 10, 2, 1, '先休息一下', NULL, NULL, '2026-11-10 09:01:00'), (200, 20, 1, 1, '今天很开心', NULL, NULL, '2026-11-11 09:00:00')");
        }
        var config = new MybatisConfiguration();
        config.setEnvironment(new Environment("test", new JdbcTransactionFactory(), dataSource));
        var interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        config.addInterceptor(interceptor);
        config.addMapper(AdminChatMapper.class);
        config.addMapper(ChatMessageMapper.class);
        sqlSession = new MybatisSqlSessionFactoryBuilder().build(config).openSession();
        service = new AdminChatService(sqlSession.getMapper(AdminChatMapper.class), sqlSession.getMapper(ChatMessageMapper.class));
    }

    @AfterEach
    void close() {
        if (sqlSession != null) sqlSession.close();
    }

    @Test
    void paginationIncludesOtherUsersAndAccurateSummaries() {
        var first = service.page(1, 2);
        assertEquals(3, first.getTotal());
        assertEquals(2, first.getRecords().size());
        assertEquals(30L, first.getRecords().get(0).getId());
        assertEquals("用户 #99", first.getRecords().get(0).getUserNickname());
        assertEquals(0L, first.getRecords().get(0).getMessageCount());
        assertNull(first.getRecords().get(0).getLastMessageContent());
        assertNotNull(first.getRecords().get(0).getLastMessageTime());
        assertEquals("hong", first.getRecords().get(1).getUserNickname());
        var second = service.page(2, 2).getRecords();
        assertEquals(1, second.size());
        assertEquals("小明", second.get(0).getUserNickname());
        assertEquals("avatar.png", second.get(0).getUserAvatar());
        assertEquals(2L, second.get(0).getMessageCount());
        assertEquals("先休息一下", second.get(0).getLastMessageContent());
        assertEquals(1, second.get(0).getLastMessageTime().getMinute());
    }

    @Test
    void detailContainsOnlySelectedSessionsMessagesInOrder() {
        var detail = service.detail(10L);
        assertEquals("小明", detail.session().getUserNickname());
        assertEquals(2, detail.messages().size());
        assertEquals(100L, detail.messages().get(0).getId());
        assertEquals(101L, detail.messages().get(1).getId());
        assertTrue(detail.messages().stream().allMatch(message -> message.getSessionId() == 10L));
        assertEquals((long) detail.messages().size(), detail.session().getMessageCount());
    }

    @Test
    void emptyAndMissingSessionsAreHandled() {
        assertTrue(service.detail(30L).messages().isEmpty());
        assertThrows(BusinessException.class, () -> service.detail(999L));
    }

    @Test
    void boundsPageSize() {
        var result = service.page(-1, 10000);
        assertEquals(1, result.getCurrent());
        assertEquals(100, result.getSize());
    }
}
