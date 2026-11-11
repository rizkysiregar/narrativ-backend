package com.rizkysiregar.feedzmore.service;

import com.rizkysiregar.feedzmore.constant.GlobalMessage;
import com.rizkysiregar.feedzmore.constant.ObjectStorageTypeGroup;
import com.rizkysiregar.feedzmore.exception.BusinessException;
import com.rizkysiregar.feedzmore.model.dto.ObjectStat;
import com.rizkysiregar.feedzmore.model.dto.UserDetailDTO;
import com.rizkysiregar.feedzmore.model.entity.UserEntity;
import com.rizkysiregar.feedzmore.model.request.CreateProfileImageUploadUrlRequest;
import com.rizkysiregar.feedzmore.model.request.SetProfileImageRequest;
import com.rizkysiregar.feedzmore.model.response.ProfileImageUploadUrlResponse;
import com.rizkysiregar.feedzmore.repository.UserRepository;
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
    private static final int STAT_ATTEMPTS = 3;
    private static final long STAT_RETRY_DELAY_MS = 150;
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
            log.error("PROFILE.IMAGE.PRESIGN.UPLOAD.FAILED: user={} reason={}", userId, e.getMessage());
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

        // a missing object is rejected; size and type are checked whenever storage can tell them
        ObjectStat stat = statWithRetry(objectKey);
        if (stat != null) {
            if (stat.getSize() > MAX_PROFILE_IMAGE_SIZE) {
                fileStorageService.deleteObject(objectKey);
                throw new BusinessException(GlobalMessage.BAD_REQUEST_FILE_TO_LARGE, HttpStatus.BAD_REQUEST);
            }
            if (!CONTENT_TYPE_EXTENSIONS.containsKey(stat.getContentType())) {
                fileStorageService.deleteObject(objectKey);
                throw new BusinessException(GlobalMessage.BAD_REQUEST, HttpStatus.BAD_REQUEST);
            }
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

    /**
     * The stat (a HEAD) sometimes fails transiently on the way to object storage, so it is retried a few times.
     * A missing object (404) is final and rethrown. When every attempt fails otherwise, null is returned and the
     * caller skips the size and type checks, like post media does: the extension was fixed by the backend when it
     * signed the upload and the content type is part of that signature.
     */
    private ObjectStat statWithRetry(String objectKey) {
        for (int attempt = 1; attempt <= STAT_ATTEMPTS; attempt++) {
            try {
                return fileStorageService.statObject(objectKey);
            } catch (BusinessException e) {
                if (HttpStatus.NOT_FOUND.equals(e.getHttpStatus())) {
                    throw e;
                }
                log.warn("PROFILE.STAT.FAILED: key={} attempt={}/{} reason={}", objectKey, attempt, STAT_ATTEMPTS, e.getMessage());
            }
            if (attempt < STAT_ATTEMPTS) {
                pause();
            }
        }
        log.warn("PROFILE.STAT.SKIPPED: key={}", objectKey);
        return null;
    }

    private void pause() {
        try {
            Thread.sleep(STAT_RETRY_DELAY_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
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
