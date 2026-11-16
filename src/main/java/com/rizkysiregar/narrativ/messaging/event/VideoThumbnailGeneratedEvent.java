package com.rizkysiregar.narrativ.messaging.event;

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
    /** The post_media row the thumbnail belongs to, echoed from the request. */
    private UUID mediaId;
    /** Object key of the generated thumbnail, echoed from the request. */
    private String thumbnailObjectKey;
}
