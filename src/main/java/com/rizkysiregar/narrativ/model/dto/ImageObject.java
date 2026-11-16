package com.rizkysiregar.narrativ.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.http.MediaType;

import java.io.InputStream;


@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ImageObject {
    private InputStream stream;
    private MediaType contentType;
    private long size;
}
