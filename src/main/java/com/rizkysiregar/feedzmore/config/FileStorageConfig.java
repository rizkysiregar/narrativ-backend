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
    @Value("${image.upload.dir.image-post}")
    private String postImageDir;
    @Value("${image.upload.dir.image-profile}")
    private String profileImageDir;
    @Value("${video.dir}")
    private String videoDir;
    @Getter
    private Path postImageStorageLocation;

    @Getter
    private Path profileImageStorageLocation;

    @Getter
    private Path videoStorageLocation;

    @PostConstruct
    public void init() {
        log.info("::. Attempt to load fileStorageLocation ... ... .. ");
        try {
            postImageStorageLocation = Paths.get(postImageDir).toAbsolutePath().normalize();
            profileImageStorageLocation = Paths.get(profileImageDir).toAbsolutePath().normalize();
            videoStorageLocation = Paths.get(videoDir).toAbsolutePath().normalize();

            // create location if doesn't exist
            Files.createDirectories(postImageStorageLocation);
            Files.createDirectories(profileImageStorageLocation);
            Files.createDirectories(videoStorageLocation);
            log.info("::. Post Image storage directory initialized to: " + postImageStorageLocation);
            log.info("::. Profile Image storage directory initialized to: " + profileImageStorageLocation);
            log.info("::. Video storage directory initialized to: " + videoStorageLocation);
        } catch (Exception e) {
            throw new RuntimeException("Could not initialize file storage location", e);
        }
    }

}
