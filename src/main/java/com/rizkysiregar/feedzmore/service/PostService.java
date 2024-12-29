package com.rizkysiregar.feedzmore.service;


import com.rizkysiregar.feedzmore.model.dto.PostDTO;
import com.rizkysiregar.feedzmore.model.response.PaginatedResponse;


public interface PostService {
    PaginatedResponse<PostDTO> getAllPost(int page, int size);
}
