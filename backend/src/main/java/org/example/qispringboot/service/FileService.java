package org.example.qispringboot.service;

import jakarta.annotation.Resource;
import org.example.qispringboot.DTO.response.FileInfoDTO;
import org.example.qispringboot.common.ResultCode;
import org.example.qispringboot.entity.SysFileInfo;
import org.example.qispringboot.exception.BusinessException;
import org.example.qispringboot.mapper.SysFileInfoMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Service
public class FileService {

    private static final String UPLOAD_ROOT = "uploads";

    @Resource
    private SysFileInfoMapper fileInfoMapper;

    public FileInfoDTO upload(MultipartFile file, String businessType, String businessId,
                              String businessField, Long uploadUserId) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ResultCode.FILE_CONTENT_INVALID);
        }
        String originalName = file.getOriginalFilename();
        if (originalName == null) {
            originalName = "unnamed";
        }
        String ext = extensionOf(originalName);
        LocalDate now = LocalDate.now();
        String relativeDir = "/" + UPLOAD_ROOT + "/" + now.format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        String dir = System.getProperty("user.dir")
                + java.io.File.separator + UPLOAD_ROOT
                + java.io.File.separator + now.format(DateTimeFormatter.ofPattern("yyyy"))
                + java.io.File.separator + now.format(DateTimeFormatter.ofPattern("MM"))
                + java.io.File.separator + now.format(DateTimeFormatter.ofPattern("dd"));
        try {
            Files.createDirectories(Path.of(dir));
            String name = UUID.randomUUID().toString().replace("-", "") + (ext.isEmpty() ? "" : "." + ext);
            Path target = Path.of(dir, name);
            file.transferTo(target.toAbsolutePath());
            String filePath = relativeDir + "/" + name;

            SysFileInfo info = SysFileInfo.builder()
                    .originalName(originalName)
                    .filePath(filePath)
                    .fileSize(file.getSize())
                    .fileType(ext.isEmpty() ? "file" : ext.toLowerCase())
                    .businessType(businessType)
                    .businessId(businessId)
                    .businessField(businessField)
                    .uploadUserId(uploadUserId)
                    .isTemp(false)
                    .status(1)
                    .createTime(LocalDateTime.now())
                    .build();
            fileInfoMapper.insert(info);
            return toDTO(info);
        } catch (IOException e) {
            throw new BusinessException(ResultCode.FILE_SAVE_FAILED);
        }
    }

    private String extensionOf(String filename) {
        int idx = filename.lastIndexOf('.');
        if (idx < 0 || idx == filename.length() - 1) {
            return "";
        }
        return filename.substring(idx + 1);
    }

    private FileInfoDTO toDTO(SysFileInfo info) {
        return FileInfoDTO.builder()
                .id(info.getId())
                .originalName(info.getOriginalName())
                .filePath(info.getFilePath())
                .fileSize(info.getFileSize())
                .fileType(info.getFileType())
                .businessType(info.getBusinessType())
                .businessId(info.getBusinessId())
                .businessField(info.getBusinessField())
                .uploadUserId(info.getUploadUserId())
                .isTemp(info.getIsTemp())
                .status(info.getStatus())
                .createTime(info.getCreateTime())
                .build();
    }
}