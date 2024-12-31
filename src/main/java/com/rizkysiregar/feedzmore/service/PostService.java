package com.rizkysiregar.feedzmore.service;


import com.rizkysiregar.feedzmore.model.dto.AddPostDTO;
import com.rizkysiregar.feedzmore.model.dto.PostDTO;
import com.rizkysiregar.feedzmore.model.request.AddPostRequest;
import com.rizkysiregar.feedzmore.model.response.PaginatedResponse;
import org.springframework.web.multipart.MultipartFile;


public interface PostService {
    PaginatedResponse<PostDTO> getAllPost(int page, int size);
    AddPostDTO addNewPost(AddPostRequest addPostRequest, MultipartFile image);
}
