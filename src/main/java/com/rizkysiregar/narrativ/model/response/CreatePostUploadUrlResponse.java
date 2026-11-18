package com.rizkysiregar.narrativ.model.response;

import com.rizkysiregar.narrativ.model.dto.PresignedUploadForm;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreatePostUploadUrlResponse {
    // presigned PUT: kept for app versions that upload this way; storage cannot cap its size
    private String presignedUrl;
    // presigned POST: storage refuses a file above the limit of its media type. Preferred
    private PresignedUploadForm uploadForm;
    private String objectKey;
    private Long expiresInSeconds;
}
