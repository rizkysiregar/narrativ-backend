package com.rizkysiregar.feedzmore.model.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity

@Table(name = "app_parameters")
@Getter
@Setter
@NoArgsConstructor
public class ParameterEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "param_key", unique = true, nullable = false)
    private String paramKey;

    @Column(name = "param_value", nullable = false)
    private String paramValue;

    @Column(name = "description", nullable = false)
    private String description;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // Convenience constructor used by tests and simple creations
    public ParameterEntity(String paramKey, String paramValue) {
        this.paramKey = paramKey;
        this.paramValue = paramValue;
        // Default non-null description to satisfy not-null constraint when needed
        this.description = "";
    }
}
