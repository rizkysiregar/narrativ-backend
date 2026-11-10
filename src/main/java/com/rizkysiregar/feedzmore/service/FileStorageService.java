package com.rizkysiregar.feedzmore.service;

import com.rizkysiregar.feedzmore.model.dto.ImageObject;
import com.rizkysiregar.feedzmore.model.dto.ObjectStat;
import com.rizkysiregar.feedzmore.model.dto.StoredObject;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.stream.Stream;


/**
 * Author: Habib Rizky Almajid Siregar
 * Date: 17/10/25
 * Description: Programmer
 */

public interface FileStorageService {
    String uploadFile(MultipartFile file, String userId, String featureName) throws Exception;

    String getPresignUrl(String objectName) throws Exception;
    String getPresignUploadUrl(String objectName, String contentType) throws Exception;
    ObjectStat statObject(String objectName);
    ImageObject getObject(String objectName, String bucketName);

    String uploadObject(String objectName, InputStream stream, long size, String contentType) throws Exception;

    void deleteObject(String objectName);

    /** Lazily lists every object (recursively) whose key starts with the prefix; an empty prefix lists the bucket. */
    Stream<StoredObject> listObjects(String prefix);
}
