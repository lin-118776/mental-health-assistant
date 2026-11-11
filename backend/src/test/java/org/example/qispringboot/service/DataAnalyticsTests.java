package org.example.qispringboot.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.example.qispringboot.mapper.DataAnalyticsMapper;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class DataAnalyticsTests {
    private SqlSession sql;
    private DataAnalyticsService service;
    private final LocalDate today = LocalDate.of(2026, 11, 11);

    @BeforeEach
    void setUp() throws Exception {
        var source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:analytics;MODE=MySQL;DB_CLOSE_DELAY=-1");
        try (var connection = source.getConnection(); var statement = connection.createStatement()) {
            statement.execute("DROP ALL OBJECTS");
            statement.execute("CREATE TABLE `user` (id BIGINT PRIMARY KEY, status INT, created_at TIMESTAMP)");
            statement.execute("CREATE TABLE chat_session (id BIGINT PRIMARY KEY, user_id BIGINT, created_at TIMESTAMP)");
            statement.execute("CREATE TABLE chat_message (id BIGINT PRIMARY KEY, session_id BIGINT, sender_type INT, created_at TIMESTAMP)");
            statement.execute("CREATE TABLE emotion_diary (id BIGINT PRIMARY KEY, user_id BIGINT, diary_date DATE, mood_score INT, created_at TIMESTAMP)");
            statement.execute("INSERT INTO `user` VALUES (1,1,'2026-11-01 09:00:00'), (2,1,'2026-11-01 09:00:00'), (3,0,'2026-11-01 09:00:00'), (4,1,'2026-11-11 09:00:00'), (5,1,'2026-11-12 00:00:00')");
            statement.execute("INSERT INTO chat_session VALUES (10,1,'2026-11-01 09:00:00'), (20,3,'2026-11-04 09:00:00'), (30,2,'2026-11-11 10:00:00'), (40,2,'2026-11-10 09:00:00'), (50,4,'2026-11-12 00:00:00'), (60,4,'2026-11-11 09:00:00')");
            statement.execute("INSERT INTO chat_message VALUES (101,10,1,'2026-11-11 08:00:00'), (102,10,2,'2026-11-11 08:02:00'), (201,20,1,'2026-11-05 00:00:00'), (401,40,2,'2026-11-11 09:00:00'), (402,40,2,'2026-11-11 10:00:00'), (501,50,1,'2026-11-12 00:00:00'), (601,60,1,'2026-11-11 09:00:00'), (602,60,2,'2026-11-11 09:06:00')");
            statement.execute("INSERT INTO emotion_diary VALUES (1,1,'2026-11-01',4,'2026-11-11 10:00:00'), (2,1,'2026-11-11',8,'2026-11-11 11:00:00'), (3,3,'2026-11-11',6,'2026-11-10 10:00:00'), (4,4,'2026-11-12',10,'2026-11-12 00:00:00')");
        }
        var config = new MybatisConfiguration();
        config.setEnvironment(new Environment("test", new JdbcTransactionFactory(), source));
        config.addMapper(DataAnalyticsMapper.class);
        sql = new MybatisSqlSessionFactoryBuilder().build(config).openSession();
        service = new DataAnalyticsService(sql.getMapper(DataAnalyticsMapper.class));
    }

    @AfterEach
    void close() { if (sql != null) sql.close(); }

    @Test
    void activeUsersComeFromRealEventsAndAreDeduplicatedAcrossFeatures() {
        var result = service.overview(today);
        assertEquals(5, result.getSystemOverview().getTotalUsers());
        assertEquals(3, result.getSystemOverview().getActiveUsers());
        var day = result.getUserActivity().get(6);
        assertEquals(2, day.getActiveUsers());
        assertEquals(1, day.getDiaryUsers());
        assertEquals(2, day.getConsultationUsers());
        assertEquals(1, day.getNewUsers());
        // 当前已禁用但在历史区间内有行为的账号仍应计入历史活跃，启用但未使用的账号不计。
        assertEquals(1, result.getUserActivity().get(0).getActiveUsers());
    }

    @Test
    void todayCountsUseCreationDateAndExcludeTomorrow() {
        var result = service.overview(today).getSystemOverview();
        assertEquals(4, result.getTotalDiaries());
        assertEquals(2, result.getTodayDiaries());
        assertEquals(6, result.getTotalSesss());
        assertEquals(2, result.getTodaySesss());
        assertEquals(7.0, result.getAvgMoodScore());
    }

    @Test
    void continuingAnOldSessionCountsAsTodayConsultationButAiOnlyAndEmptySessionsDoNot() {
        var result = service.overview(today).getConsultationStats().getDailyTrend();
        assertEquals(7, result.size());
        assertEquals("2026-11-05", result.get(0).getDate());
        assertEquals(1, result.get(0).getSessionCount());
        assertEquals(2, result.get(6).getSessionCount());
        assertEquals(2, result.get(6).getUserCount());
    }

    @Test
    void averageMessageSpanUsesActualMessagesAndIncludesSingleMessageZeroSpan() {
        assertEquals(2.7, service.overview(today).getConsultationStats().getAvgDurationMinutes());
    }

    @Test
    void emotionTrendUsesDiaryDatesAndKeepsUnrecordedDaysNull() {
        var result = service.overview(today).getEmotionTrend();
        assertEquals(7.0, result.get(6).getAvgMoodScore());
        assertEquals(2, result.get(6).getSessionCount());
        assertNull(result.get(1).getAvgMoodScore());
        assertEquals(0, result.get(1).getSessionCount());
    }

    @Test
    void eventsJustBeforeWindowAreExcluded() throws Exception {
        execute("INSERT INTO emotion_diary VALUES (5,2,'2026-11-04',5,'2026-11-04 23:59:59')");
        execute("INSERT INTO chat_session VALUES (70,2,'2026-11-04 23:59:59')");
        execute("INSERT INTO chat_message VALUES (701,70,1,'2026-11-04 23:59:59')");
        var result = service.overview(today);
        assertEquals(3, result.getSystemOverview().getActiveUsers());
        assertEquals(1, result.getConsultationStats().getDailyTrend().get(0).getSessionCount());
    }

    @Test
    void emptyDatabaseReturnsZeroCountsAndUnknownAverages() throws Exception {
        execute("DELETE FROM chat_message");
        execute("DELETE FROM chat_session");
        execute("DELETE FROM emotion_diary");
        execute("DELETE FROM `user`");
        var result = service.overview(today);
        assertEquals(0, result.getSystemOverview().getTotalUsers());
        assertEquals(0, result.getSystemOverview().getActiveUsers());
        assertNull(result.getSystemOverview().getAvgMoodScore());
        assertNull(result.getConsultationStats().getAvgDurationMinutes());
        assertEquals(7, result.getUserActivity().size());
        assertTrue(result.getUserActivity().stream().allMatch(day -> day.getActiveUsers() == 0));
        assertTrue(result.getEmotionTrend().stream().allMatch(day -> day.getAvgMoodScore() == null));
    }

    private void execute(String query) throws Exception {
        try (var statement = sql.getConnection().createStatement()) { statement.execute(query); }
        sql.clearCache();
    }
}
