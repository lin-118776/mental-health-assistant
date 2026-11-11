package org.example.qispringboot.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.example.qispringboot.entity.EmotionDiary;
import org.example.qispringboot.exception.BusinessException;
import org.example.qispringboot.mapper.EmotionDiaryMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class EmotionDiaryTrendTests {
    private EmotionDiaryService service;
    private EmotionDiaryMapper mapper;
    private final LocalDate today = LocalDate.of(2026, 11, 11);

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "test"), EmotionDiary.class);
        service = new EmotionDiaryService();
        mapper = mock(EmotionDiaryMapper.class);
        ReflectionTestUtils.setField(service, "diaryMapper", mapper);
        when(mapper.selectList(any(Wrapper.class))).thenReturn(List.of());
    }

    @Test
    void sevenDaysIncludesTodayAndScopesQueryToOwnerAndDiaryDates() {
        var result = service.myTrend(7L, 7, today);
        assertEquals(today.minusDays(6), result.startDate());
        assertEquals(today, result.endDate());
        assertEquals(7, result.days());
        assertEquals(7, result.dailyTrend().size());
        var query = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(mapper).selectList(query.capture());
        String sql = query.getValue().getSqlSegment();
        assertTrue(sql.contains("user_id ="));
        assertTrue(sql.contains("diary_date >="));
        assertTrue(sql.contains("diary_date <="));
        assertFalse(sql.contains("created_at"));
        var values = query.getValue().getParamNameValuePairs();
        assertTrue(values.containsValue(7L));
        assertTrue(values.containsValue(today.minusDays(6)));
        assertTrue(values.containsValue(today));
    }

    @Test
    void emptyDatesHaveNullMetricsRatherThanInventedZeroes() {
        var result = service.myTrend(7L, 7, today);
        for (int i = 0; i < 7; i++) {
            var day = result.dailyTrend().get(i);
            assertEquals(today.minusDays(6 - i), day.date());
            assertEquals(0, day.diaryCount());
            assertNull(day.moodScore());
            assertNull(day.sleepQuality());
            assertNull(day.stressLevel());
        }
    }

    @Test
    void averagesEachMetricIndependentlyAndPreservesMissingValues() {
        when(mapper.selectList(any(Wrapper.class))).thenReturn(List.of(
                diary(today, 4, null, 3), diary(today, 7, 5, 2), diary(today, 8, 4, null),
                diary(today.minusDays(1), 9, null, null)));
        var days = service.myTrend(7L, 7, today).dailyTrend();
        var latest = days.get(6);
        assertEquals(3, latest.diaryCount());
        assertEquals(6.3, latest.moodScore());
        assertEquals(4.5, latest.sleepQuality());
        assertEquals(2.5, latest.stressLevel());
        assertEquals(9.0, days.get(5).moodScore());
        assertNull(days.get(5).sleepQuality());
        assertNull(days.get(5).stressLevel());
    }

    @Test
    void thirtyDaysCrossesMonthAndLeapDayWithoutDroppingDates() {
        LocalDate end = LocalDate.of(2024, 3, 1);
        var result = service.myTrend(7L, 30, end);
        assertEquals(LocalDate.of(2024, 2, 1), result.startDate());
        assertEquals(30, result.dailyTrend().size());
        assertEquals(LocalDate.of(2024, 2, 29), result.dailyTrend().get(28).date());
        assertEquals(end, result.dailyTrend().get(29).date());
    }

    @Test
    void keepsBoundaryDatesAndExcludesOutOfWindowDates() {
        when(mapper.selectList(any(Wrapper.class))).thenReturn(List.of(
                diary(today.minusDays(6), 1, 1, 5), diary(today, 10, 5, 1),
                diary(today.minusDays(7), 2, 2, 2), diary(today.plusDays(1), 3, 3, 3)));
        var result = service.myTrend(7L, 7, today).dailyTrend();
        assertEquals(1.0, result.get(0).moodScore());
        assertEquals(10.0, result.get(6).moodScore());
        assertEquals(2, result.stream().mapToInt(d -> d.diaryCount()).sum());
    }

    @Test
    void invalidPeriodsAreRejectedBeforeReadingData() {
        for (int days : List.of(-1, 0, 1, 8, 31, 365)) {
            assertThrows(BusinessException.class, () -> service.myTrend(7L, days, today));
        }
        verifyNoInteractions(mapper);
    }

    @Test
    void freshQueryReflectsEditsAndDeletionsInsteadOfCachingOldValues() {
        when(mapper.selectList(any(Wrapper.class))).thenReturn(List.of(diary(today, 2, 1, 5)),
                List.of(diary(today, 8, 4, 2)), List.of());
        assertEquals(2.0, service.myTrend(7L, 7, today).dailyTrend().get(6).moodScore());
        assertEquals(8.0, service.myTrend(7L, 7, today).dailyTrend().get(6).moodScore());
        assertNull(service.myTrend(7L, 7, today).dailyTrend().get(6).moodScore());
    }

    private EmotionDiary diary(LocalDate date, Integer mood, Integer sleep, Integer stress) {
        return EmotionDiary.builder().userId(7L).diaryDate(date).moodScore(mood)
                .sleepQuality(sleep).stressLevel(stress).build();
    }
}
