package com.rizkysiregar.feedzmore.messaging.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Command sent to the media worker asking it to generate a thumbnail for a video.
 * It carries object keys only, never URLs or user data.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VideoThumbnailRequestedEvent {
    private UUID postId;
    /** The post_media row the thumbnail belongs to; echoed back in the result. */
    private UUID mediaId;
    /** Object key of the uploaded video. */
    private String mediaUrl;
    /** Object key the worker must write the generated thumbnail to. */
    private String thumbnailObjectKey;

    public static VideoThumbnailRequestedEvent of(UUID postId, UUID mediaId, String mediaUrl) {
        return VideoThumbnailRequestedEvent.builder()
                .postId(postId)
                .mediaId(mediaId)
                .mediaUrl(mediaUrl)
                .thumbnailObjectKey(thumbnailObjectKeyFor(mediaUrl))
                .build();
    }

    public static String thumbnailObjectKeyFor(String mediaUrl) {
        int dot = mediaUrl.lastIndexOf('.');
        if (dot == -1) {
            return mediaUrl + "_thumb.jpg";
        }
        return mediaUrl.substring(0, dot) + "_thumb.jpg";
    }
}
