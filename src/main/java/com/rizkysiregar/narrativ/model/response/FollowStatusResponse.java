package com.rizkysiregar.narrativ.model.response;

public record FollowStatusResponse(boolean isFollowing, long followerCount, long followingCount) {
}
