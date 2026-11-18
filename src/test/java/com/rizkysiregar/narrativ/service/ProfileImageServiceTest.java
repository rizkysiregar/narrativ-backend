package com.rizkysiregar.narrativ.service;

import com.rizkysiregar.narrativ.constant.GlobalMessage;
import com.rizkysiregar.narrativ.exception.BusinessException;
import com.rizkysiregar.narrativ.model.dto.ObjectStat;
import com.rizkysiregar.narrativ.model.dto.PresignedUploadForm;
import com.rizkysiregar.narrativ.model.dto.UserDetailDTO;
import com.rizkysiregar.narrativ.model.entity.UserEntity;
import com.rizkysiregar.narrativ.model.request.CreateProfileImageUploadUrlRequest;
import com.rizkysiregar.narrativ.model.request.SetProfileImageRequest;
import com.rizkysiregar.narrativ.model.response.ProfileImageUploadUrlResponse;
import com.rizkysiregar.narrativ.repository.UserRepository;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
        PresignedUploadForm form = new PresignedUploadForm("https://minio/bucket", java.util.Map.of("policy", "p"));
        when(fileStorageService.getPresignUploadForm(anyString(), eq("image/png"), eq(2L * 1024 * 1024))).thenReturn(form);

        ProfileImageUploadUrlResponse response =
                profileImageService.createUploadUrl(userId, new CreateProfileImageUploadUrlRequest("image/png"));

        assertTrue(response.objectKey().startsWith(userId + "/PROFILE_PICTURE/"));
        assertTrue(response.objectKey().endsWith(".png"));
        assertEquals("https://minio/put", response.presignedUrl());
        // the POST form is capped at 2 MB by storage itself
        assertEquals(form, response.uploadForm());
        verify(fileStorageService).getPresignUploadForm(response.objectKey(), "image/png", 2L * 1024 * 1024);
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

    @Test
    void setProfileImage_retriesATransientStatFailure() {
        when(fileStorageService.statObject(key("new.jpg")))
                .thenThrow(new BusinessException(GlobalMessage.INTERNAL_ERROR, HttpStatus.INTERNAL_SERVER_ERROR))
                .thenReturn(new ObjectStat(1024, "image/jpeg"));
        when(userRepository.save(user)).thenReturn(user);

        profileImageService.setProfileImage(userId, new SetProfileImageRequest(key("new.jpg")));

        assertEquals(key("new.jpg"), user.getProfileImage());
        verify(fileStorageService, times(2)).statObject(key("new.jpg"));
    }

    @Test
    void setProfileImage_isRefusedWhenStorageCannotTellTheSize() {
        // a presigned PUT cannot cap the size: an object that cannot be checked is not accepted unchecked
        when(fileStorageService.statObject(key("new.jpg")))
                .thenThrow(new BusinessException(GlobalMessage.INTERNAL_ERROR, HttpStatus.INTERNAL_SERVER_ERROR));

        assertFails(GlobalMessage.UPLOAD_NOT_VERIFIED, HttpStatus.SERVICE_UNAVAILABLE,
                () -> profileImageService.setProfileImage(userId, new SetProfileImageRequest(key("new.jpg"))));

        verify(fileStorageService, times(3)).statObject(key("new.jpg"));
        verify(fileStorageService, never()).deleteObject(anyString());
        verify(userRepository, never()).save(any());
    }

    @Test
    void setProfileImage_missingObjectIsFinalAndNotRetried() {
        when(fileStorageService.statObject(key("gone.jpg")))
                .thenThrow(new BusinessException(GlobalMessage.NOT_FOUND, HttpStatus.NOT_FOUND));

        assertFails(GlobalMessage.NOT_FOUND, HttpStatus.NOT_FOUND,
                () -> profileImageService.setProfileImage(userId, new SetProfileImageRequest(key("gone.jpg"))));
        verify(fileStorageService, times(1)).statObject(key("gone.jpg"));
        verify(userRepository, never()).save(any());
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
