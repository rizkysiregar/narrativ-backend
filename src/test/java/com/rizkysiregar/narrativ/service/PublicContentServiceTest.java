package com.rizkysiregar.narrativ.service;

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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PublicContentServiceTest {

    @Mock PostPaginationRepository postRepository;
    @Mock PostMediaRepository postMediaRepository;
    @Mock LikeRepository likeRepository;
    @Mock CommentRepository commentRepository;
    @Mock UserRepository userRepository;
    @Mock FollowRepository followRepository;
    @Mock FileStorageService fileStorageService;
    @InjectMocks PublicContentService service;

    private final UUID postId = UUID.randomUUID();

    private UserEntity author(String profileImage) {
        UserEntity user = new UserEntity();
        user.setId(UUID.randomUUID());
        user.setUsername("ani");
        user.setFirstName("Ani");
        user.setLastName("Wijaya");
        user.setProfileImage(profileImage);
        return user;
    }

    private PostEntity post(ModerationStatus status) {
        PostEntity post = new PostEntity();
        post.setId(postId);
        post.setUser(author("ani/PROFILE_PICTURE/a.jpg"));
        post.setCaption("Hello");
        post.setCreatedAt(LocalDateTime.of(2026, 11, 20, 10, 0));
        post.setModerationStatus(status);
        return post;
    }

    private PostMediaEntity media(MediaType type, String key, String thumbnail) {
        return PostMediaEntity.builder().id(UUID.randomUUID()).postId(postId).mediaType(type).mediaKey(key)
                .thumbnailKey(thumbnail).build();
    }

    private static void assertNotFound(Runnable call) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void getPost_StableImagePathsAndNothingAboutAViewer() {
        PostMediaEntity photo = media(MediaType.IMAGE, "k/photo.jpg", null);
        PostMediaEntity video = media(MediaType.VIDEO, "k/clip.mp4", "k/clip_thumb.jpg");
        PostMediaEntity newVideo = media(MediaType.VIDEO, "k/new.mp4", null);
        when(postRepository.findVisibleById(postId, null)).thenReturn(Optional.of(post(ModerationStatus.ACTIVE)));
        when(likeRepository.countByPostIds(List.of(postId))).thenReturn(List.of(new PostCountDTO(postId, 3)));
        when(commentRepository.countByPostIds(List.of(postId), null)).thenReturn(List.of(new PostCountDTO(postId, 2)));
        when(postMediaRepository.findByPostIdOrderByPositionAsc(postId)).thenReturn(List.of(photo, video, newVideo));

        PublicPostResponse response = service.getPost(postId);

        assertThat(response.caption()).isEqualTo("Hello");
        assertThat(response.likeCount()).isEqualTo(3);
        assertThat(response.commentCount()).isEqualTo(2);
        assertThat(response.author()).isEqualTo(new PublicPostResponse.PublicAuthor("ani", "Ani Wijaya", "/public/users/ani/avatar"));
        assertThat(response.media()).extracting(PublicPostResponse.PublicMedia::imagePath)
                .containsExactly("/public/media/" + photo.getId(), "/public/media/" + video.getId(), null);
    }

    @Test
    void getPost_TakenDownOrInvisible_IsNotFound() {
        when(postRepository.findVisibleById(postId, null)).thenReturn(Optional.of(post(ModerationStatus.TAKEN_DOWN)));
        assertNotFound(() -> service.getPost(postId));

        UUID gone = UUID.randomUUID();
        when(postRepository.findVisibleById(gone, null)).thenReturn(Optional.empty());
        assertNotFound(() -> service.getPost(gone));
    }

    @Test
    void mediaImageUrl_PhotoOrVideoThumbnail_OfAPublicPost() throws Exception {
        PostMediaEntity photo = media(MediaType.IMAGE, "k/photo.jpg", null);
        PostMediaEntity video = media(MediaType.VIDEO, "k/clip.mp4", "k/clip_thumb.jpg");
        when(postMediaRepository.findById(photo.getId())).thenReturn(Optional.of(photo));
        when(postMediaRepository.findById(video.getId())).thenReturn(Optional.of(video));
        when(postRepository.findVisibleById(postId, null)).thenReturn(Optional.of(post(ModerationStatus.ACTIVE)));
        when(fileStorageService.getPresignUrl("k/photo.jpg")).thenReturn("https://s/photo?sig");
        when(fileStorageService.getPresignUrl("k/clip_thumb.jpg")).thenReturn("https://s/thumb?sig");

        assertThat(service.mediaImageUrl(photo.getId())).isEqualTo("https://s/photo?sig");
        assertThat(service.mediaImageUrl(video.getId())).isEqualTo("https://s/thumb?sig");
    }

    @Test
    void mediaImageUrl_HiddenPostOrNoThumbnailYet_IsNotFound() {
        PostMediaEntity photo = media(MediaType.IMAGE, "k/photo.jpg", null);
        PostMediaEntity newVideo = media(MediaType.VIDEO, "k/new.mp4", null);
        when(postMediaRepository.findById(photo.getId())).thenReturn(Optional.of(photo));
        when(postMediaRepository.findById(newVideo.getId())).thenReturn(Optional.of(newVideo));
        when(postRepository.findVisibleById(postId, null))
                .thenReturn(Optional.of(post(ModerationStatus.TAKEN_DOWN)))
                .thenReturn(Optional.of(post(ModerationStatus.ACTIVE)));

        assertNotFound(() -> service.mediaImageUrl(photo.getId()));
        assertNotFound(() -> service.mediaImageUrl(newVideo.getId()));
    }

    @Test
    void getProfile_CountsAndStableAvatar() {
        UserEntity user = author("ani/PROFILE_PICTURE/a.jpg");
        when(userRepository.findActiveByUsername("Ani")).thenReturn(Optional.of(user));
        when(followRepository.countByFollowedId(user.getId())).thenReturn(10L);
        when(followRepository.countByFollowerId(user.getId())).thenReturn(4L);

        assertThat(service.getProfile("Ani"))
                .isEqualTo(new PublicProfileResponse("ani", "Ani Wijaya", "/public/users/ani/avatar", 10, 4));
    }

    @Test
    void avatar_GooglePictureAsIs_MissingIsNotFound() {
        when(userRepository.findActiveByUsername("g")).thenReturn(Optional.of(author("https://lh3.googleusercontent.com/a")));
        when(userRepository.findActiveByUsername("none")).thenReturn(Optional.of(author(null)));
        when(userRepository.findActiveByUsername("ghost")).thenReturn(Optional.empty());

        assertThat(service.avatarUrl("g")).isEqualTo("https://lh3.googleusercontent.com/a");
        assertNotFound(() -> service.avatarUrl("none"));
        assertNotFound(() -> service.avatarUrl("ghost"));
    }
}
