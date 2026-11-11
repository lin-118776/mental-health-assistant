package org.example.qispringboot.DTO.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DataAnalyticsOverviewDTO {

    private SystemOverviewDTO systemOverview;

    private ConsultationStatsDTO consultationStats;

    private List<UserActivityItemDTO> userActivity;

    private List<EmotionTrendItemDTO> emotionTrend;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SystemOverviewDTO {
        private Long totalUsers;
        // 近7天新建日志或发送用户消息的现存账号去重数。
        private Long activeUsers;
        private Long totalDiaries;
        private Long todayDiaries;
        private Long totalSesss;
        private Long todaySesss;
        private Double avgMoodScore;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConsultationStatsDTO {
        private Long totalSessions;
        // 有用户发言的会话首末消息时间差的均值；不是在线使用时长。
        private Double avgDurationMinutes;
        private List<TrendItemDTO> dailyTrend;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TrendItemDTO {
        private String date;
        private Long sessionCount;
        private Long userCount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserActivityItemDTO {
        private String date;
        private Long activeUsers;
        private Long newUsers;
        private Long diaryUsers;
        private Long consultationUsers;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EmotionTrendItemDTO {
        private String date;
        private Double avgMoodScore;
        private Long sessionCount; // 保留原接口字段名，此处表示日志条数。
    }
}
