package com.rizkysiregar.feedzmore.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

/**
 * Author: Habib Rizky Almajid Siregar
 * Date: 17/10/25
 * Description: Programmer
 */


@Data
@AllArgsConstructor
@Builder
public class FileUrlResponse {
    private String fileName;
    private String url;
}
