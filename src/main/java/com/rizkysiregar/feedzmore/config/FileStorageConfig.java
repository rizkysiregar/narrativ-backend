package com.rizkysiregar.feedzmore.config;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Component
@Slf4j
public class FileStorageConfig {
    @Value("${image.upload.dir}")
    private String imageDir;

    @Getter
    private Path fileStorageLocation;

    @PostConstruct
    public void init() {
        log.info("::. Attempt to load fileStorageLocation ... ... .. ");
        try {
            fileStorageLocation = Paths.get(imageDir).toAbsolutePath().normalize();

            // create location if doesn't exist
            Files.createDirectories(fileStorageLocation);
            log.info("File storage directory initialized: " + fileStorageLocation);
        } catch (Exception e) {
            throw new RuntimeException("Could not initialize file storage location", e);
        }
    }

}
