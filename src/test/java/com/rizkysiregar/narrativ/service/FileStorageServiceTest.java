package com.rizkysiregar.narrativ.service;

import com.rizkysiregar.narrativ.constant.GlobalMessage;
import com.rizkysiregar.narrativ.exception.BusinessException;
import com.rizkysiregar.narrativ.model.dto.ImageObject;
import com.rizkysiregar.narrativ.model.dto.ObjectStat;
import com.rizkysiregar.narrativ.service.impl.FileStorageServiceImpl;
import io.minio.*;
import io.minio.errors.ErrorResponseException;
import io.minio.credentials.Credentials;
import io.minio.messages.ErrorResponse;
import com.rizkysiregar.narrativ.model.dto.PresignedUploadForm;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.io.ByteArrayInputStream;
import java.util.Base64;
import java.util.Map;

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
        fileStorageService = new FileStorageServiceImpl(minioClient, TEST_BUCKET, "https://storage.example.com/");
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
    void getPresignUploadForm_pinsKeyTypeAndMaximumSize() throws Exception {
        when(minioClient.getPresignedPostFormData(any(PostPolicy.class)))
                .thenReturn(Map.of("policy", "p", "x-amz-signature", "s"));
        ArgumentCaptor<PostPolicy> policy = ArgumentCaptor.forClass(PostPolicy.class);

        PresignedUploadForm form = fileStorageService.getPresignUploadForm("u1/POST_FEED/a.jpg", "image/jpeg", 5_242_880L);

        assertEquals("https://storage.example.com/test-bucket", form.url());
        assertEquals("u1/POST_FEED/a.jpg", form.fields().get("key"));
        assertEquals("image/jpeg", form.fields().get("Content-Type"));
        assertEquals("s", form.fields().get("x-amz-signature"));

        // the conditions storage enforces, read back from the signed policy document
        verify(minioClient).getPresignedPostFormData(policy.capture());
        String document = new String(Base64.getDecoder().decode(policy.getValue()
                .formData(new Credentials("ak", "sk", null, null), "us-east-1").get("policy")));
        assertTrue(document.contains("[\"content-length-range\",1,5242880]"), document);
        assertTrue(document.contains("\"$key\",\"u1/POST_FEED/a.jpg\""), document);
        assertTrue(document.contains("\"$Content-Type\",\"image/jpeg\""), document);
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
