package com.rizkysiregar.narrativ.model.response;

import com.rizkysiregar.narrativ.model.dto.PresignedUploadForm;

/**
 * Where to upload a new profile picture. {@code uploadForm} (presigned POST, refused by storage above 2 MB) is
 * preferred; {@code presignedUrl} (PUT, no size cap) stays for app versions that upload this way.
 */
public record ProfileImageUploadUrlResponse(String presignedUrl, String objectKey, long expiresInSeconds,
                                            PresignedUploadForm uploadForm) {
}
