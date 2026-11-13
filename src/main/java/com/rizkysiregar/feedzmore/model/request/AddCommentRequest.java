package com.rizkysiregar.feedzmore.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Setter
@Getter
public class AddCommentRequest {
    UUID parentId;

    // comments.content is VARCHAR(255): anything longer would fail in the database as a 500
    @NotBlank(message = "Comment cannot be empty")
    @Size(max = 255, message = "A comment can have at most 255 characters")
    String content;
}
