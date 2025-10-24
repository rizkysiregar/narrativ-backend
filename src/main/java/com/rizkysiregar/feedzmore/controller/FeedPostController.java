package com.rizkysiregar.feedzmore.controller;

import com.rizkysiregar.feedzmore.model.dto.AddPostDTO;
import com.rizkysiregar.feedzmore.model.dto.PostDTO;
import com.rizkysiregar.feedzmore.model.request.AddPostRequest;
import com.rizkysiregar.feedzmore.model.response.PaginatedResponse;
import com.rizkysiregar.feedzmore.service.PostService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/posts")
public class FeedPostController {
    @Autowired
    private PostService postService;

    @GetMapping
    public PaginatedResponse<PostDTO> getAllPosts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam UUID userId) {
        return postService.getAllPost(page, size, userId);
    }

    @Operation(summary = "Upload new post feed")
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AddPostDTO upload(
            @Parameter(
                    description = "Other payloads that related to the post",
                    required = true,
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = AddPostRequest.class)
                    )
            )
            @RequestPart(value = "addPostRequest") AddPostRequest addPostRequest,

            @Parameter(description = "A image that want to post", required = true)
            @RequestPart(value = "image") MultipartFile image
    ) throws Exception {
        return postService.addNewPost(addPostRequest, image);
    }


}