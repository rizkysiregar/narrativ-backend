package com.rizkysiregar.feedzmore.repository;

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

    @Query(value = """
            SELECT p FROM PostEntity p JOIN FETCH p.user author
            WHERE p.isDeleted = false AND author.isDeleted = false AND author.status = com.rizkysiregar.feedzmore.model.enums.UserStatus.ACTIVE
            ORDER BY p.createdAt DESC, p.id DESC
            """, countQuery = """
            SELECT COUNT(p) FROM PostEntity p JOIN p.user author
            WHERE p.isDeleted = false AND author.isDeleted = false AND author.status = com.rizkysiregar.feedzmore.model.enums.UserStatus.ACTIVE
            """)
    Page<PostEntity> findAllActivePosts(Pageable pageable);

    @Query(value = """
            SELECT p FROM PostEntity p JOIN FETCH p.user author
            WHERE p.isDeleted = false AND author.isDeleted = false AND author.status = com.rizkysiregar.feedzmore.model.enums.UserStatus.ACTIVE
              AND EXISTS (
                  SELECT f.id FROM FollowEntity f
                  WHERE f.follower.id = :viewerId AND f.followed.id = author.id
              )
            ORDER BY p.createdAt DESC, p.id DESC
            """, countQuery = """
            SELECT COUNT(p) FROM PostEntity p JOIN p.user author
            WHERE p.isDeleted = false AND author.isDeleted = false AND author.status = com.rizkysiregar.feedzmore.model.enums.UserStatus.ACTIVE
              AND EXISTS (
                  SELECT f.id FROM FollowEntity f
                  WHERE f.follower.id = :viewerId AND f.followed.id = author.id
              )
            """)
    Page<PostEntity> findFollowingPosts(Pageable pageable, @Param("viewerId") UUID viewerId);

    @Query(value = """
            SELECT p FROM PostEntity p JOIN FETCH p.user author
            WHERE author.id = :userId AND author.isDeleted = false AND author.status = com.rizkysiregar.feedzmore.model.enums.UserStatus.ACTIVE AND p.isDeleted = false
            ORDER BY p.createdAt DESC, p.id DESC
            """, countQuery = """
            SELECT COUNT(p) FROM PostEntity p JOIN p.user author
            WHERE author.id = :userId AND author.isDeleted = false AND author.status = com.rizkysiregar.feedzmore.model.enums.UserStatus.ACTIVE AND p.isDeleted = false
            """)
    Page<PostEntity> findAllActivePostsByUserId(Pageable pageable, @Param("userId") UUID userId);
}
