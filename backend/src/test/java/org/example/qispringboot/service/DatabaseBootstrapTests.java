package org.example.qispringboot.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.example.qispringboot.entity.*;
import org.example.qispringboot.mapper.*;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

class DatabaseBootstrapTests {
    private SqlSession sql;

    @BeforeEach
    void setUp() throws Exception {
        var source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:bootstrap;MODE=MySQL;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=USER;DB_CLOSE_DELAY=-1");
        try (var connection = source.getConnection(); var statement = connection.createStatement()) {
            statement.execute("DROP ALL OBJECTS");
            runScript(connection, "schema.sql");
            runScript(connection, "seed-categories.sql");
        }
        var config = new MybatisConfiguration();
        config.setEnvironment(new Environment("test", new JdbcTransactionFactory(), source));
        for (Class<?> mapper : List.of(UserMapper.class, EmotionDiaryMapper.class, KnowledgeCategoryMapper.class,
                KnowledgeArticleMapper.class, SysFileInfoMapper.class, ChatSessionMapper.class, ChatMessageMapper.class)) {
            config.addMapper(mapper);
        }
        sql = new MybatisSqlSessionFactoryBuilder().build(config).openSession();
    }

    @AfterEach
    void close() { if (sql != null) sql.close(); }

    @Test
    void allSevenEntitiesHaveEveryMappedColumnInBootstrapSchema() throws Exception {
        for (Class<?> entity : List.of(User.class, EmotionDiary.class, KnowledgeCategory.class,
                KnowledgeArticle.class, SysFileInfo.class, ChatSession.class, ChatMessage.class)) {
            var info = TableInfoHelper.getTableInfo(entity);
            assertNotNull(info);
            var columns = new HashSet<String>();
            try (var metadata = sql.getConnection().getMetaData().getColumns(null, null, info.getTableName(), null)) {
                while (metadata.next()) columns.add(metadata.getString("COLUMN_NAME").toLowerCase(Locale.ROOT));
            }
            assertTrue(columns.contains(info.getKeyColumn()), entity.getName() + " primary key");
            for (var field : info.getFieldList()) {
                assertTrue(columns.contains(field.getColumn()), info.getTableName() + "." + field.getColumn());
            }
        }
    }

    @Test
    void bootstrapSupportsExistingEntityInsertsAndReads() {
        var users = sql.getMapper(UserMapper.class);
        var user = User.builder().username("bootstrap-user").password("test-only-encoded-hash").build();
        users.insert(user);
        var stored = users.selectById(user.getId());
        assertNotNull(user.getId());
        assertEquals(1, stored.getUserType());
        assertEquals(1, stored.getStatus());
        assertNotNull(stored.getCreatedAt());

        var categories = sql.getMapper(KnowledgeCategoryMapper.class);
        var category = categories.selectList(null).get(0);
        category.setDescription("修改后的分类描述");
        categories.updateById(category);
        assertEquals("修改后的分类描述", categories.selectById(category.getId()).getDescription());

        var articles = sql.getMapper(KnowledgeArticleMapper.class);
        var article = KnowledgeArticle.builder().categoryId(category.getId()).authorId(user.getId())
                .title("测试文章").content("用于验证数据库映射").build();
        articles.insert(article);
        assertEquals(32, article.getId().length());
        assertEquals(0, articles.selectById(article.getId()).getStatus());

        var diaries = sql.getMapper(EmotionDiaryMapper.class);
        var diary = EmotionDiary.builder().userId(user.getId()).diaryDate(LocalDate.of(2026, 11, 12))
                .moodScore(7).diaryContent("测试日志").build();
        diaries.insert(diary);
        assertEquals("测试日志", diaries.selectById(diary.getId()).getDiaryContent());

        var sessions = sql.getMapper(ChatSessionMapper.class);
        var session = ChatSession.builder().userId(user.getId()).sessionTitle("测试会话")
                .lastEmotionAnalysis("{\"available\":true}").build();
        sessions.insert(session);
        assertEquals(session.getLastEmotionAnalysis(), sessions.selectById(session.getId()).getLastEmotionAnalysis());

        var messages = sql.getMapper(ChatMessageMapper.class);
        var message = ChatMessage.builder().sessionId(session.getId()).senderType(1).content("测试提问").build();
        messages.insert(message);
        assertEquals(1, messages.selectById(message.getId()).getMessageType());

        var files = sql.getMapper(SysFileInfoMapper.class);
        var file = SysFileInfo.builder().originalName("example.png").filePath("/uploads/example.png")
                .fileSize(100L).uploadUserId(user.getId()).isTemp(false).businessId(article.getId()).build();
        files.insert(file);
        assertEquals(false, files.selectById(file.getId()).getIsTemp());
        assertNotNull(files.selectById(file.getId()).getCreateTime());
    }

    @Test
    void repeatedBootstrapDoesNotDuplicateCategoriesOrOverwriteExistingData() throws Exception {
        var categories = sql.getMapper(KnowledgeCategoryMapper.class);
        var category = categories.selectList(null).get(0);
        Long id = category.getId();
        category.setCategoryName("自行调整的名称");
        category.setStatus(0);
        categories.updateById(category);
        runScript(sql.getConnection(), "schema.sql");
        runScript(sql.getConnection(), "seed-categories.sql");
        sql.clearCache();
        assertEquals(3, categories.selectCount(null));
        assertEquals("自行调整的名称", categories.selectById(id).getCategoryName());
        assertEquals(0, categories.selectById(id).getStatus());
        assertEquals(0, sql.getMapper(UserMapper.class).selectCount(null));
    }

    private void runScript(Connection connection, String name) throws Exception {
        Path file = Path.of(System.getProperty("basedir", "."), "sql", name);
        String mysql = Files.readString(file, StandardCharsets.UTF_8);
        // H2只验证关系结构和字段映射；不把兼容模式测试当成真实MySQL验收。
        String portable = mysql.replaceAll("(?im)^CREATE DATABASE[^;]*;\\s*", "")
                .replaceAll("(?im)^USE [^;]*;\\s*", "")
                .replaceAll("(?im)^SET NAMES [^;]*;\\s*", "")
                .replaceAll("(?m)^\\) ENGINE=[^;]*;", ");");
        ScriptUtils.executeSqlScript(connection, new EncodedResource(
                new ByteArrayResource(portable.getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8));
    }
}
