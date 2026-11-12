package org.example.qispringboot.service;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.example.qispringboot.DTO.response.FileInfoDTO;
import org.example.qispringboot.common.ResultCode;
import org.example.qispringboot.entity.SysFileInfo;
import org.example.qispringboot.exception.BusinessException;
import org.example.qispringboot.mapper.SysFileInfoMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

@Slf4j
@Service
public class FileService {
    static final long MAX_FILE_BYTES = 5L * 1024 * 1024;
    private static final int MAX_IMAGE_SIDE = 8192;
    private static final long MAX_IMAGE_PIXELS = 20_000_000;
    private final Path uploadRoot;

    @Resource
    private SysFileInfoMapper fileInfoMapper;

    public FileService() { this(Path.of(System.getProperty("user.dir"), "uploads")); }
    FileService(Path root) { uploadRoot = root.toAbsolutePath().normalize(); }

    public FileInfoDTO upload(MultipartFile file, String businessType, String businessId,
                              String businessField, Long uploadUserId) {
        if (file == null || file.isEmpty()) throw new BusinessException(ResultCode.FILE_CONTENT_INVALID);
        if (file.getSize() > MAX_FILE_BYTES) throw new BusinessException(ResultCode.FILE_SIZE_EXCEEDED);
        if (!"ARTICLE".equals(businessType) || !"cover".equals(businessField))
            throw new BusinessException("此接口仅支持文章封面上传");
        if (businessId != null && businessId.length() > 100) throw new BusinessException("文件关联ID过长");
        String originalName = filename(file.getOriginalFilename());
        int dot = originalName.lastIndexOf('.');
        String extension = dot < 0 ? "" : originalName.substring(dot + 1).toLowerCase(Locale.ROOT);
        String format = switch (extension) {
            case "png" -> "png";
            case "jpg", "jpeg" -> "jpeg";
            default -> throw new BusinessException(ResultCode.FILE_TYPE_NOT_SUPPORTED);
        };
        String contentType = file.getContentType();
        if (StringUtils.hasText(contentType)) {
            contentType = contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
            if (!contentType.equals("application/octet-stream") && !contentType.equals("image/" + format))
                throw new BusinessException(ResultCode.FILE_TYPE_NOT_SUPPORTED);
        }
        byte[] data;
        try { data = file.getBytes(); }
        catch (IOException e) { throw new BusinessException(ResultCode.FILE_CONTENT_INVALID); }
        if (data.length > MAX_FILE_BYTES) throw new BusinessException(ResultCode.FILE_SIZE_EXCEEDED);
        validateImage(data, format);

        String date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        String suffix = format.equals("jpeg") ? "jpg" : "png";
        String name = UUID.randomUUID().toString().replace("-", "") + "." + suffix;
        Path directory = uploadRoot.resolve(date).normalize();
        Path target = directory.resolve(name).normalize();
        if (!target.startsWith(uploadRoot)) throw new BusinessException(ResultCode.FILE_NAME_INVALID);
        boolean created = false;
        try {
            Files.createDirectories(directory);
            // 只创建全新的文件，任何失败都只清理本次创建的目标。
            try (var output = Files.newOutputStream(target, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                created = true;
                output.write(data);
            }
            SysFileInfo info = SysFileInfo.builder().originalName(originalName)
                    .filePath("/uploads/" + date + "/" + name).fileSize((long) data.length).fileType(suffix)
                    .businessType("ARTICLE").businessId(businessId).businessField("cover")
                    .uploadUserId(uploadUserId).isTemp(false).status(1).createTime(LocalDateTime.now()).build();
            if (fileInfoMapper.insert(info) != 1) throw new BusinessException(ResultCode.FILE_SAVE_FAILED);
            return toDTO(info);
        } catch (IOException e) {
            if (created) removeNewFile(target);
            throw new BusinessException(ResultCode.FILE_SAVE_FAILED);
        } catch (RuntimeException e) {
            if (created) removeNewFile(target);
            throw e;
        }
    }

    private String filename(String value) {
        if (!StringUtils.hasText(value)) throw new BusinessException(ResultCode.FILE_NAME_INVALID);
        String normalized = value.replace('\\', '/');
        String name = normalized.substring(normalized.lastIndexOf('/') + 1);
        if (name.isBlank() || name.length() > 255 || name.chars().anyMatch(c -> Character.isISOControl(c)))
            throw new BusinessException(ResultCode.FILE_NAME_INVALID);
        return name;
    }

    private void validateImage(byte[] data, String format) {
        try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(data))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new BusinessException(ResultCode.FILE_CONTENT_INVALID);
            var reader = readers.next();
            try {
                if (!reader.getFormatName().equalsIgnoreCase(format)) throw new BusinessException(ResultCode.FILE_CONTENT_INVALID);
                reader.setInput(input, true, true);
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if (width < 1 || height < 1 || width > MAX_IMAGE_SIDE || height > MAX_IMAGE_SIDE
                        || (long) width * height > MAX_IMAGE_PIXELS)
                    throw new BusinessException("图片尺寸过大，宽高不能超过8192像素且总像素不能超过2000万");
                var image = reader.read(0);
                if (image == null) throw new BusinessException(ResultCode.FILE_CONTENT_INVALID);
                image.flush();
            } finally { reader.dispose(); }
        } catch (IOException | IllegalArgumentException e) {
            throw new BusinessException(ResultCode.FILE_CONTENT_INVALID);
        }
    }

    private void removeNewFile(Path target) {
        if (!target.startsWith(uploadRoot)) return;
        try { Files.deleteIfExists(target); }
        catch (IOException e) { log.warn("本次上传失败后的文件清理未完成：{}", target); }
    }

    private FileInfoDTO toDTO(SysFileInfo info) {
        return FileInfoDTO.builder().id(info.getId()).originalName(info.getOriginalName()).filePath(info.getFilePath())
                .fileSize(info.getFileSize()).fileType(info.getFileType()).businessType(info.getBusinessType())
                .businessId(info.getBusinessId()).businessField(info.getBusinessField()).uploadUserId(info.getUploadUserId())
                .isTemp(info.getIsTemp()).status(info.getStatus()).createTime(info.getCreateTime()).build();
    }
}
