package com.rizkysiregar.feedzmore.service;

import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ResponseEntity;

public interface ResourceService {
    ResponseEntity<InputStreamResource> getFile(String fileName, String bucketName);
}
