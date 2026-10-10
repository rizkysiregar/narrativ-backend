package com.rizkysiregar.feedzmore.model.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreatePostUploadUrlResponse {
    private String presignedUrl;
    private String objectKey;
    private Long expiresInSeconds;
}
