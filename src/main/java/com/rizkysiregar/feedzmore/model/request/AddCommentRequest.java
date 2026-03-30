package com.rizkysiregar.feedzmore.model.request;

import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Setter
@Getter
public class AddCommentRequest {
    UUID parentId;
    String content;
}
