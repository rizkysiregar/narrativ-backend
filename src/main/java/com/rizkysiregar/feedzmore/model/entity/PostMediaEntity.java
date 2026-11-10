package com.rizkysiregar.feedzmore.model.entity;

import com.rizkysiregar.feedzmore.model.enums.MediaType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One image or video of a post. {@code position} is the order in the post (0-based); the keys are object
 * storage keys, never URLs.
 */
@Entity
@Table(name = "post_media")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PostMediaEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "post_id", nullable = false)
    private UUID postId;

    @Column(name = "position", nullable = false)
    private int position;

    @Column(name = "media_key", nullable = false)
    private String mediaKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "media_type", nullable = false, length = 20)
    private MediaType mediaType;

    @Column(name = "thumbnail_key")
    private String thumbnailKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
