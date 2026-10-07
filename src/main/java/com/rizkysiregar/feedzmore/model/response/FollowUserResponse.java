package com.rizkysiregar.feedzmore.model.response;

import java.util.UUID;

public record FollowUserResponse(UUID id, String username, String firstName, String lastName, String profileImage) {
}
