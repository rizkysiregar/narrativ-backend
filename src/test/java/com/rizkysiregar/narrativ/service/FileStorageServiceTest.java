package com.rizkysiregar.narrativ.service;

import com.rizkysiregar.narrativ.constant.GlobalMessage;
import com.rizkysiregar.narrativ.exception.BusinessException;
import com.rizkysiregar.narrativ.model.dto.ImageObject;
import com.rizkysiregar.narrativ.model.dto.ObjectStat;
import com.rizkysiregar.narrativ.service.impl.FileStorageServiceImpl;
import io.minio.*;
import io.minio.errors.ErrorResponseException;
import io.minio.messages.ErrorResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class FileStorageServiceTest {

    @Mock
    private MinioClient minioClient;

    private FileStorageServiceImpl fileStorageService;

    private static final String TEST_BUCKET = "test-bucket";

    @BeforeEach
    void setup() {
        fileStorageService = new FileStorageServiceImpl(minioClient, TEST_BUCKET);
    }


    @Test
    void uploadFile_Success_BucketExists() throws Exception {
        // Arrange
        MultipartFile file = mock(MultipartFile.class);
        when(file.getOriginalFilename()).thenReturn("image.png");
        when(file.getSize()).thenReturn(10L);
        when(file.getContentType()).thenReturn("image/png");
        when(file.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[]{1, 2, 3}));

        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);
        // stub putObject to return a response
        when(minioClient.putObject(any(PutObjectArgs.class)))
                .thenReturn(mock(ObjectWriteResponse.class));

        String userId = "user-1";
        String featureName = "POST_FEED";

        // Act
        String objectName = fileStorageService.uploadFile(file, userId, featureName);

        // Assert
        assertNotNull(objectName);
        assertTrue(objectName.startsWith(userId + "/" + featureName + "/"));
        assertTrue(objectName.endsWith(".png"));
        verify(minioClient, times(1)).bucketExists(any(BucketExistsArgs.class));
        verify(minioClient, never()).makeBucket(any(MakeBucketArgs.class));
        verify(minioClient, times(1)).putObject(any(PutObjectArgs.class));
    }

    @Test
    void uploadFile_Success_BucketCreatedWhenMissing() throws Exception {
        // Arrange
        MultipartFile file = mock(MultipartFile.class);
        when(file.getOriginalFilename()).thenReturn("photo.jpg");
        when(file.getSize()).thenReturn(20L);
        when(file.getContentType()).thenReturn("image/jpeg");
        when(file.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[]{4, 5, 6}));

        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(false);
        doNothing().when(minioClient).makeBucket(any(MakeBucketArgs.class));
        when(minioClient.putObject(any(PutObjectArgs.class)))
                .thenReturn(mock(ObjectWriteResponse.class));

        // Act
        String objectName = fileStorageService.uploadFile(file, "u2", "AVATAR");

        // Assert
        assertNotNull(objectName);
        assertTrue(objectName.startsWith("u2/AVATAR/"));
        assertTrue(objectName.endsWith(".jpg"));
        verify(minioClient, times(1)).bucketExists(any(BucketExistsArgs.class));
        verify(minioClient, times(1)).makeBucket(any(MakeBucketArgs.class));
        verify(minioClient, times(1)).putObject(any(PutObjectArgs.class));
    }

    @Test
    void getPresignUrl_Success() throws Exception {
        // Arrange
        when(minioClient.getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class)))
                .thenReturn("http://localhost:9000/test-bucket/u1/POST/x.png");

        // Act
        String url = fileStorageService.getPresignUrl("u1/POST/x.png");

        // Assert
        assertEquals("http://localhost:9000/test-bucket/u1/POST/x.png", url);
        verify(minioClient, times(1)).getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class));
    }

    @Test
    void getPresignUploadUrl_Success() throws Exception {
        // Arrange
        when(minioClient.getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class)))
                .thenReturn("http://localhost:9000/test-bucket/u1/POST_FEED/x.mp4");

        // Act
        String url = fileStorageService.getPresignUploadUrl("u1/POST_FEED/x.mp4", "video/mp4");

        // Assert
        assertEquals("http://localhost:9000/test-bucket/u1/POST_FEED/x.mp4", url);
        verify(minioClient, times(1)).getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class));
    }

    @Test
    void statObject_Success() throws Exception {
        // Arrange
        String objectName = "u1/POST_FEED/x.mp4";
        StatObjectResponse stat = mock(StatObjectResponse.class);
        when(stat.size()).thenReturn(1024L);
        when(stat.contentType()).thenReturn("video/mp4");
        when(minioClient.statObject(any(StatObjectArgs.class))).thenReturn(stat);

        // Act
        ObjectStat result = fileStorageService.statObject(objectName);

        // Assert
        assertNotNull(result);
        assertEquals(1024L, result.getSize());
        assertEquals("video/mp4", result.getContentType());
        verify(minioClient, times(1)).statObject(any(StatObjectArgs.class));
    }

    @Test
    void statObject_NotFound_ThrowsBusinessException() throws Exception {
        // Arrange
        ErrorResponse errorResponse = new ErrorResponse("NoSuchKey", "The specified key does not exist.",
                TEST_BUCKET, "u1/POST_FEED/x.mp4", "/" + TEST_BUCKET + "/u1/POST_FEED/x.mp4", "req", "host");
        when(minioClient.statObject(any(StatObjectArgs.class)))
                .thenThrow(new ErrorResponseException(errorResponse, null, null));

        // Act & Assert
        BusinessException ex = assertThrows(BusinessException.class,
                () -> fileStorageService.statObject("u1/POST_FEED/x.mp4"));
        assertEquals(GlobalMessage.NOT_FOUND.getMessage(), ex.getMessage());
    }

    @Test
    void getObject_Success() throws Exception {
        // Arrange
        String objectName = "u1/POST/x.png";
        long expectedSize = 10L;
        String expectedContentType = "image/png";

        StatObjectResponse stat = mock(StatObjectResponse.class);
        when(stat.size()).thenReturn(expectedSize);
        when(stat.contentType()).thenReturn(expectedContentType);
        when(minioClient.statObject(any(StatObjectArgs.class))).thenReturn(stat);

        GetObjectResponse responseStream = mock(GetObjectResponse.class);
        when(minioClient.getObject(any(GetObjectArgs.class))).thenReturn(responseStream);

        // Act
        ImageObject result = fileStorageService.getObject(objectName, TEST_BUCKET);

        // Assert
        assertNotNull(result);
        assertSame(responseStream, result.getStream());
        assertEquals(expectedSize, result.getSize());
        assertEquals(org.springframework.http.MediaType.parseMediaType(expectedContentType), result.getContentType());

        verify(minioClient, times(1)).statObject(any(StatObjectArgs.class));
        verify(minioClient, times(1)).getObject(any(GetObjectArgs.class));
    }


}
