package com.rizkysiregar.feedzmore.controller;

import com.rizkysiregar.feedzmore.service.ResourceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


/*
@RestController
@RequestMapping("/res")
public class ResourceController {

    @Autowired
    private ResourceService resourceService;

    @GetMapping("/photo")
    ResponseEntity<Resource> getImage(@RequestParam String fileName) {
        return resourceService.getResourceImage(fileName);
    }

    @GetMapping("/video")
    ResponseEntity<InputStreamResource> streamVideo(
            @PathVariable String fileName,
            @RequestHeader(value = "Range", required = false) String rengeHeader) {
        return resourceService.streamVideo(fileName, rengeHeader);
    }
}


 */