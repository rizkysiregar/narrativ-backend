package com.rizkysiregar.narrativ.model.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AddPostRequest {
    private String caption;
    /** Object keys from {@code /posts/upload-url}, in display order. Empty or null for a caption-only post. */
    private List<String> mediaKeys;
}
