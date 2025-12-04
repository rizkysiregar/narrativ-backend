package com.rizkysiregar.feedzmore.controller;

import com.rizkysiregar.feedzmore.service.ResourceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/res")
@Slf4j
public class ResourceController {

    private final ResourceService resourceService;

    public ResourceController(ResourceService resourceService) {
        this.resourceService = resourceService;
    }

    @GetMapping("/file")
    ResponseEntity<InputStreamResource> getImage(@RequestParam String objectName,
                                                 @RequestParam String bucketName
    ) {
        log.info("Getting image for object name {} and bucket name {}", objectName, bucketName);
        return resourceService.getFile(objectName, bucketName);
    }
}
