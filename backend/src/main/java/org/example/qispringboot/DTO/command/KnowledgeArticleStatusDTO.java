package org.example.qispringboot.DTO.command;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class KnowledgeArticleStatusDTO {

    @NotBlank(message = "status is required")
    private String status;
}