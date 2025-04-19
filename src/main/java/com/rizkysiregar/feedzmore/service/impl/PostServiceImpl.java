package com.rizkysiregar.feedzmore.service.impl;


import com.rizkysiregar.feedzmore.constant.GlobalMessage;
import com.rizkysiregar.feedzmore.exception.BusinessException;
import com.rizkysiregar.feedzmore.model.dto.AddPostDTO;
import com.rizkysiregar.feedzmore.model.dto.PostDTO;
import com.rizkysiregar.feedzmore.model.entity.PostEntity;
import com.rizkysiregar.feedzmore.model.entity.UserEntity;
import com.rizkysiregar.feedzmore.model.request.AddPostRequest;
import com.rizkysiregar.feedzmore.model.response.PaginatedResponse;
import com.rizkysiregar.feedzmore.repository.*;
import com.rizkysiregar.feedzmore.service.PostService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;


@Service
@RequiredArgsConstructor
@Slf4j
public class PostServiceImpl implements PostService {
    @Value("${image.upload.dir.image-post}")
    private String uploadDir;

    private final PostPaginationRepository postPaginationRepository;
    private final PostCrudRepository postCrudRepository;
    private final UserRepository userRepository;
    private final LikeRepository likeRepository;
    private final CommentRepository commentRepository;

    @Override
    public PaginatedResponse<PostDTO> getAllPost(int page, int size, UUID userId) {
        log.info(":: Fetching post pagination with page=  " + page + ", size= " + size + " ,userId=" + userId);
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
//        Page<PostEntity> postPage = postPaginationRepository.findAll(pageable);
        Page<PostDTO> postPage = postPaginationRepository.findAllWithCounts(pageable, userId);


//        List<PostDTO> postDto = postPage.getContent().stream().map(post -> {
//            PostDTO dto = new PostDTO();
//            dto.setId(post.getId());
//            dto.setUsername(post.getUser().getUsername());
//            dto.setCaption(post.getCaption());
//            dto.setImageUrl(post.getImageUrl());
//            dto.setCreatedAt(post.getCreatedAt());
//            dto.setLikeCount(String.valueOf(likeRepository.countByPostId(post.getId())));
//            dto.setCommentCount(String.valueOf(commentRepository.contByPostId(post.getId())));
//            return dto;
//        }).toList();


        return new PaginatedResponse<>(
                postPage.getContent(),
                postPage.getNumber(),
                postPage.getSize(),
                postPage.getTotalElements(),
                postPage.getTotalPages(),
                postPage.isLast());
    }

    @Override
    public AddPostDTO addNewPost(AddPostRequest addPostRequest, MultipartFile image) {
        log.info("::. Entering PostServiceImpl.addNewPost by : " + addPostRequest.getUsername());
        log.info(":: image upload dir : " + uploadDir);
        if (image.isEmpty()) {
            log.error("Image is empty");
            throw new BusinessException(GlobalMessage.BAD_REQUEST, HttpStatus.BAD_REQUEST);
        }

        if (image.getSize() > 5 * 1024 * 1024) { // Limit to max 5 mb
            log.error("Image too large");
            throw new BusinessException(GlobalMessage.BAD_REQUEST_FILE_TO_LARGE, HttpStatus.BAD_REQUEST);
        }

        try {
            // ensure the directory exist
            File dir = new File(uploadDir);
            if (!dir.exists()) dir.mkdir();

            // create a unique file name
            String uniqueFileImageName = System.currentTimeMillis() + "_" + addPostRequest.getUsername() + "_" + image.getOriginalFilename();
            String imagePath = uploadDir + "/" + uniqueFileImageName;

            // save file to the disk, either mac for local or server path for dev/prod
            image.transferTo(new File(imagePath));
            log.info(":: image > " + imagePath + " successfully uploaded.");

            // getting user details data
            Optional<UserEntity> userEntity = userRepository.findByUsername(addPostRequest.getUsername());

            if (userEntity.isEmpty()) {
                throw new BusinessException(GlobalMessage.BAD_REQUEST, HttpStatus.BAD_REQUEST);
            }

            // attempt to save to repo
            PostEntity postEntity = PostEntity.builder()
                    .caption(addPostRequest.getCaption())
                    .createdAt(LocalDateTime.now())
                    .imageUrl(uniqueFileImageName)
                    .user(userEntity.get())
                    .build();

            postCrudRepository.save(postEntity);
        } catch (Exception e) {
            throw new BusinessException(GlobalMessage.INTERNAL_ERROR, HttpStatus.INTERNAL_SERVER_ERROR);
        }

        return AddPostDTO.builder()
                .status(GlobalMessage.CREATED.getMessage())
                .build();
    }
}
