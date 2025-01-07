package com.rizkysiregar.feedzmore.service;

import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;

public interface ResourceService {
    ResponseEntity<Resource> getResourceImage(String fileName);
}
