package com.rizkysiregar.feedzmore.service.impl;

import com.rizkysiregar.feedzmore.model.dto.ImageObject;
import com.rizkysiregar.feedzmore.service.FileStorageService;
import com.rizkysiregar.feedzmore.service.ResourceService;
import com.rizkysiregar.feedzmore.util.Helper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
@Slf4j
@RequiredArgsConstructor
public class ResourceServiceImpl implements ResourceService {

    private final FileStorageService fileStorageService;

    @Override
    public ResponseEntity<InputStreamResource> getFile(String fileName, String bucketName) {

        ImageObject object = fileStorageService.getObject(fileName, bucketName);
        log.info("object: {}", object);

        // contentType from MinIO (MediaType)
        MediaType mediaType = object.getContentType();
        String contentType = null;

        if (mediaType != null) {
            contentType = mediaType.toString();
        }

        // Check invalid content type
        if (contentType == null ||
                contentType.trim().isEmpty() ||
                contentType.equalsIgnoreCase("image/*") ||
                contentType.endsWith("/*")) {

            log.info("Invalid contentType '{}', detecting based on filename", contentType);
            contentType = Helper.detectContentTypeFromFilename(fileName);
        }

        // Final fallback
        if (contentType == null) {
            log.info("Content type is null, fallback to APPLICATION_OCTET_STREAM");
            contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE;
        }

        log.info("## final contentType: {}", contentType);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .contentLength(object.getSize())
                .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS))
                .body(new InputStreamResource(object.getStream()));
    }



    /*
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
            Path filePath = fileStorageConfig.getPostImageStorageLocation().resolve(fileName).normalize();
            Resource resource = new UrlResource(filePath.toUri());

            if (resource.exists() && resource.isReadable()) {
//                log.info("::. file exist and readable, return success");
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

     */
}
