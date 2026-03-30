package com.rizkysiregar.feedzmore.service.impl;

import com.rizkysiregar.feedzmore.constant.GlobalMessage;
import com.rizkysiregar.feedzmore.exception.BusinessException;
import com.rizkysiregar.feedzmore.model.dto.ImageObject;
import com.rizkysiregar.feedzmore.service.FileStorageService;
import io.minio.*;
import io.minio.http.Method;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.awt.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.TimeUnit;

/**
 * Author: Habib Rizky Almajid Siregar
 * Date: 17/10/25
 * Description: Programmer
 */

@Service
@Slf4j
public class FileStorageServiceImpl implements FileStorageService {

    private final MinioClient minioClient;
    private final String bucketName;


    public FileStorageServiceImpl(MinioClient minioClient,
                                  @Value("${minio.bucket}") String bucketName
    ) {
        this.minioClient = minioClient;
        this.bucketName = bucketName;
    }

    @Override
    public String uploadFile(MultipartFile file, String userId, String featureName) throws Exception {
        log.info(":: UPLOAD.MINIO: {}, {}", file.getOriginalFilename(), userId);
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String originalName = file.getOriginalFilename();
        String extension = "";


        if (originalName != null && originalName.contains(".")) {
            extension = originalName.substring(originalName.lastIndexOf("."));
        }

        // format it: userId/featureName/fileName
        String objectName = String.format("%s/%s/%s_%s%s", userId, featureName, timestamp, "upload", extension);

        // ensure bucket exist
        boolean found = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build());
        if (!found) {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
        }

        // upload
        minioClient.putObject(
                PutObjectArgs.builder()
                        .bucket(bucketName)
                        .object(objectName)
                        .stream(file.getInputStream(), file.getSize(), -1)
                        .contentType(file.getContentType())
                        .build()
        );

        return objectName;
    }

    @Override
    public String getPresignUrl(String objectName) throws Exception {
        return minioClient.getPresignedObjectUrl(
                GetPresignedObjectUrlArgs.builder()
                        .bucket(bucketName)
                        .object(objectName)
                        .method(Method.GET)
                        .expiry(1, TimeUnit.HOURS)
                        .build()
        );
    }

    @Override
    public ImageObject getObject(String objectName, String bucketName) {
        try {
            // get metadata first
            StatObjectResponse stat = minioClient.statObject(
                    StatObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .build()
            );

            // now stream the file
            GetObjectResponse response = minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .build()
            );

            MediaType contentType = MediaType.parseMediaType(stat.contentType());
            long size = stat.size();  // CORRECT SIZE

            return ImageObject.builder()
                    .stream(response)
                    .contentType(contentType)
                    .size(size)
                    .build();

        } catch (Exception e) {
            log.error(":: get object failed, objectName: {}", objectName);
            throw new BusinessException(GlobalMessage.INTERNAL_ERROR, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}
