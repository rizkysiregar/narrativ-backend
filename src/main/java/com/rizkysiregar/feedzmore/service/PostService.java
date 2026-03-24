package com.rizkysiregar.feedzmore.service;


import com.rizkysiregar.feedzmore.model.dto.AddPostDTO;
import com.rizkysiregar.feedzmore.model.dto.PostDTO;
import com.rizkysiregar.feedzmore.model.request.AddPostRequest;
import com.rizkysiregar.feedzmore.model.response.PaginatedResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;


public interface PostService {
    PaginatedResponse<PostDTO> getAllPost(int page, int size, UUID userId);
    PaginatedResponse<PostDTO> getAllPostByUserId(int page, int size, String username);
    AddPostDTO addNewPost(AddPostRequest addPostRequest, MultipartFile image) throws Exception ;
}
