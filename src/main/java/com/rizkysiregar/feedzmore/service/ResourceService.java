package com.rizkysiregar.feedzmore.service;

import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;

public interface ResourceService {
    ResponseEntity<Resource> getResourceImage(String fileName);
    ResponseEntity<InputStreamResource> streamVideo(String fileName, String rangeHeader);
}
