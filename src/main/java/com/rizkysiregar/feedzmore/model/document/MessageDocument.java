package com.rizkysiregar.feedzmore.model.document;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.time.Instant;
import java.util.UUID;

/**
 * Mirror of the Message entity for full-text search.
 * Indexed asynchronously after DB insert — not the source of truth.
 */
@Document(indexName = "messages")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MessageDocument {
    @Id
    private String id; // UUID as string
    @Field(type = FieldType.Keyword)
    private String conversationId;
    @Field(type = FieldType.Keyword)
    private String senderId;
    // Analyzed for full-text search
    @Field(type = FieldType.Text, analyzer = "standard")
    private String content;
    @Field(type = FieldType.Date)
    private Instant createdAt;
}
