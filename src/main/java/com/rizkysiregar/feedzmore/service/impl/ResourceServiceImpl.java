package com.rizkysiregar.feedzmore.service.impl;

import com.rizkysiregar.feedzmore.config.FileStorageConfig;
import com.rizkysiregar.feedzmore.service.ResourceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.io.File;
import java.net.MalformedURLException;
import java.nio.file.Path;

@Service
@Slf4j
public class ResourceServiceImpl implements ResourceService {

    @Value("${video.dir}")
    private String videoDir;
    private final FileStorageConfig fileStorageConfig;

    public ResourceServiceImpl(FileStorageConfig fileStorageConfig) {
        this.fileStorageConfig = fileStorageConfig;
    }


    @Override
    public ResponseEntity<Resource> getResourceImage(String fileName) {
        log.info("::. attempt to get image | " + fileName);
        try {
            Path filePath = fileStorageConfig.getImageStorageLocation().resolve(fileName).normalize();
            Resource resource = new UrlResource(filePath.toUri());

            if (resource.exists() && resource.isReadable()) {
                log.info("::. file exist and readable, return success");
                return ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + fileName + "\"")
                        .body(resource);
            } else {
                log.error("::. image not found!");
                return ResponseEntity.notFound().build();
            }
        } catch (MalformedURLException e) {
            log.error("::. MalformedURLException " + e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    // video stream is on hold, focus on the photos and post first
    @Override
    public ResponseEntity<InputStreamResource> streamVideo(String fileName, String rangeHeader) {
        log.info("::. incoming stream video with filename: " + fileName + ", and rangeHeader: " + rangeHeader);

        try {
//            Path videoPath = fileStorageConfig.getVideoStorageLocation().resolve(fileName).normalize();
            File videoFile = new File(videoDir + "/" + fileName);
            if (!videoFile.exists()) {
                log.error("::. Video file not found!");
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }

            long fileLength = videoFile.length();
            long rangeStart = 0;
            long rangeEnd = fileLength - 1;

            if (rangeHeader != null ) {

            }


        } catch (Exception e) {

        }

        return null;
    }
}
