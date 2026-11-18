package org.example.qispringboot;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.qispringboot.config.JwtUtil;
import org.example.qispringboot.rag.RagService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import javax.sql.DataSource;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={
    "spring.datasource.url=jdbc:h2:mem:requirementsapp;MODE=MySQL;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=USER;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver","spring.datasource.username=sa","spring.datasource.password=",
    "spring.ai.openai.api-key=test-only-not-a-real-key","jwt.secret=test-only-secret-with-at-least-32-bytes-for-requirements",
    "app.ingestion.enabled=false","app.cache.enabled=false","spring.rabbitmq.listener.simple.auto-startup=false"
})
@AutoConfigureMockMvc
class RequirementsApplicationTests {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource source;
    @Autowired ObjectMapper mapper;
    @Autowired RagService rag;
    @Autowired JwtUtil jwt;
    String adminToken,userToken;
    @BeforeEach void setup() throws Exception {
        jdbc.execute("DROP ALL OBJECTS");
        String schema=Files.readString(Path.of("sql/schema.sql")).replaceAll("(?im)^CREATE DATABASE[^;]*;\\s*","")
            .replaceAll("(?im)^USE [^;]*;\\s*","").replaceAll("(?im)^SET NAMES [^;]*;\\s*","").replaceAll("(?m)^\\) ENGINE=[^;]*;",");");
        try(var connection=source.getConnection()) {ScriptUtils.executeSqlScript(connection,new ByteArrayResource(schema.getBytes(StandardCharsets.UTF_8)));}
        jdbc.update("INSERT INTO `user`(id,username,password,user_type) VALUES (7,'admin','test-hash',2),(8,'reader','test-hash',1)");
        adminToken="Bearer "+jwt.generateToken(7L,"admin",2,"test-hash");
        userToken="Bearer "+jwt.generateToken(8L,"reader",1,"test-hash");
    }
    @Test void swaggerGeneratesInTheRealApplicationAndBusinessRoutesStillRequireAuthentication() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"));
        mvc.perform(get("/swagger-ui.html")).andExpect(status().is3xxRedirection());
        mvc.perform(get("/api/rag/search").param("query","睡眠")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/rag/admin/documents").header("Authorization",userToken)).andExpect(jsonPath("$.code").value("A0301"));
    }
    @Test void importIngestSearchDisableAndMcpWorkThroughActualJwtFilters() throws Exception {
        String response=mvc.perform(post("/api/rag/admin/documents").header("Authorization",adminToken).contentType(MediaType.APPLICATION_JSON)
            .content("{\"title\":\"睡眠资料\",\"content\":\"规律作息有助于改善睡眠。\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("200")).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String id=mapper.readTree(response).get("data").asText();
        assertEquals(7L,jdbc.queryForObject("SELECT created_by FROM rag_document WHERE id=?",Long.class,id));
        rag.ingest(jdbc.queryForObject("SELECT id FROM ingestion_task WHERE document_id=?",String.class,id));
        mvc.perform(get("/api/rag/search").param("query","改善睡眠").header("Authorization",userToken))
            .andExpect(jsonPath("$.data[0].documentId").value(id));
        mvc.perform(post("/api/mcp").header("Authorization",userToken).header("Accept","application/json, text/event-stream").contentType(MediaType.APPLICATION_JSON)
            .content("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\",\"params\":{\"name\":\"get_my_emotion_trend\",\"arguments\":{\"days\":7}}}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.result.isError").value(false));
        mvc.perform(delete("/api/rag/admin/documents/"+id).header("Authorization",adminToken)).andExpect(jsonPath("$.code").value("200"));
        mvc.perform(get("/api/rag/search").param("query","睡眠").header("Authorization",userToken)).andExpect(jsonPath("$.data").isEmpty());
    }
}
