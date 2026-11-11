package org.example.qispringboot.DTO.response;

import java.time.LocalDate;
import java.util.List;

public record EmotionTrendResponseDTO(int days, LocalDate startDate, LocalDate endDate, List<Day> dailyTrend) {
    public record Day(LocalDate date, int diaryCount, Double moodScore, Double sleepQuality, Double stressLevel) {}
}
