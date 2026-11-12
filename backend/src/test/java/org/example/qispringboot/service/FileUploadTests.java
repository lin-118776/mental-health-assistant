package org.example.qispringboot.service;

import org.example.qispringboot.entity.SysFileInfo;
import org.example.qispringboot.exception.BusinessException;
import org.example.qispringboot.mapper.SysFileInfoMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FileUploadTests {
    @TempDir Path root;
    private FileService service;
    private SysFileInfoMapper mapper;
    private AtomicReference<SysFileInfo> stored;

    @BeforeEach
    void setUp() {
        service = new FileService(root);
        mapper = mock(SysFileInfoMapper.class);
        stored = new AtomicReference<>();
        when(mapper.insert(any(SysFileInfo.class))).thenAnswer(invocation -> {
            SysFileInfo info = invocation.getArgument(0);
            info.setId(1L);
            stored.set(info);
            return 1;
        });
        ReflectionTestUtils.setField(service, "fileInfoMapper", mapper);
    }

    @Test
    void savesRealImageWithGeneratedPathAndTrustedUploadOwner() throws Exception {
        byte[] bytes = image("png", 2, 2);
        var result = service.upload(file("../../cover.PNG", "image/png", bytes), "ARTICLE", "draft-id", "cover", 7L);
        assertEquals("cover.PNG", result.getOriginalName());
        assertTrue(result.getFilePath().matches("/uploads/\\d{4}/\\d{2}/\\d{2}/[0-9a-f]{32}\\.png"));
        Path target = root.resolve(result.getFilePath().substring("/uploads/".length())).normalize();
        assertTrue(target.startsWith(root));
        assertArrayEquals(bytes, Files.readAllBytes(target));
        assertEquals(7L, stored.get().getUploadUserId());
        assertEquals("ARTICLE", result.getBusinessType());
        assertEquals("cover", result.getBusinessField());
        assertEquals((long) bytes.length, result.getFileSize());
        assertNotNull(result.getId());
    }

    @Test
    void acceptsJpegAndNormalizesStoredExtension() throws Exception {
        var result = service.upload(file("photo.JPEG", "image/jpeg", image("jpeg", 2, 2)), "ARTICLE", null, "cover", 7L);
        assertTrue(result.getFilePath().endsWith(".jpg"));
        assertEquals("jpg", result.getFileType());
    }

    @Test
    void rejectsHtmlDisguisedAsImageTruncatedContentAndFormatMismatch() throws Exception {
        for (var invalid : java.util.List.of(file("fake.png", "image/png", "<html>not an image</html>".getBytes()),
                file("truncated.png", "image/png", Arrays.copyOf(image("png", 2, 2), 16)),
                file("mismatch.jpg", "image/jpeg", image("png", 2, 2)),
                file("no-extension", "image/png", image("png", 2, 2)))) {
            assertThrows(BusinessException.class, () -> service.upload(invalid, "ARTICLE", null, "cover", 7L));
        }
        verifyNoInteractions(mapper);
        assertEquals(0, files());
    }

    @Test
    void rejectsEmptyOversizedAndWrongMimeUploadsBeforeStorage() throws Exception {
        var empty = file("empty.png", "image/png", new byte[0]);
        var large = file("large.png", "image/png", new byte[(int) FileService.MAX_FILE_BYTES + 1]);
        var wrongMime = file("cover.png", "text/html", image("png", 2, 2));
        for (var invalid : java.util.List.of(empty, large, wrongMime))
            assertThrows(BusinessException.class, () -> service.upload(invalid, "ARTICLE", null, "cover", 7L));
        verifyNoInteractions(mapper);
        assertEquals(0, files());
    }

    @Test
    void exactFiveMiBBoundaryIsAccepted() throws Exception {
        byte[] bytes = Arrays.copyOf(image("png", 2, 2), (int) FileService.MAX_FILE_BYTES);
        var result = service.upload(file("limit.png", "image/png", bytes), "ARTICLE", null, "cover", 7L);
        assertEquals(FileService.MAX_FILE_BYTES, result.getFileSize());
        assertEquals(1, files());
    }

    @Test
    void rejectsHugeImageDimensionsAndUnsupportedBusinessMetadata() throws Exception {
        assertThrows(BusinessException.class, () -> service.upload(file("wide.png", "image/png", image("png", 8193, 1)), "ARTICLE", null, "cover", 7L));
        var valid = file("cover.png", "image/png", image("png", 2, 2));
        assertThrows(BusinessException.class, () -> service.upload(valid, "OTHER", null, "cover", 7L));
        assertThrows(BusinessException.class, () -> service.upload(valid, "ARTICLE", null, "other", 7L));
        assertThrows(BusinessException.class, () -> service.upload(valid, "ARTICLE", "x".repeat(101), "cover", 7L));
        verifyNoInteractions(mapper);
    }

    @Test
    void databaseFailureRemovesOnlyNewFileAndPreservesExistingFile() throws Exception {
        Path existing = root.resolve("keep.png");
        Files.writeString(existing, "keep");
        when(mapper.insert(any(SysFileInfo.class))).thenThrow(new DataIntegrityViolationException("test database failure"));
        assertThrows(DataIntegrityViolationException.class, () -> service.upload(file("cover.png", "image/png", image("png", 2, 2)), "ARTICLE", null, "cover", 7L));
        assertEquals(1, files());
        assertEquals("keep", Files.readString(existing));
    }

    @Test
    void failedInsertAndStorageErrorDoNotReportSuccess() throws Exception {
        when(mapper.insert(any(SysFileInfo.class))).thenReturn(0);
        assertThrows(BusinessException.class, () -> service.upload(file("cover.png", "image/png", image("png", 2, 2)), "ARTICLE", null, "cover", 7L));
        assertEquals(0, files());
        Path blocker = root.resolve("blocker");
        Files.writeString(blocker, "existing");
        var blocked = new FileService(blocker);
        ReflectionTestUtils.setField(blocked, "fileInfoMapper", mapper);
        assertThrows(BusinessException.class, () -> blocked.upload(file("cover.png", "image/png", image("png", 2, 2)), "ARTICLE", null, "cover", 7L));
        assertEquals("existing", Files.readString(blocker));
    }

    @Test
    void multipartLimitsAreConfiguredUnderSpringServlet() {
        var yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application.yml"));
        var properties = yaml.getObject();
        assertNotNull(properties);
        assertEquals("5MB", properties.getProperty("spring.servlet.multipart.max-file-size"));
        assertEquals("6MB", properties.getProperty("spring.servlet.multipart.max-request-size"));
        assertNull(properties.getProperty("jwt.servlet.multipart.max-file-size"));
    }

    private long files() throws Exception {
        try (var paths = Files.walk(root)) { return paths.filter(Files::isRegularFile).count(); }
    }

    private MockMultipartFile file(String name, String type, byte[] bytes) {
        return new MockMultipartFile("file", name, type, bytes);
    }

    private byte[] image(String format, int width, int height) throws Exception {
        var image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        var output = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(image, format, output));
        image.flush();
        return output.toByteArray();
    }
}
