package com.rizkysiregar.feedzmore.service.impl;

import com.rizkysiregar.feedzmore.config.FileStorageConfig;
import com.rizkysiregar.feedzmore.service.ResourceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.net.MalformedURLException;
import java.nio.file.Path;

@Service
@Slf4j
public class ResourceServiceImpl implements ResourceService {

    private final FileStorageConfig fileStorageConfig;

    public ResourceServiceImpl(FileStorageConfig fileStorageConfig) {
        this.fileStorageConfig = fileStorageConfig;
    }


    @Override
    public ResponseEntity<Resource> getResourceImage(String fileName) {
        log.info("::. attempt to get image | " + fileName);
        try {
            Path filePath = fileStorageConfig.getFileStorageLocation().resolve(fileName).normalize();
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
}
