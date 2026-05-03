package com.rizkysiregar.feedzmore.repository;

import com.rizkysiregar.feedzmore.model.entity.chat.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConversationRepository extends JpaRepository<Conversation, UUID> {

    /**
     * All conversations a user participates in, ordered by latest activity.
     */
    @Query("""
            SELECT c FROM Conversation c
            JOIN c.participants p
            WHERE p.userId = :userId
              AND p.leftAt IS NULL
              AND c.deletedAt IS NULL
            ORDER BY c.updatedAt DESC
            """)
    List<Conversation> findActiveByUserId(@Param("userId") UUID userId);

    /**
     * Check if a direct conversation already exist between two users.
     *
     */

    @Query("""
            SELECT C FROM Conversation c
            JOIN c.participants p1 ON p1.userId = :userId1
            JOIN c.participants p2 ON p2.userId = :userId2
            WHERE c.type = 'DIRECT'
            AND p1.leftAt IS NULL
            AND p2.leftAt IS NULL
            """)
    Optional<Conversation> findDirectConversation(
            @Param("userId1") UUID userId1,
            @Param("userId2") UUID userId2
    );
}



