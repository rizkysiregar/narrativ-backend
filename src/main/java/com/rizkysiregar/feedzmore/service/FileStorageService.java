package com.rizkysiregar.feedzmore.service;

import com.rizkysiregar.feedzmore.model.dto.ImageObject;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;


/**
 * Author: Habib Rizky Almajid Siregar
 * Date: 17/10/25
 * Description: Programmer
 */

public interface FileStorageService {
    String uploadFile(MultipartFile file, String userId, String featureName) throws Exception;

    String getPresignUrl(String objectName) throws Exception;
    ImageObject getObject(String objectName, String bucketName);

    String uploadObject(String objectName, InputStream stream, long size, String contentType) throws Exception;

    void deleteObject(String objectName);
}
