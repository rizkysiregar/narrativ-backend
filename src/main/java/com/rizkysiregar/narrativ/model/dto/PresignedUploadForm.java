package com.rizkysiregar.narrativ.model.dto;

import java.util.Map;

/**
 * A presigned POST upload: send a multipart/form-data POST to {@code url} with every entry of {@code fields} as a
 * form field, and the file last as the field {@code file}. The signed policy fixes the object key, the content type
 * and the allowed size, so object storage itself refuses a larger file, which a presigned PUT cannot do.
 */
public record PresignedUploadForm(String url, Map<String, String> fields) {
}
