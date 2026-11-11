package org.example.qispringboot.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.example.qispringboot.DTO.response.DataAnalyticsOverviewDTO.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface DataAnalyticsMapper {
    // 使用事件发生时间统计行为，不读取日志正文或消息内容。
    String ACTIVITY_SQL = """
            SELECT user_id, created_at FROM emotion_diary
            WHERE created_at >= #{since} AND created_at < #{until}
            UNION ALL
            SELECT s.user_id, m.created_at FROM chat_message m JOIN chat_session s ON s.id = m.session_id
            WHERE m.sender_type = 1 AND m.created_at >= #{since} AND m.created_at < #{until}
            """;

    @Select("""
            SELECT (SELECT COUNT(*) FROM `user`) AS total_users,
              (SELECT COUNT(DISTINCT a.user_id) FROM (
            """ + ACTIVITY_SQL + """
              ) a JOIN `user` u ON u.id = a.user_id) AS active_users,
              (SELECT COUNT(*) FROM emotion_diary) AS total_diaries,
              (SELECT COUNT(*) FROM emotion_diary WHERE created_at >= #{today} AND created_at < #{until}) AS today_diaries,
              (SELECT COUNT(*) FROM chat_session) AS total_sesss,
              (SELECT COUNT(*) FROM chat_session WHERE created_at >= #{today} AND created_at < #{until}) AS today_sesss,
              (SELECT AVG(mood_score) FROM emotion_diary) AS avg_mood_score
            """)
    SystemOverviewDTO systemOverview(@Param("today") LocalDateTime today,
            @Param("since") LocalDateTime since, @Param("until") LocalDateTime until);

    @Select("""
            SELECT CAST(m.created_at AS DATE) AS date, COUNT(DISTINCT m.session_id) AS session_count,
              COUNT(DISTINCT s.user_id) AS user_count
            FROM chat_message m JOIN chat_session s ON s.id = m.session_id
            WHERE m.sender_type = 1 AND m.created_at >= #{since} AND m.created_at < #{until}
            GROUP BY CAST(m.created_at AS DATE)
            """)
    List<TrendItemDTO> consultationDaily(@Param("since") LocalDateTime since, @Param("until") LocalDateTime until);

    @Select("""
            SELECT CAST(created_at AS DATE) AS date, COUNT(*) AS new_users FROM `user`
            WHERE created_at >= #{since} AND created_at < #{until} GROUP BY CAST(created_at AS DATE)
            """)
    List<UserActivityItemDTO> registrationsDaily(@Param("since") LocalDateTime since, @Param("until") LocalDateTime until);

    @Select("""
            SELECT CAST(created_at AS DATE) AS date, COUNT(DISTINCT user_id) AS diary_users FROM emotion_diary
            WHERE created_at >= #{since} AND created_at < #{until} GROUP BY CAST(created_at AS DATE)
            """)
    List<UserActivityItemDTO> diaryUsersDaily(@Param("since") LocalDateTime since, @Param("until") LocalDateTime until);

    @Select("SELECT CAST(a.created_at AS DATE) AS date, COUNT(DISTINCT a.user_id) AS active_users FROM ("
            + ACTIVITY_SQL + ") a JOIN `user` u ON u.id = a.user_id GROUP BY CAST(a.created_at AS DATE)")
    List<UserActivityItemDTO> activeUsersDaily(@Param("since") LocalDateTime since, @Param("until") LocalDateTime until);

    @Select("""
            SELECT diary_date AS date, AVG(mood_score) AS avg_mood_score, COUNT(*) AS session_count
            FROM emotion_diary WHERE diary_date >= #{since} AND diary_date <= #{today} GROUP BY diary_date
            """)
    List<EmotionTrendItemDTO> emotionDaily(@Param("since") LocalDate since, @Param("today") LocalDate today);

    @Select("""
            SELECT AVG(spans.minutes) FROM (
              SELECT TIMESTAMPDIFF(SECOND, MIN(m.created_at), MAX(m.created_at)) / 60.0 AS minutes
              FROM chat_message m JOIN chat_session s ON s.id = m.session_id
              WHERE m.created_at < #{until}
              GROUP BY m.session_id HAVING SUM(CASE WHEN m.sender_type = 1 THEN 1 ELSE 0 END) > 0
            ) spans
            """)
    Double averageMessageSpan(@Param("until") LocalDateTime until);
}
