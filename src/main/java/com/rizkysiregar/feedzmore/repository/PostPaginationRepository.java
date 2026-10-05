package com.rizkysiregar.feedzmore.repository;

import com.rizkysiregar.feedzmore.model.dto.PostDTO;
import com.rizkysiregar.feedzmore.model.entity.PostEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface PostPaginationRepository extends PagingAndSortingRepository<PostEntity, UUID> {

    // Find all post with like and commend count also find if related user already like the post or not
    @Query("""
            SELECT new com.rizkysiregar.feedzmore.model.dto.PostDTO(
                                                             p.id,
                                                             p.user.username,
                                                             p.caption,
                                                             p.imageUrl,
                                                             p.createdAt,
                                                             COUNT(DISTINCT l.id),
                                                             COUNT(DISTINCT c.id),
                                                             CASE
                                                                 WHEN COUNT(DISTINCT l2.id) > 0 THEN true
                                                                 ELSE false
                                                             END
                                                         )
                                                         FROM PostEntity p
                                                         LEFT JOIN LikeEntity l ON p.id = l.post.id
                                                         LEFT JOIN CommentEntity c ON p.id = c.post.id
                                                         LEFT JOIN LikeEntity l2 ON p.id = l2.post.id AND l2.user.id = :userId
                                                         WHERE p.isDeleted = false
                                                         GROUP BY p.id, p.user.username, p.caption, p.imageUrl, p.createdAt
                                                         ORDER BY p.createdAt DESC
            """)
    Page<PostDTO> findAllPost(Pageable pageable, @Param("userId") UUID userId);

    // same as findAllPost but filtering only for user post.userId
    @Query("""
            SELECT new com.rizkysiregar.feedzmore.model.dto.PostDTO(
                                                             p.id,
                                                             p.user.username,
                                                             p.caption,
                                                             p.imageUrl,
                                                             p.createdAt,
                                                             COUNT(DISTINCT l.id),
                                                             COUNT(DISTINCT c.id),
                                                             CASE
                                                                 WHEN COUNT(DISTINCT l2.id) > 0 THEN true
                                                                 ELSE false
                                                             END
                                                         )
                                                         FROM PostEntity p
                                                         LEFT JOIN LikeEntity l ON p.id = l.post.id
                                                         LEFT JOIN CommentEntity c ON p.id = c.post.id
                                                         LEFT JOIN LikeEntity l2 ON p.id = l2.post.id AND l2.user.id = :userId
                                                         WHERE p.user.id = :userId AND p.isDeleted = false
                                                         GROUP BY p.id, p.user.username, p.caption, p.imageUrl, p.createdAt
                                                         ORDER BY p.createdAt DESC
            """)
    Page<PostDTO> findAllPostByUserId(Pageable pageable, @Param("userId") UUID userId);
}
