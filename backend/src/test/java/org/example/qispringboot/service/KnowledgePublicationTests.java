package org.example.qispringboot.service;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.example.qispringboot.DTO.command.KnowledgeArticleCommandDTO;
import org.example.qispringboot.entity.KnowledgeArticle;
import org.example.qispringboot.entity.KnowledgeCategory;
import org.example.qispringboot.entity.User;
import org.example.qispringboot.exception.BusinessException;
import org.example.qispringboot.mapper.*;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class KnowledgePublicationTests {
    private SqlSession sql;
    private KnowledgeArticleMapper articles;
    private KnowledgeService service;

    @BeforeEach
    void setUp() throws Exception {
        var source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:knowledge;MODE=MySQL;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=USER;DB_CLOSE_DELAY=-1");
        try (var connection = source.getConnection(); var statement = connection.createStatement()) {
            statement.execute("DROP ALL OBJECTS");
            String schema = Files.readString(Path.of(System.getProperty("basedir", "."), "sql/schema.sql"), StandardCharsets.UTF_8)
                    .replaceAll("(?im)^CREATE DATABASE[^;]*;\\s*", "")
                    .replaceAll("(?im)^USE [^;]*;\\s*", "")
                    .replaceAll("(?im)^SET NAMES [^;]*;\\s*", "")
                    .replaceAll("(?m)^\\) ENGINE=[^;]*;", ");");
            ScriptUtils.executeSqlScript(connection, new EncodedResource(new ByteArrayResource(
                    schema.getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8));
        }
        var config = new MybatisConfiguration();
        config.setEnvironment(new Environment("test", new JdbcTransactionFactory(), source));
        var pagination = new MybatisPlusInterceptor();
        pagination.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        config.addInterceptor(pagination);
        config.addMapper(KnowledgeArticleMapper.class);
        config.addMapper(KnowledgeCategoryMapper.class);
        config.addMapper(UserMapper.class);
        // 默认Mapper方法可能使用独立会话，测试夹具需对这些连接可见。
        sql = new MybatisSqlSessionFactoryBuilder().build(config).openSession(true);
        articles = sql.getMapper(KnowledgeArticleMapper.class);
        var categories = sql.getMapper(KnowledgeCategoryMapper.class);
        var users = sql.getMapper(UserMapper.class);
        users.insert(User.builder().id(10L).username("author").nickname("作者").password("test-hash").build());
        categories.insert(KnowledgeCategory.builder().id(1L).categoryName("情绪管理").build());
        for (int status = 0; status <= 2; status++) {
            articles.insert(KnowledgeArticle.builder().id("article-" + status).title("文章" + status)
                    .content("内容" + status).summary("摘要").coverImage("/uploads/test.png").tags("情绪,生活")
                    .categoryId(1L).authorId(10L).readCount(5).status(status)
                    .publishedAt(status == 1 ? LocalDateTime.of(2026, 11, 11, 10, 0) : null).build());
        }
        service = new KnowledgeService();
        ReflectionTestUtils.setField(service, "articleMapper", articles);
        ReflectionTestUtils.setField(service, "categoryMapper", categories);
        ReflectionTestUtils.setField(service, "userMapper", users);
    }

    @AfterEach
    void close() { if (sql != null) sql.close(); }

    @Test
    void readingListIncludesOnlyPublishedWhileAdminCanFilterEveryState() {
        var published = service.pagePublishedArticles(1L, 10L, null, null, null, null);
        assertEquals(1, published.getTotal());
        assertEquals("article-1", published.getRecords().get(0).getId());
        assertEquals("作者", published.getRecords().get(0).getAuthorName());
        assertEquals(3, service.pageArticles(1L, 10L, null, null, null, null, null).getTotal());
        assertEquals("article-0", service.pageArticles(1L, 10L, null, null, 0, null, null).getRecords().get(0).getId());
    }

    @Test
    void readingDraftOfflineOrMissingArticleIsRejectedWithoutIncreasingReads() {
        for (String id : java.util.List.of("article-0", "article-2", "missing"))
            assertThrows(BusinessException.class, () -> service.getPublishedArticleDetail(id));
        assertEquals(5, articles.selectById("article-0").getReadCount());
        assertEquals(5, articles.selectById("article-2").getReadCount());
    }

    @Test
    void publishedReadsIncreaseCountButAdminPreviewDoesNotAndContentTimestampStaysUnchanged() {
        var before = articles.selectById("article-1").getUpdatedAt();
        assertEquals(6, service.getPublishedArticleDetail("article-1").getReadCount());
        service.getPublishedArticleDetail("article-1");
        assertEquals(7, articles.selectById("article-1").getReadCount());
        assertEquals(before, articles.selectById("article-1").getUpdatedAt());
        assertEquals("内容0", service.getAdminArticleDetail("article-0").getContent());
        service.getAdminArticleDetail("article-1");
        assertEquals(7, articles.selectById("article-1").getReadCount());
    }

    @Test
    void publishingAndTakingOfflineChangeVisibilityAndPreserveFirstPublicationTime() {
        service.updateStatus("article-0", "1");
        var firstPublished = articles.selectById("article-0").getPublishedAt();
        assertNotNull(firstPublished);
        assertEquals(2, service.pagePublishedArticles(1L, 10L, null, null, null, null).getTotal());
        service.updateStatus("article-0", "2");
        assertThrows(BusinessException.class, () -> service.getPublishedArticleDetail("article-0"));
        service.updateStatus("article-0", "1");
        assertEquals(firstPublished, articles.selectById("article-0").getPublishedAt());
        assertEquals(5, articles.selectById("article-0").getReadCount());
    }

    @Test
    void publishedArticleMustBeTakenOfflineBeforeDeletion() {
        assertThrows(BusinessException.class, () -> service.deleteArticle("article-1"));
        assertNotNull(articles.selectById("article-1"));
        service.updateStatus("article-1", "2");
        service.deleteArticle("article-1");
        assertNull(articles.selectById("article-1"));
        service.deleteArticle("article-0");
        assertNull(articles.selectById("article-0"));
    }

    @Test
    void editingContentClearsOptionalFieldsWithoutOverwritingStateReadCountOrAuthor() {
        var dto = new KnowledgeArticleCommandDTO();
        dto.setId("article-1"); // 路径ID决定更新目标，忽略正文中的不同ID。
        dto.setTitle("新的标题");
        dto.setCategoryId(1L);
        dto.setContent("修改后的正文");
        service.updateArticle("article-2", dto);
        var result = articles.selectById("article-2");
        assertEquals(2, result.getStatus());
        assertEquals(5, result.getReadCount());
        assertEquals(10L, result.getAuthorId());
        assertNull(result.getSummary());
        assertNull(result.getCoverImage());
        assertNull(result.getTags());
        assertEquals("新的标题", result.getTitle());
        assertEquals("文章1", articles.selectById("article-1").getTitle());
    }

    @Test
    void rejectsInvalidStateAndBoundsPageSize() {
        for (String invalid : java.util.Arrays.asList("999", "-1", "bad", null))
            assertThrows(BusinessException.class, () -> service.updateStatus("article-0", invalid));
        assertEquals(0, articles.selectById("article-0").getStatus());
        var page = service.pagePublishedArticles(-1L, 10000L, null, null, "id; DELETE FROM user", "desc");
        assertEquals(1, page.getCurrent());
        assertEquals(100, page.getSize());
        assertEquals(1, page.getTotal());
    }
}
