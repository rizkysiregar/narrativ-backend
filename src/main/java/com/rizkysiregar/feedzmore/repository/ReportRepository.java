package com.rizkysiregar.feedzmore.repository;

import com.rizkysiregar.feedzmore.model.entity.ReportEntity;
import com.rizkysiregar.feedzmore.model.enums.ReportAction;
import com.rizkysiregar.feedzmore.model.enums.ReportStatus;
import com.rizkysiregar.feedzmore.model.enums.ReportTargetType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.UUID;

@Repository
public interface ReportRepository extends JpaRepository<ReportEntity, UUID>, JpaSpecificationExecutor<ReportEntity> {

    boolean existsByReporterIdAndTargetTypeAndTargetId(UUID reporterId, ReportTargetType targetType, UUID targetId);

    long countByTargetTypeAndTargetId(ReportTargetType targetType, UUID targetId);

    long countByTargetTypeAndTargetIdAndStatus(ReportTargetType targetType, UUID targetId, ReportStatus status);

    // closes the still open reports of one target because an admin acted on it
    @Modifying
    @Query("""
            UPDATE ReportEntity r
            SET r.status = com.rizkysiregar.feedzmore.model.enums.ReportStatus.RESOLVED,
                r.resolvedBy = :adminId, r.resolvedAt = :now,
                r.resolutionAction = :action, r.resolutionNote = :note
            WHERE r.targetType = :targetType AND r.targetId = :targetId
              AND r.status = com.rizkysiregar.feedzmore.model.enums.ReportStatus.OPEN
            """)
    int closeOpenForTarget(@Param("targetType") ReportTargetType targetType, @Param("targetId") UUID targetId,
                           @Param("adminId") UUID adminId, @Param("now") Instant now,
                           @Param("action") ReportAction action, @Param("note") String note);

    // same for the comments of a post that is about to be hard deleted
    @Modifying
    @Query("""
            UPDATE ReportEntity r
            SET r.status = com.rizkysiregar.feedzmore.model.enums.ReportStatus.RESOLVED,
                r.resolvedBy = :adminId, r.resolvedAt = :now,
                r.resolutionAction = :action, r.resolutionNote = :note
            WHERE r.targetType = com.rizkysiregar.feedzmore.model.enums.ReportTargetType.COMMENT
              AND r.status = com.rizkysiregar.feedzmore.model.enums.ReportStatus.OPEN
              AND r.targetId IN (SELECT c.id FROM CommentEntity c WHERE c.post.id = :postId)
            """)
    int closeOpenForCommentsOfPost(@Param("postId") UUID postId,
                                   @Param("adminId") UUID adminId, @Param("now") Instant now,
                                   @Param("action") ReportAction action, @Param("note") String note);
}
