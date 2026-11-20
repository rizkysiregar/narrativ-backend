package com.rizkysiregar.narrativ.model.response;

import com.rizkysiregar.narrativ.model.enums.MediaType;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A post as anyone with its link may see it, signed in or not: what a share preview (Open Graph) and the public
 * page of the web client need, nothing about the viewer. The nested records carry the Public prefix because the
 * OpenAPI document names schemas by simple class name, and a Media schema exists already. Image paths are relative to the API base (/api/v1) and
 * stable: they redirect to a fresh presigned URL, so a crawler can keep them longer than the URL's hour.
 */
public record PublicPostResponse(
        UUID id,
        String caption,
        Instant createdAt,
        long likeCount,
        long commentCount,
        PublicAuthor author,
        List<PublicMedia> media
) {
    public record PublicAuthor(String username, String fullName, String avatarPath) {
    }

    /** {@code imagePath}: the photo, or the thumbnail of a video (null while the thumbnail is not made yet). */
    public record PublicMedia(UUID id, MediaType type, String imagePath) {
    }
}
