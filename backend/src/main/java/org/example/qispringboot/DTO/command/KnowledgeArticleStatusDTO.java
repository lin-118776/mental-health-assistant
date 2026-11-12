package org.example.qispringboot.DTO.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class KnowledgeArticleStatusDTO {

    @NotBlank(message = "status is required")
    @Pattern(regexp = "[012]", message = "文章状态仅支持草稿、发布或下线")
    private String status;
}
