package com.rizkysiregar.feedzmore.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "comments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CommentEntity {
    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "post_id", nullable = false)
    private PostEntity post;

    @Column(nullable = false)
    private UUID userId;

    @ManyToOne
    @JoinColumn(name = "parent_id")
    private CommentEntity parent;

    @Column(nullable = false)
    private String content;

    @Column(nullable = false)
    @CreationTimestamp
    private LocalDateTime createdAt;
}
