package com.rizkysiregar.feedzmore.model.response;

public record ProfileImageUploadUrlResponse(String presignedUrl, String objectKey, long expiresInSeconds) {
}
