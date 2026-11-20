package com.rizkysiregar.narrativ.model.response;

/** A profile as anyone with its link may see it. {@code avatarPath} is relative to the API base and stable. */
public record PublicProfileResponse(
        String username,
        String fullName,
        String avatarPath,
        long followerCount,
        long followingCount
) {
}
