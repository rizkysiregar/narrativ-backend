package com.rizkysiregar.feedzmore.model.response;

public record FollowStatusResponse(boolean isFollowing, long followerCount, long followingCount) {
}
