package org.example.qispringboot.service;

import org.example.qispringboot.rag.*;
import org.example.qispringboot.exception.BusinessException;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import javax.sql.DataSource;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class RagIntegrationTests {
    @Configuration @EnableTransactionManagement(proxyTargetClass=true)
    static class Config {
        @Bean DataSource source() {var source=new JdbcDataSource();source.setURL("jdbc:h2:mem:ragtest;MODE=MySQL;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=USER;DB_CLOSE_DELAY=-1");return source;}
        @Bean JdbcTemplate jdbc(DataSource source) {return new JdbcTemplate(source);}
        @Bean DataSourceTransactionManager transactionManager(DataSource source) {return new DataSourceTransactionManager(source);}
        @Bean RagService rag(JdbcTemplate jdbc) {return new RagService(jdbc);}
    }
    AnnotationConfigApplicationContext context;
    JdbcTemplate jdbc;
    RagService rag;
    @BeforeEach void setup() throws Exception {
        context=new AnnotationConfigApplicationContext(Config.class);jdbc=context.getBean(JdbcTemplate.class);rag=context.getBean(RagService.class);
        jdbc.execute("DROP ALL OBJECTS");
        var schema=Files.readString(Path.of("sql/schema.sql")).replaceAll("(?im)^CREATE DATABASE[^;]*;\\s*","").replaceAll("(?im)^USE [^;]*;\\s*","")
            .replaceAll("(?im)^SET NAMES [^;]*;\\s*","").replaceAll("(?m)^\\) ENGINE=[^;]*;",");");
        try(var connection=context.getBean(DataSource.class).getConnection()) {
            ScriptUtils.executeSqlScript(connection,new ByteArrayResource(schema.getBytes(StandardCharsets.UTF_8)));
        }
    }
    @AfterEach void close() {context.close();}
    String task(String document) {return jdbc.queryForObject("SELECT id FROM ingestion_task WHERE document_id=?",String.class,document);}
    @Test void databaseHasNineNonRbacTablesAndRealDocumentRelationships() {
        assertEquals(10,jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='public'",Integer.class));
        assertThrows(Exception.class,()->jdbc.update("INSERT INTO rag_chunk(id,document_id,chunk_index,content) VALUES ('x','missing',0,'x')"));
    }
    @Test void pendingDocumentsAreInvisibleAndRepeatedIngestionIsIdempotent() {
        String id=rag.create("睡眠管理","https://example.org/sleep","睡眠管理：规律作息，保持睡眠环境安静。".repeat(80),7L);
        assertTrue(rag.search("睡眠管理").isEmpty());
        rag.ingest(task(id));
        int count=jdbc.queryForObject("SELECT COUNT(*) FROM rag_chunk",Integer.class);
        assertTrue(count>1);
        assertFalse(rag.search("睡眠管理").isEmpty());
        assertTrue(rag.context("睡眠管理").contains("[资料1]"));
        assertTrue(rag.citations(rag.search("睡眠管理")).contains(id));
        rag.ingest(task(id));
        assertEquals(count,jdbc.queryForObject("SELECT COUNT(*) FROM rag_chunk",Integer.class));
        assertEquals("DONE",jdbc.queryForObject("SELECT status FROM ingestion_task",String.class));
        assertTrue(rag.search("quantumxyz").isEmpty());
    }
    @Test void disabledAndFailedDocumentsCannotBeRetrievedAndRetryUsesPersistentTask() {
        String id=rag.create("压力调节",null,"压力调节知识",7L),task=task(id);
        rag.failed(task);
        assertTrue(rag.search("压力").isEmpty());
        rag.retry(id);
        assertEquals("PENDING",jdbc.queryForObject("SELECT status FROM ingestion_task",String.class));
        rag.ingest(task);rag.disable(id);rag.ingest(task);
        assertTrue(rag.search("压力").isEmpty());
        assertThrows(BusinessException.class,()->rag.retry(id));
    }
    @Test void invalidContentAndUrlsAreRejectedAndTaskFailureRollsBackDocumentCreation() {
        assertThrows(BusinessException.class,()->rag.create("",null,"正文",7L));
        assertThrows(BusinessException.class,()->rag.create("资料","javascript:alert(1)","正文",7L));
        jdbc.execute("ALTER TABLE ingestion_task ADD CONSTRAINT force_task_failure CHECK (attempts<0)");
        assertThrows(Exception.class,()->rag.create("资料",null,"正文",7L));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM rag_document",Integer.class));
    }
    @Test void splitKeepsAllContentAndHandlesSurrogatePairs() {
        String text="睡眠😀".repeat(400);
        var chunks=RagService.split(text);
        assertTrue(chunks.size()>1);
        assertTrue(chunks.stream().allMatch(c->!Character.isLowSurrogate(c.charAt(0)) && !Character.isHighSurrogate(c.charAt(c.length()-1))));
        assertTrue(chunks.get(chunks.size()-1).endsWith("😀"));
    }
    @Test void queuePublishesWithConfirmationAndConsumerProcessesThePersistedTask() {
        String id=rag.create("睡眠",null,"睡眠改善知识",7L);
        var rabbit=mock(RabbitTemplate.class);
        doAnswer(call->{CorrelationData data=call.getArgument(3);data.getFuture().complete(new CorrelationData.Confirm(true,null));return null;})
                .when(rabbit).convertAndSend(eq("mental.rag"),eq("ingest"),anyString(),any(CorrelationData.class));
        var queue=new IngestionQueue(jdbc,rabbit,rag);
        queue.publishPending();
        assertEquals("PUBLISHED",jdbc.queryForObject("SELECT status FROM ingestion_task",String.class));
        queue.consume(task(id));
        assertEquals("READY",rag.page(1,10).records().get(0).status());
        verify(rabbit).convertAndSend(eq("mental.rag"),eq("ingest"),eq(task(id)),any(CorrelationData.class));
    }
    @Test void failedPublishRetriesAreBoundedAndVisibleForManualRetry() {
        String id=rag.create("资料",null,"正文",7L);
        var rabbit=mock(RabbitTemplate.class);
        doThrow(new IllegalStateException("broker down")).when(rabbit).convertAndSend(anyString(),anyString(),anyString(),any(CorrelationData.class));
        var queue=new IngestionQueue(jdbc,rabbit,rag);
        queue.publishPending();queue.publishPending();queue.publishPending();queue.publishPending();
        assertEquals("FAILED",rag.page(1,10).records().get(0).status());
        assertEquals(3,rag.page(1,10).records().get(0).attempts());
        rag.retry(id);
        assertEquals(0,rag.page(1,10).records().get(0).attempts());
    }
}
