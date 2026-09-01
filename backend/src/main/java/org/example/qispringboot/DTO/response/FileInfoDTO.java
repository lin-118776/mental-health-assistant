package org.example.qispringboot.DTO.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileInfoDTO {

    private Long id;

    private String originalName;

    private String filePath;

    private Long fileSize;

    private String fileType;

    private String businessType;

    private String businessId;

    private String businessField;

    private Long uploadUserId;

    private Boolean isTemp;

    private Integer status;

    private LocalDateTime createTime;
}