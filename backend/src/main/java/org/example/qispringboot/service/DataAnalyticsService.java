package org.example.qispringboot.service;

import org.example.qispringboot.DTO.response.DataAnalyticsOverviewDTO;
import org.example.qispringboot.DTO.response.DataAnalyticsOverviewDTO.*;
import org.example.qispringboot.mapper.DataAnalyticsMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class DataAnalyticsService {
    private static final int TREND_DAYS = 7;
    private final DataAnalyticsMapper mapper;

    public DataAnalyticsService(DataAnalyticsMapper mapper) { this.mapper = mapper; }

    public DataAnalyticsOverviewDTO overview() {
        return overview(LocalDate.now(ZoneId.of("Asia/Shanghai")));
    }

    DataAnalyticsOverviewDTO overview(LocalDate today) {
        LocalDate since = today.minusDays(TREND_DAYS - 1);
        var sinceTime = since.atStartOfDay();
        var until = today.plusDays(1).atStartOfDay();
        var system = mapper.systemOverview(today.atStartOfDay(), sinceTime, until);
        system.setAvgMoodScore(round1(system.getAvgMoodScore()));
        var consultation = index(mapper.consultationDaily(sinceTime, until), TrendItemDTO::getDate);
        var registrations = index(mapper.registrationsDaily(sinceTime, until), UserActivityItemDTO::getDate);
        var diaries = index(mapper.diaryUsersDaily(sinceTime, until), UserActivityItemDTO::getDate);
        var active = index(mapper.activeUsersDaily(sinceTime, until), UserActivityItemDTO::getDate);
        var emotions = index(mapper.emotionDaily(since, today), EmotionTrendItemDTO::getDate);
        List<TrendItemDTO> dailyTrend = new ArrayList<>();
        List<UserActivityItemDTO> userActivity = new ArrayList<>();
        List<EmotionTrendItemDTO> emotionTrend = new ArrayList<>();
        for (LocalDate day = since; !day.isAfter(today); day = day.plusDays(1)) {
            String date = day.toString();
            var chat = consultation.getOrDefault(date, TrendItemDTO.builder().date(date).sessionCount(0L).userCount(0L).build());
            dailyTrend.add(chat);
            userActivity.add(UserActivityItemDTO.builder().date(date)
                    .newUsers(registrations.containsKey(date) ? registrations.get(date).getNewUsers() : 0L)
                    .diaryUsers(diaries.containsKey(date) ? diaries.get(date).getDiaryUsers() : 0L)
                    .activeUsers(active.containsKey(date) ? active.get(date).getActiveUsers() : 0L)
                    .consultationUsers(chat.getUserCount()).build());
            var emotion = emotions.getOrDefault(date, EmotionTrendItemDTO.builder().date(date).sessionCount(0L).build());
            emotion.setAvgMoodScore(round1(emotion.getAvgMoodScore()));
            emotionTrend.add(emotion);
        }
        return DataAnalyticsOverviewDTO.builder().systemOverview(system)
                .consultationStats(ConsultationStatsDTO.builder().totalSessions(system.getTotalSesss())
                        .avgDurationMinutes(round1(mapper.averageMessageSpan(until))).dailyTrend(dailyTrend).build())
                .userActivity(userActivity).emotionTrend(emotionTrend).build();
    }

    private <T> Map<String, T> index(List<T> rows, Function<T, String> date) {
        return rows.stream().collect(Collectors.toMap(date, Function.identity()));
    }

    private Double round1(Double value) {
        return value == null ? null : Math.round(value * 10.0) / 10.0;
    }
}
