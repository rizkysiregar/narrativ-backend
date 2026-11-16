package com.rizkysiregar.narrativ.repository;

import com.rizkysiregar.narrativ.model.entity.PostMediaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface PostMediaRepository extends JpaRepository<PostMediaEntity, UUID> {

    List<PostMediaEntity> findByPostIdOrderByPositionAsc(UUID postId);

    List<PostMediaEntity> findByPostIdInOrderByPostIdAscPositionAsc(Collection<UUID> postIds);

    /** Which of the given object keys are still the media of a post. */
    @Query("SELECT m.mediaKey FROM PostMediaEntity m WHERE m.mediaKey IN :keys")
    List<String> findReferencedMediaKeys(@Param("keys") Collection<String> keys);

    /** Which of the given object keys are still the thumbnail of a video. */
    @Query("SELECT m.thumbnailKey FROM PostMediaEntity m WHERE m.thumbnailKey IN :keys")
    List<String> findReferencedThumbnailKeys(@Param("keys") Collection<String> keys);

    /** Object keys of the media and thumbnails of every post of the user that is not deleted yet. */
    @Query("SELECT m.mediaKey FROM PostMediaEntity m WHERE m.postId IN (SELECT p.id FROM PostEntity p WHERE p.user.id = :userId AND p.isDeleted = false)")
    List<String> findMediaKeysOfLivePosts(@Param("userId") UUID userId);

    @Query("SELECT m.thumbnailKey FROM PostMediaEntity m WHERE m.thumbnailKey IS NOT NULL AND m.postId IN (SELECT p.id FROM PostEntity p WHERE p.user.id = :userId AND p.isDeleted = false)")
    List<String> findThumbnailKeysOfLivePosts(@Param("userId") UUID userId);

    @Modifying
    @Query("DELETE FROM PostMediaEntity m WHERE m.postId IN (SELECT p.id FROM PostEntity p WHERE p.user.id = :userId AND p.isDeleted = false)")
    int deleteAllOfLivePosts(@Param("userId") UUID userId);

    @Modifying
    @Query("DELETE FROM PostMediaEntity m WHERE m.postId = :postId")
    int deleteAllByPostId(@Param("postId") UUID postId);
}
