package com.rizkysiregar.narrativ.service;

import com.rizkysiregar.narrativ.model.dto.ImageObject;
import com.rizkysiregar.narrativ.model.dto.ObjectStat;
import com.rizkysiregar.narrativ.model.dto.PresignedUploadForm;
import com.rizkysiregar.narrativ.model.dto.StoredObject;

import java.io.InputStream;
import java.util.stream.Stream;


/**
 * Author: Habib Rizky Almajid Siregar
 * Date: 17/10/25
 * Description: Programmer
 */

public interface FileStorageService {

    String getPresignUrl(String objectName) throws Exception;
    String getPresignUploadUrl(String objectName, String contentType) throws Exception;

    /** A presigned POST for exactly this key and content type, refused by storage above {@code maxBytes}. */
    PresignedUploadForm getPresignUploadForm(String objectName, String contentType, long maxBytes) throws Exception;
    ObjectStat statObject(String objectName);
    ImageObject getObject(String objectName, String bucketName);

    String uploadObject(String objectName, InputStream stream, long size, String contentType) throws Exception;

    void deleteObject(String objectName);

    /** Lazily lists every object (recursively) whose key starts with the prefix; an empty prefix lists the bucket. */
    Stream<StoredObject> listObjects(String prefix);
}
