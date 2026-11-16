package com.rizkysiregar.narrativ.model.response;

public record ProfileImageUploadUrlResponse(String presignedUrl, String objectKey, long expiresInSeconds) {
}
