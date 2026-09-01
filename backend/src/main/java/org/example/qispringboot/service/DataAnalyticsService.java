package org.example.qispringboot.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import org.example.qispringboot.DTO.response.DataAnalyticsOverviewDTO;
import org.example.qispringboot.entity.ChatSession;
import org.example.qispringboot.entity.EmotionDiary;
import org.example.qispringboot.entity.User;
import org.example.qispringboot.mapper.ChatSessionMapper;
import org.example.qispringboot.mapper.EmotionDiaryMapper;
import org.example.qispringboot.mapper.UserMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class DataAnalyticsService {

    private static final int TREND_DAYS = 7;

    @Resource
    private UserMapper userMapper;

    @Resource
    private EmotionDiaryMapper diaryMapper;

    @Resource
    private ChatSessionMapper sessionMapper;

    public DataAnalyticsOverviewDTO overview() {
        LocalDate today = LocalDate.now();
        LocalDate sinceDate = today.minusDays(TREND_DAYS - 1);
        LocalDateTime sinceStart = sinceDate.atStartOfDay();

        Long totalUsers = userMapper.selectCount(null);
        Long activeUsers = userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getStatus, 1));
        Long totalDiaries = diaryMapper.selectCount(null);
        Long todayDiaries = diaryMapper.selectCount(new LambdaQueryWrapper<EmotionDiary>()
                .ge(EmotionDiary::getDiaryDate, today));
        Long totalSesss = sessionMapper.selectCount(null);
        Long todaySesss = sessionMapper.selectCount(new LambdaQueryWrapper<ChatSession>()
                .ge(ChatSession::getCreatedAt, today.atStartOfDay()));

        List<EmotionDiary> allDiaries = diaryMapper.selectList(null);
        double avgMood = allDiaries.stream()
                .filter(d -> d.getMoodScore() != null)
                .mapToInt(EmotionDiary::getMoodScore)
                .average()
                .orElse(0.0);

        DataAnalyticsOverviewDTO.SystemOverviewDTO systemOverview = DataAnalyticsOverviewDTO.SystemOverviewDTO.builder()
                .totalUsers(totalUsers)
                .activeUsers(activeUsers)
                .totalDiaries(totalDiaries)
                .todayDiaries(todayDiaries)
                .totalSesss(totalSesss)
                .todaySesss(todaySesss)
                .avgMoodScore(round1(avgMood))
                .build();

        List<ChatSession> recentSessions = sessionMapper.selectList(new LambdaQueryWrapper<ChatSession>()
                .ge(ChatSession::getCreatedAt, sinceStart));
        Map<LocalDate, List<ChatSession>> sessionsByDay = recentSessions.stream()
                .filter(s -> s.getCreatedAt() != null)
                .collect(Collectors.groupingBy(s -> s.getCreatedAt().toLocalDate()));

        List<EmotionDiary> recentDiaries = diaryMapper.selectList(new LambdaQueryWrapper<EmotionDiary>()
                .ge(EmotionDiary::getDiaryDate, sinceDate));
        Map<LocalDate, List<EmotionDiary>> diariesByDay = recentDiaries.stream()
                .filter(d -> d.getDiaryDate() != null)
                .collect(Collectors.groupingBy(EmotionDiary::getDiaryDate));

        List<DataAnalyticsOverviewDTO.TrendItemDTO> dailyTrend = new ArrayList<>();
        List<DataAnalyticsOverviewDTO.UserActivityItemDTO> userActivity = new ArrayList<>();
        for (LocalDate day = sinceDate; !day.isAfter(today); day = day.plusDays(1)) {
            List<ChatSession> daySessions = sessionsByDay.getOrDefault(day, List.of());
            long sessionCount = daySessions.size();
            long userCount = daySessions.stream()
                    .map(ChatSession::getUserId)
                    .filter(java.util.Objects::nonNull)
                    .distinct()
                    .count();
            dailyTrend.add(DataAnalyticsOverviewDTO.TrendItemDTO.builder()
                    .date(day.toString())
                    .sessionCount(sessionCount)
                    .userCount(userCount)
                    .build());

            long newUsers = userMapper.selectCount(new LambdaQueryWrapper<User>()
                    .ge(User::getCreatedAt, day.atStartOfDay())
                    .lt(User::getCreatedAt, day.plusDays(1).atStartOfDay()));
            List<EmotionDiary> dayDiaries = diariesByDay.getOrDefault(day, List.of());
            long diaryUsers = dayDiaries.stream()
                    .map(EmotionDiary::getUserId)
                    .filter(java.util.Objects::nonNull)
                    .distinct()
                    .count();
            long consultationUsers = daySessions.stream()
                    .map(ChatSession::getUserId)
                    .filter(java.util.Objects::nonNull)
                    .distinct()
                    .count();
            userActivity.add(DataAnalyticsOverviewDTO.UserActivityItemDTO.builder()
                    .date(day.toString())
                    .newUsers(newUsers)
                    .diaryUsers(diaryUsers)
                    .consultationUsers(consultationUsers)
                    .build());
        }

        List<DataAnalyticsOverviewDTO.EmotionTrendItemDTO> emotionTrend = new ArrayList<>();
        for (LocalDate day = sinceDate; !day.isAfter(today); day = day.plusDays(1)) {
            List<EmotionDiary> dayDiaries = diariesByDay.getOrDefault(day, List.of());
            double dayAvg = dayDiaries.stream()
                    .filter(d -> d.getMoodScore() != null)
                    .mapToInt(EmotionDiary::getMoodScore)
                    .average()
                    .orElse(0.0);
            emotionTrend.add(DataAnalyticsOverviewDTO.EmotionTrendItemDTO.builder()
                    .date(day.toString())
                    .avgMoodScore(round1(dayAvg))
                    .sessionCount((long) dayDiaries.size())
                    .build());
        }

        DataAnalyticsOverviewDTO.ConsultationStatsDTO consultationStats = DataAnalyticsOverviewDTO.ConsultationStatsDTO.builder()
                .totalSessions(totalSesss)
                .avgDurationMinutes(0.0)
                .dailyTrend(dailyTrend)
                .build();

        return DataAnalyticsOverviewDTO.builder()
                .systemOverview(systemOverview)
                .consultationStats(consultationStats)
                .userActivity(userActivity)
                .emotionTrend(emotionTrend)
                .build();
    }

    private double round1(double value) {
        return Math.round(value * 10) / 10.0;
    }
}