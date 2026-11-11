package org.example.qispringboot.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import org.example.qispringboot.DTO.command.EmotionDiaryCreateDTO;
import org.example.qispringboot.DTO.response.EmotionDiaryResponseDTO;
import org.example.qispringboot.entity.EmotionDiary;
import org.example.qispringboot.entity.User;
import org.example.qispringboot.exception.BusinessException;
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

    public Page<EmotionDiaryResponseDTO> myPage(Long userId, long current, long size) {
        Page<EmotionDiary> page = new Page<>(Math.max(1, current), Math.max(1, Math.min(100, size)));
        diaryMapper.selectPage(page, new LambdaQueryWrapper<EmotionDiary>()
                .eq(EmotionDiary::getUserId, userId)
                .orderByDesc(EmotionDiary::getDiaryDate).orderByDesc(EmotionDiary::getId));
        Page<EmotionDiaryResponseDTO> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        result.setRecords(page.getRecords().stream().map(d -> toResponse(d, null)).toList());
        return result;
    }

    public EmotionDiaryResponseDTO myDetail(Long userId, Long id) {
        EmotionDiary diary = diaryMapper.selectOne(new LambdaQueryWrapper<EmotionDiary>()
                .eq(EmotionDiary::getId, id).eq(EmotionDiary::getUserId, userId));
        if (diary == null) throw new BusinessException("日志不存在或无权访问");
        return toResponse(diary, null);
    }

    public void updateMine(Long userId, Long id, EmotionDiaryCreateDTO dto) {
        // 显式set允许清空可选字段，且更新条件始终包含当前登录用户。
        int changed = diaryMapper.update(null, new LambdaUpdateWrapper<EmotionDiary>()
                .eq(EmotionDiary::getId, id).eq(EmotionDiary::getUserId, userId)
                .set(EmotionDiary::getDiaryDate, dto.getDiaryDate())
                .set(EmotionDiary::getMoodScore, dto.getMoodScore())
                .set(EmotionDiary::getDominantEmotion, dto.getDominantEmotion())
                .set(EmotionDiary::getEmotionTriggers, dto.getEmotionTriggers())
                .set(EmotionDiary::getDiaryContent, dto.getDiaryContent())
                .set(EmotionDiary::getSleepQuality, dto.getSleepQuality())
                .set(EmotionDiary::getStressLevel, dto.getStressLevel())
                .set(EmotionDiary::getAiEmotionAnalysis, null)
                .set(EmotionDiary::getAiAnalysisUpdatedAt, null)
                .set(EmotionDiary::getUpdatedAt, LocalDateTime.now()));
        if (changed == 0) throw new BusinessException("日志不存在或无权访问");
    }

    public void deleteMine(Long userId, Long id) {
        int changed = diaryMapper.delete(new LambdaQueryWrapper<EmotionDiary>()
                .eq(EmotionDiary::getId, id).eq(EmotionDiary::getUserId, userId));
        if (changed == 0) throw new BusinessException("日志不存在或无权访问");
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
