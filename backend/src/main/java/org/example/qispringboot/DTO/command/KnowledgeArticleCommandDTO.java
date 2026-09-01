package org.example.qispringboot.DTO.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class KnowledgeArticleCommandDTO {

    private String id;

    @NotBlank(message = "title is required")
    private String title;

    @NotNull(message = "categoryId is required")
    private Long categoryId;

    private String summary;

    private String content;

    private String coverImage;

    private String tags;
}