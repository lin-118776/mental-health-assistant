package org.example.qispringboot.DTO.command;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Data
public class EmotionDiaryCreateDTO {

    @NotNull(message = "diaryDate is required")
    private LocalDate diaryDate;

    @NotNull(message = "moodScore is required")
    @Min(value = 1, message = "moodScore must be between 1 and 10")
    @Max(value = 10, message = "moodScore must be between 1 and 10")
    private Integer moodScore;

    private String dominantEmotion;

    private String emotionTriggers;

    private String diaryContent;

    @Min(value = 1, message = "sleepQuality must be between 1 and 5")
    @Max(value = 5, message = "sleepQuality must be between 1 and 5")
    private Integer sleepQuality;

    @Min(value = 1, message = "stressLevel must be between 1 and 5")
    @Max(value = 5, message = "stressLevel must be between 1 and 5")
    private Integer stressLevel;
}