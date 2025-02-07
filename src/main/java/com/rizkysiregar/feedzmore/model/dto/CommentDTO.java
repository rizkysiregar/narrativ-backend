package com.rizkysiregar.feedzmore.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class CommentDTO {
    String username;
    String imageUrl;
    String commentContent;
}
