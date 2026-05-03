package com.rizkysiregar.feedzmore.repository;

import com.rizkysiregar.feedzmore.model.entity.chat.Message;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface MessageRepository extends JpaRepository<Message, UUID> {

    /**
     * Cursor-based pagination: messages before a given message ID.
     * More efficient than offset pagination for chat history.
     */
    @Query("""
            SELECT m FROM Message m
            WHERE m.conversationId = :conversationId
              AND m.isDeleted = false
              AND (:beforeId IS NULL OR m.createdAt < (
                  SELECT m2.createdAt FROM Message m2 WHERE m2.id = :beforeId
              ))
            ORDER BY m.createdAt DESC
            """)
    Slice<Message> findByConversationIdBefore(
            @Param("conversationId") UUID conversationId,
            @Param("beforeId") UUID beforeId,
            Pageable pageable
    );

    /**
     * Count unread messages for a participant.
     */
    @Query("""
            SELECT COUNT(m) FROM Message m
            WHERE m.conversationId = :conversationId
              AND m.senderId != :userId
              AND m.isDeleted = false
              AND (:lastReadId IS NULL OR m.createdAt > (
                  SELECT m2.createdAt FROM Message m2 WHERE m2.id = :lastReadId
              ))
            """)
    long countUnread(
            @Param("conversationId") UUID conversationId,
            @Param("userId") UUID userId,
            @Param("lastReadId") UUID lastReadId
    );

}
