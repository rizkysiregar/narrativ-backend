package com.rizkysiregar.narrativ.service;

import com.rizkysiregar.narrativ.constant.GlobalMessage;
import com.rizkysiregar.narrativ.constant.ObjectStorageTypeGroup;
import com.rizkysiregar.narrativ.exception.BusinessException;
import com.rizkysiregar.narrativ.model.dto.ObjectStat;
import com.rizkysiregar.narrativ.model.dto.UserDetailDTO;
import com.rizkysiregar.narrativ.model.entity.UserEntity;
import com.rizkysiregar.narrativ.model.request.CreateProfileImageUploadUrlRequest;
import com.rizkysiregar.narrativ.model.request.SetProfileImageRequest;
import com.rizkysiregar.narrativ.model.response.ProfileImageUploadUrlResponse;
import com.rizkysiregar.narrativ.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

/**
 * Add, replace and remove the caller's profile picture. The client asks for a presigned PUT url, uploads straight
 * to object storage, then confirms the object key. Keys are {@code userId/PROFILE_PICTURE/uuid.ext}, so they stay
 * valid when the username changes. The previous uploaded picture is deleted once it is replaced or removed.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ProfileImageService {

    static final long MAX_PROFILE_IMAGE_SIZE = 2L * 1024 * 1024;
    private static final long PRESIGN_UPLOAD_EXPIRY_SECONDS = 15L * 60;
    private static final Map<String, String> CONTENT_TYPE_EXTENSIONS = Map.of(
            "image/jpeg", ".jpg",
            "image/png", ".png",
            "image/webp", ".webp"
    );

    private final UserRepository userRepository;
    private final FileStorageService fileStorageService;
    private final ProfileImageUrlResolver profileImageUrlResolver;

    public ProfileImageUploadUrlResponse createUploadUrl(UUID userId, CreateProfileImageUploadUrlRequest request) {
        UserEntity user = findActiveUserOrThrow(userId);

        String extension = CONTENT_TYPE_EXTENSIONS.get(request.contentType());
        if (extension == null) {
            throw new BusinessException(GlobalMessage.BAD_REQUEST, HttpStatus.BAD_REQUEST);
        }

        String objectKey = prefixOf(user) + UUID.randomUUID() + extension;
        try {
            String url = fileStorageService.getPresignUploadUrl(objectKey, request.contentType());
            return new ProfileImageUploadUrlResponse(url, objectKey, PRESIGN_UPLOAD_EXPIRY_SECONDS);
        } catch (Exception e) {
            log.error("PROFILE.IMAGE.PRESIGN.UPLOAD.FAILED userId={} reason={}", userId, e.getMessage());
            throw new BusinessException(GlobalMessage.INTERNAL_ERROR, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Transactional
    public UserDetailDTO setProfileImage(UUID userId, SetProfileImageRequest request) {
        UserEntity user = findActiveUserOrThrow(userId);
        String objectKey = request.objectKey();

        if (!objectKey.startsWith(prefixOf(user)) || !hasAllowedExtension(objectKey)) {
            throw new BusinessException(GlobalMessage.ACCESS_DENIED, HttpStatus.FORBIDDEN);
        }

        // a missing object is rejected, and so is one whose size and type storage cannot tell right now
        ObjectStat stat = UploadedObjectStats.statOrRefuse(fileStorageService, objectKey);
        if (stat.getSize() > MAX_PROFILE_IMAGE_SIZE) {
            fileStorageService.deleteObject(objectKey);
            throw new BusinessException(GlobalMessage.BAD_REQUEST_FILE_TO_LARGE, HttpStatus.BAD_REQUEST);
        }
        if (!CONTENT_TYPE_EXTENSIONS.containsKey(stat.getContentType())) {
            fileStorageService.deleteObject(objectKey);
            throw new BusinessException(GlobalMessage.BAD_REQUEST, HttpStatus.BAD_REQUEST);
        }

        String previous = user.getProfileImage();
        user.setProfileImage(objectKey);
        UserEntity saved = userRepository.save(user);
        deleteIfUploaded(user, previous, objectKey);

        return toDto(saved);
    }

    @Transactional
    public UserDetailDTO deleteProfileImage(UUID userId) {
        UserEntity user = findActiveUserOrThrow(userId);

        String previous = user.getProfileImage();
        user.setProfileImage(null);
        UserEntity saved = userRepository.save(user);
        deleteIfUploaded(user, previous, null);

        return toDto(saved);
    }

    private UserDetailDTO toDto(UserEntity user) {
        return UserDetailDTO.from(user).withProfileImageUrl(profileImageUrlResolver.resolve(user.getProfileImage()));
    }

    /** Only objects this feature wrote are removed, never an external url or a key of anything else. */
    private void deleteIfUploaded(UserEntity user, String previous, String keep) {
        if (previous == null || previous.equals(keep) || !previous.startsWith(prefixOf(user))) {
            return;
        }
        fileStorageService.deleteObject(previous);
    }

    private boolean hasAllowedExtension(String objectKey) {
        String lower = objectKey.toLowerCase();
        return CONTENT_TYPE_EXTENSIONS.values().stream().anyMatch(lower::endsWith);
    }

    private String prefixOf(UserEntity user) {
        return user.getId() + "/" + ObjectStorageTypeGroup.PROFILE_PICTURE + "/";
    }

    private UserEntity findActiveUserOrThrow(UUID userId) {
        return userRepository.findActiveById(userId)
                .orElseThrow(() -> new BusinessException(GlobalMessage.USER_NOT_FOUND, HttpStatus.NOT_FOUND));
    }
}
