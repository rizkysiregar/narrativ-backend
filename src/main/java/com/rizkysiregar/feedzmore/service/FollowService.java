package com.rizkysiregar.feedzmore.service;

import com.rizkysiregar.feedzmore.model.response.FollowStatusResponse;
import com.rizkysiregar.feedzmore.model.response.FollowUserResponse;
import com.rizkysiregar.feedzmore.model.response.PaginatedResponse;
import com.rizkysiregar.feedzmore.util.Operation;
import java.util.UUID;

public interface FollowService {
    Operation follow(UUID userId, UUID requesterId);

    Operation unfollow(UUID userId, UUID requesterId);

    PaginatedResponse<FollowUserResponse> getFollowers(UUID userId, UUID requesterId, int page, int size);

    PaginatedResponse<FollowUserResponse> getFollowing(UUID userId, UUID requesterId, int page, int size);

    FollowStatusResponse getFollowStatus(UUID userId, UUID requesterId);
}
