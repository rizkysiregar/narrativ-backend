package com.rizkysiregar.feedzmore.service.impl;

import com.rizkysiregar.feedzmore.model.dto.PostLikeCountDTO;
import com.rizkysiregar.feedzmore.model.request.LikeUnlikePostRequest;
import com.rizkysiregar.feedzmore.model.response.LikesDTO;
import com.rizkysiregar.feedzmore.service.LikeUnlikeService;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class LikeUnlikeServiceImpl implements LikeUnlikeService {
    @Override
    public LikesDTO likePost(LikeUnlikePostRequest request) {
        return null;
    }

    @Override
    public LikesDTO unlikePost(LikeUnlikePostRequest request) {
        return null;
    }

    @Override
    public PostLikeCountDTO getPostLikeCount(UUID postId) {
        return null;
    }
}
