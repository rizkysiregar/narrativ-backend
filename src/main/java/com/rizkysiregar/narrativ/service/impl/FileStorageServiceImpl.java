package com.rizkysiregar.narrativ.service.impl;

import com.rizkysiregar.narrativ.constant.GlobalMessage;
import com.rizkysiregar.narrativ.exception.BusinessException;
import com.rizkysiregar.narrativ.model.dto.ImageObject;
import com.rizkysiregar.narrativ.model.dto.ObjectStat;
import com.rizkysiregar.narrativ.model.dto.StoredObject;
import com.rizkysiregar.narrativ.service.FileStorageService;
import io.minio.*;
import io.minio.errors.ErrorResponseException;
import io.minio.http.Method;
import io.minio.messages.Item;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.awt.*;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Author: Habib Rizky Almajid Siregar
 * Date: 17/10/25
 * Description: Programmer
 */

@Service
@Slf4j
public class FileStorageServiceImpl implements FileStorageService {

    private static final int PRESIGN_UPLOAD_EXPIRY_MINUTES = 15;

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
        log.debug("STORAGE.UPLOAD fileName={} userId={}", file.getOriginalFilename(), userId);
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
    public String getPresignUploadUrl(String objectName, String contentType) throws Exception {
        log.debug("STORAGE.PRESIGN.UPLOAD objectKey={}", objectName);
        return minioClient.getPresignedObjectUrl(
                GetPresignedObjectUrlArgs.builder()
                        .bucket(bucketName)
                        .object(objectName)
                        .method(Method.PUT)
                        .expiry(PRESIGN_UPLOAD_EXPIRY_MINUTES, TimeUnit.MINUTES)
                        .extraHeaders(Map.of("Content-Type", contentType))
                        .build()
        );
    }

    @Override
    public ObjectStat statObject(String objectName) {
        try {
            StatObjectResponse stat = minioClient.statObject(
                    StatObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .build()
            );
            return ObjectStat.builder()
                    .size(stat.size())
                    .contentType(stat.contentType())
                    .build();
        } catch (ErrorResponseException e) {
            String code = e.errorResponse() != null ? e.errorResponse().code() : null;
            if ("NoSuchKey".equals(code) || "NoSuchBucket".equals(code) || "NoSuchObject".equals(code)) {
                log.debug("STORAGE.STAT.NOT.FOUND objectKey={} code={}", objectName, code);
                throw new BusinessException(GlobalMessage.NOT_FOUND, HttpStatus.NOT_FOUND);
            }
            // the ids let a failure be traced in the MinIO log and in Cloudflare
            String amzRequestId = e.response() == null ? null : e.response().header("x-amz-request-id");
            String cfRay = e.response() == null ? null : e.response().header("cf-ray");
            log.error("STORAGE.STAT.FAILED objectKey={} code={} message={} amzRequestId={} cfRay={}",
                    objectName, code, e.getMessage(), amzRequestId, cfRay);
            throw new BusinessException(GlobalMessage.INTERNAL_ERROR, HttpStatus.INTERNAL_SERVER_ERROR);
        } catch (Exception e) {
            log.error("STORAGE.STAT.FAILED objectKey={} reason={}", objectName, e.getMessage());
            throw new BusinessException(GlobalMessage.INTERNAL_ERROR, HttpStatus.INTERNAL_SERVER_ERROR);
        }
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
            log.error("STORAGE.GET.FAILED objectKey={} reason={}", objectName, e.getMessage());
            throw new BusinessException(GlobalMessage.INTERNAL_ERROR, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public String uploadObject(String objectName, InputStream stream, long size, String contentType) throws Exception {
        minioClient.putObject(
                PutObjectArgs.builder()
                        .bucket(bucketName)
                        .object(objectName)
                        .stream(stream, size, -1)
                        .contentType(contentType)
                        .build()
        );
        return objectName;
    }

    @Override
    public Stream<StoredObject> listObjects(String prefix) {
        Iterable<Result<Item>> results = minioClient.listObjects(
                ListObjectsArgs.builder().bucket(bucketName).prefix(prefix).recursive(true).build());
        return StreamSupport.stream(results.spliterator(), false)
                .map(result -> {
                    try {
                        return result.get();
                    } catch (Exception e) {
                        throw new IllegalStateException("cannot list objects with prefix '" + prefix + "'", e);
                    }
                })
                .filter(item -> !item.isDir() && item.lastModified() != null)
                .map(item -> new StoredObject(item.objectName(), item.lastModified().toInstant()));
    }

    @Override
    public void deleteObject(String objectName) {
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .build()
            );
            log.debug("STORAGE.DELETED objectKey={}", objectName);
        } catch (Exception e) {
            log.error("STORAGE.DELETE.FAILED objectKey={} reason={}", objectName, e.getMessage());
        }
    }
}
