package org.example.qispringboot.DTO.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeArticleResponseDTO {

    private String id;

    private Long categoryId;

    private String categoryName;

    private String title;

    private String summary;

    private String content;

    private String coverImage;

    private String tags;

    private List<String> tagArray;

    private Long authorId;

    private String authorName;

    private Integer readCount;

    private Integer status;

    private LocalDateTime publishedAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    public static List<String> splitTags(String tags) {
        if (tags == null || tags.trim().isEmpty()) {
            return List.of();
        }
        return java.util.Arrays.stream(tags.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}