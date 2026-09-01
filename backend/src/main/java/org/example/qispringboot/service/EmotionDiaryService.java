package org.example.qispringboot.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import org.example.qispringboot.DTO.command.EmotionDiaryCreateDTO;
import org.example.qispringboot.DTO.response.EmotionDiaryResponseDTO;
import org.example.qispringboot.entity.EmotionDiary;
import org.example.qispringboot.entity.User;
import org.example.qispringboot.mapper.EmotionDiaryMapper;
import org.example.qispringboot.mapper.UserMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class EmotionDiaryService {

    @Resource
    private EmotionDiaryMapper diaryMapper;

    @Resource
    private UserMapper userMapper;

    public void addDiary(Long userId, EmotionDiaryCreateDTO dto) {
        LocalDateTime now = LocalDateTime.now();
        EmotionDiary diary = EmotionDiary.builder()
                .userId(userId)
                .diaryDate(dto.getDiaryDate())
                .moodScore(dto.getMoodScore())
                .dominantEmotion(dto.getDominantEmotion())
                .emotionTriggers(dto.getEmotionTriggers())
                .diaryContent(dto.getDiaryContent())
                .sleepQuality(dto.getSleepQuality())
                .stressLevel(dto.getStressLevel())
                .createdAt(now)
                .updatedAt(now)
                .build();
        diaryMapper.insert(diary);
    }

    public Page<EmotionDiaryResponseDTO> adminPage(Long current, Long size, Long userId, String moodScoreRange) {
        LambdaQueryWrapper<EmotionDiary> qw = new LambdaQueryWrapper<>();
        qw.eq(userId != null, EmotionDiary::getUserId, userId);
        if (StringUtils.hasText(moodScoreRange)) {
            String[] parts = moodScoreRange.split("-");
            try {
                int min = Integer.parseInt(parts[0].trim());
                int max = Integer.parseInt(parts[1].trim());
                qw.between(EmotionDiary::getMoodScore, min, max);
            } catch (Exception ignored) {
                // invalid range, ignore filter
            }
        }
        qw.orderByDesc(EmotionDiary::getCreatedAt);

        Page<EmotionDiary> page = new Page<>(current, size);
        diaryMapper.selectPage(page, qw);

        Map<Long, String> nicknames = userMapper.selectList(null).stream()
                .collect(Collectors.toMap(User::getId, User::getDisplayName, (a, b) -> a));

        List<EmotionDiaryResponseDTO> records = page.getRecords().stream()
                .map(d -> toResponse(d, nicknames.get(d.getUserId())))
                .toList();

        Page<EmotionDiaryResponseDTO> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        result.setRecords(records);
        return result;
    }

    public void deleteDiary(Long id) {
        diaryMapper.deleteById(id);
    }

    private EmotionDiaryResponseDTO toResponse(EmotionDiary d, String nickname) {
        return EmotionDiaryResponseDTO.builder()
                .id(d.getId())
                .userId(d.getUserId())
                .nickname(nickname)
                .diaryDate(d.getDiaryDate())
                .moodScore(d.getMoodScore())
                .dominantEmotion(d.getDominantEmotion())
                .emotionTriggers(d.getEmotionTriggers())
                .diaryContent(d.getDiaryContent())
                .sleepQuality(d.getSleepQuality())
                .stressLevel(d.getStressLevel())
                .aiEmotionAnalysis(d.getAiEmotionAnalysis())
                .aiAnalysisUpdatedAt(d.getAiAnalysisUpdatedAt())
                .createdAt(d.getCreatedAt())
                .updatedAt(d.getUpdatedAt())
                .build();
    }
}