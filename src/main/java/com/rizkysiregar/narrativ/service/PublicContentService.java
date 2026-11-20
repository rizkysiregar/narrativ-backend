package com.rizkysiregar.narrativ.service;

import com.rizkysiregar.narrativ.constant.GlobalMessage;
import com.rizkysiregar.narrativ.exception.BusinessException;
import com.rizkysiregar.narrativ.model.dto.PostCountDTO;
import com.rizkysiregar.narrativ.model.entity.PostEntity;
import com.rizkysiregar.narrativ.model.entity.PostMediaEntity;
import com.rizkysiregar.narrativ.model.entity.UserEntity;
import com.rizkysiregar.narrativ.model.enums.MediaType;
import com.rizkysiregar.narrativ.model.enums.ModerationStatus;
import com.rizkysiregar.narrativ.model.response.PublicPostResponse;
import com.rizkysiregar.narrativ.model.response.PublicProfileResponse;
import com.rizkysiregar.narrativ.repository.CommentRepository;
import com.rizkysiregar.narrativ.repository.FollowRepository;
import com.rizkysiregar.narrativ.repository.LikeRepository;
import com.rizkysiregar.narrativ.repository.PostMediaRepository;
import com.rizkysiregar.narrativ.repository.PostPaginationRepository;
import com.rizkysiregar.narrativ.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * What a shared link shows to anyone, signed in or not: one post or one profile, and their images. Only what is
 * visible to everybody anyway: a deleted or taken down post, and a deleted or banned author, answer 404 like a post
 * that never existed. There is no listing or search here, only lookups by a link.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PublicContentService {

    public static final String MEDIA_PATH = "/public/media/";
    public static final String AVATAR_PATH = "/public/users/%s/avatar";

    private final PostPaginationRepository postRepository;
    private final PostMediaRepository postMediaRepository;
    private final LikeRepository likeRepository;
    private final CommentRepository commentRepository;
    private final UserRepository userRepository;
    private final FollowRepository followRepository;
    private final FileStorageService fileStorageService;

    public PublicPostResponse getPost(UUID postId) {
        PostEntity post = visiblePost(postId);
        List<UUID> ids = List.of(postId);
        // no viewer: the block filters of these queries match nobody
        long likes = likeRepository.countByPostIds(ids).stream().mapToLong(PostCountDTO::count).sum();
        long comments = commentRepository.countByPostIds(ids, null).stream().mapToLong(PostCountDTO::count).sum();
        List<PublicPostResponse.PublicMedia> media = postMediaRepository.findByPostIdOrderByPositionAsc(postId).stream()
                .map(m -> new PublicPostResponse.PublicMedia(m.getId(), m.getMediaType(), imagePath(m)))
                .toList();
        UserEntity author = post.getUser();
        return new PublicPostResponse(
                post.getId(),
                post.getCaption(),
                post.getCreatedAt() == null ? null : post.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant(),
                likes,
                comments,
                new PublicPostResponse.PublicAuthor(author.getUsername(), fullName(author), avatarPath(author)),
                media
        );
    }

    public PublicProfileResponse getProfile(String username) {
        UserEntity user = activeUser(username);
        return new PublicProfileResponse(
                user.getUsername(),
                fullName(user),
                avatarPath(user),
                followRepository.countByFollowedId(user.getId()),
                followRepository.countByFollowerId(user.getId())
        );
    }

    /** A fresh presigned URL of a media's image (a photo, or a video's thumbnail), when its post is public. */
    public String mediaImageUrl(UUID mediaId) {
        PostMediaEntity media = postMediaRepository.findById(mediaId)
                .orElseThrow(PublicContentService::notFound);
        visiblePost(media.getPostId());
        String key = media.getMediaType() == MediaType.VIDEO ? media.getThumbnailKey() : media.getMediaKey();
        if (key == null) {
            throw notFound();
        }
        return presign(key);
    }

    public String avatarUrl(String username) {
        UserEntity user = activeUser(username);
        if (user.getProfileImage() == null || user.getProfileImage().isBlank()) {
            throw notFound();
        }
        // an avatar may be a Google picture URL (accounts created with Google Sign-In)
        return user.getProfileImage().startsWith("http") ? user.getProfileImage() : presign(user.getProfileImage());
    }

    private PostEntity visiblePost(UUID postId) {
        return postRepository.findVisibleById(postId, null)
                .filter(post -> post.getModerationStatus() != ModerationStatus.TAKEN_DOWN)
                .orElseThrow(PublicContentService::notFound);
    }

    private UserEntity activeUser(String username) {
        return Optional.ofNullable(username)
                .flatMap(userRepository::findActiveByUsername)
                .orElseThrow(PublicContentService::notFound);
    }

    private static String imagePath(PostMediaEntity media) {
        boolean hasImage = media.getMediaType() != MediaType.VIDEO || media.getThumbnailKey() != null;
        return hasImage ? MEDIA_PATH + media.getId() : null;
    }

    private static String avatarPath(UserEntity user) {
        return user.getProfileImage() == null || user.getProfileImage().isBlank()
                ? null : AVATAR_PATH.formatted(user.getUsername());
    }

    private static String fullName(UserEntity user) {
        return (user.getFirstName() + " " + user.getLastName()).trim();
    }

    private String presign(String key) {
        try {
            return fileStorageService.getPresignUrl(key);
        } catch (Exception e) {
            log.error("PUBLIC.PRESIGN.FAILED objectKey={} reason={}", key, e.getMessage());
            throw new BusinessException(GlobalMessage.INTERNAL_ERROR, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private static BusinessException notFound() {
        return new BusinessException(GlobalMessage.NOT_FOUND, HttpStatus.NOT_FOUND);
    }
}
