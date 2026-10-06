package com.rizkysiregar.feedzmore.model.response;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class CommentResponse {
    String username;
    String imageUrl;
    String commentContent;
}
