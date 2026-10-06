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
            SELECT p FROM PostEntity p JOIN FETCH p.user
            WHERE p.isDeleted = false
            ORDER BY p.createdAt DESC, p.id DESC
            """, countQuery = """
            SELECT COUNT(p) FROM PostEntity p WHERE p.isDeleted = false
            """)
    Page<PostEntity> findAllActivePosts(Pageable pageable);

    @Query(value = """
            SELECT p FROM PostEntity p JOIN FETCH p.user
            WHERE p.user.id = :userId AND p.isDeleted = false
            ORDER BY p.createdAt DESC, p.id DESC
            """, countQuery = """
            SELECT COUNT(p) FROM PostEntity p
            WHERE p.user.id = :userId AND p.isDeleted = false
            """)
    Page<PostEntity> findAllActivePostsByUserId(Pageable pageable, @Param("userId") UUID userId);
}
