package com.rizkysiregar.feedzmore.messaging.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Result published by the media worker once the thumbnail object exists in storage.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VideoThumbnailGeneratedEvent {
    private UUID postId;
    /** Object key of the generated thumbnail, echoed from the request. */
    private String thumbnailObjectKey;
}
