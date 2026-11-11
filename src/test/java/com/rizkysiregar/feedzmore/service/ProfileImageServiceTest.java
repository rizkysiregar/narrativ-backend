package com.rizkysiregar.feedzmore.service;

import com.rizkysiregar.feedzmore.constant.GlobalMessage;
import com.rizkysiregar.feedzmore.exception.BusinessException;
import com.rizkysiregar.feedzmore.model.dto.ObjectStat;
import com.rizkysiregar.feedzmore.model.dto.UserDetailDTO;
import com.rizkysiregar.feedzmore.model.entity.UserEntity;
import com.rizkysiregar.feedzmore.model.request.CreateProfileImageUploadUrlRequest;
import com.rizkysiregar.feedzmore.model.request.SetProfileImageRequest;
import com.rizkysiregar.feedzmore.model.response.ProfileImageUploadUrlResponse;
import com.rizkysiregar.feedzmore.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProfileImageServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private FileStorageService fileStorageService;
    @Mock
    private ProfileImageUrlResolver profileImageUrlResolver;

    @InjectMocks
    private ProfileImageService profileImageService;

    private final UUID userId = UUID.randomUUID();
    private UserEntity user;

    @BeforeEach
    void setUp() {
        user = new UserEntity();
        user.setId(userId);
        user.setUsername("someone");
        user.setEmail("someone@mail.com");
        user.setFirstName("Some");
        user.setLastName("One");
        when(userRepository.findActiveById(userId)).thenReturn(Optional.of(user));
    }

    private String key(String file) {
        return userId + "/PROFILE_PICTURE/" + file;
    }

    private void assertFails(GlobalMessage expected, HttpStatus status, Runnable action) {
        BusinessException e = assertThrows(BusinessException.class, action::run);
        assertEquals(expected.getMessage(), e.getMessage());
        assertEquals(status, e.getHttpStatus());
    }

    // ------------------------------------------------------------ upload url

    @Test
    void createUploadUrl_buildsKeyUnderUserIdAndPresigns() throws Exception {
        when(fileStorageService.getPresignUploadUrl(anyString(), anyString())).thenReturn("https://minio/put");

        ProfileImageUploadUrlResponse response =
                profileImageService.createUploadUrl(userId, new CreateProfileImageUploadUrlRequest("image/png"));

        assertTrue(response.objectKey().startsWith(userId + "/PROFILE_PICTURE/"));
        assertTrue(response.objectKey().endsWith(".png"));
        assertEquals("https://minio/put", response.presignedUrl());
    }

    @Test
    void createUploadUrl_rejectsGifAndNonImages() {
        assertFails(GlobalMessage.BAD_REQUEST, HttpStatus.BAD_REQUEST,
                () -> profileImageService.createUploadUrl(userId, new CreateProfileImageUploadUrlRequest("image/gif")));
        assertFails(GlobalMessage.BAD_REQUEST, HttpStatus.BAD_REQUEST,
                () -> profileImageService.createUploadUrl(userId, new CreateProfileImageUploadUrlRequest("video/mp4")));
    }

    // ------------------------------------------------------------------- set

    @Test
    void setProfileImage_savesKeyAndDeletesPreviousUpload() {
        user.setProfileImage(key("old.jpg"));
        when(fileStorageService.statObject(key("new.jpg"))).thenReturn(new ObjectStat(1024, "image/jpeg"));
        when(userRepository.save(user)).thenReturn(user);
        when(profileImageUrlResolver.resolve(key("new.jpg"))).thenReturn("https://signed/new");

        UserDetailDTO dto = profileImageService.setProfileImage(userId, new SetProfileImageRequest(key("new.jpg")));

        assertEquals(key("new.jpg"), user.getProfileImage());
        assertEquals("https://signed/new", dto.profileImageUrl());
        verify(fileStorageService).deleteObject(key("old.jpg"));
    }

    @Test
    void setProfileImage_keepsAnExternalPreviousUrl() {
        user.setProfileImage("https://lh3.googleusercontent.com/a/photo");
        when(fileStorageService.statObject(key("new.png"))).thenReturn(new ObjectStat(1024, "image/png"));
        when(userRepository.save(user)).thenReturn(user);

        profileImageService.setProfileImage(userId, new SetProfileImageRequest(key("new.png")));

        verify(fileStorageService, never()).deleteObject(anyString());
    }

    @Test
    void setProfileImage_rejectsAnotherUsersKey() {
        String foreign = UUID.randomUUID() + "/PROFILE_PICTURE/x.jpg";

        assertFails(GlobalMessage.ACCESS_DENIED, HttpStatus.FORBIDDEN,
                () -> profileImageService.setProfileImage(userId, new SetProfileImageRequest(foreign)));
        verify(userRepository, never()).save(any());
    }

    @Test
    void setProfileImage_rejectsPostMediaKeyAndBadExtension() {
        assertFails(GlobalMessage.ACCESS_DENIED, HttpStatus.FORBIDDEN,
                () -> profileImageService.setProfileImage(userId, new SetProfileImageRequest(userId + "/POST_FEED/x.jpg")));
        assertFails(GlobalMessage.ACCESS_DENIED, HttpStatus.FORBIDDEN,
                () -> profileImageService.setProfileImage(userId, new SetProfileImageRequest(key("x.exe"))));
    }

    @Test
    void setProfileImage_rejectsOversizedObjectAndRemovesIt() {
        when(fileStorageService.statObject(key("big.jpg")))
                .thenReturn(new ObjectStat(ProfileImageService.MAX_PROFILE_IMAGE_SIZE + 1, "image/jpeg"));

        assertFails(GlobalMessage.BAD_REQUEST_FILE_TO_LARGE, HttpStatus.BAD_REQUEST,
                () -> profileImageService.setProfileImage(userId, new SetProfileImageRequest(key("big.jpg"))));
        verify(fileStorageService).deleteObject(key("big.jpg"));
        verify(userRepository, never()).save(any());
    }

    @Test
    void setProfileImage_rejectsNonImageContentType() {
        when(fileStorageService.statObject(key("fake.jpg"))).thenReturn(new ObjectStat(100, "text/html"));

        assertFails(GlobalMessage.BAD_REQUEST, HttpStatus.BAD_REQUEST,
                () -> profileImageService.setProfileImage(userId, new SetProfileImageRequest(key("fake.jpg"))));
        verify(fileStorageService).deleteObject(key("fake.jpg"));
    }

    // ---------------------------------------------------------------- delete

    @Test
    void deleteProfileImage_clearsColumnAndDeletesObject() {
        user.setProfileImage(key("old.webp"));
        when(userRepository.save(user)).thenReturn(user);

        UserDetailDTO dto = profileImageService.deleteProfileImage(userId);

        assertNull(user.getProfileImage());
        assertNull(dto.profileImageUrl());
        verify(fileStorageService).deleteObject(key("old.webp"));
    }

    @Test
    void deleteProfileImage_withoutPictureOrWithExternalUrlDeletesNothing() {
        when(userRepository.save(user)).thenReturn(user);
        profileImageService.deleteProfileImage(userId);

        user.setProfileImage("https://lh3.googleusercontent.com/a/photo");
        profileImageService.deleteProfileImage(userId);

        verify(fileStorageService, never()).deleteObject(anyString());
    }
}
