package com.rizkysiregar.narrativ.model.response;

import java.util.UUID;

public record FollowUserResponse(UUID id, String username, String firstName, String lastName, String profileImage) {
    public FollowUserResponse withProfileImage(String url) {
        return new FollowUserResponse(id, username, firstName, lastName, url);
    }
}
