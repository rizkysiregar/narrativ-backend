package com.rizkysiregar.feedzmore.service.impl;


import com.rizkysiregar.feedzmore.constant.GlobalMessage;
import com.rizkysiregar.feedzmore.constant.ObjectStorageTypeGroup;
import com.rizkysiregar.feedzmore.exception.BusinessException;
import com.rizkysiregar.feedzmore.model.dto.AddPostDTO;
import com.rizkysiregar.feedzmore.model.dto.PostDTO;
import com.rizkysiregar.feedzmore.model.entity.PostEntity;
import com.rizkysiregar.feedzmore.model.entity.UserEntity;
import com.rizkysiregar.feedzmore.model.request.AddPostRequest;
import com.rizkysiregar.feedzmore.model.response.PaginatedResponse;
import com.rizkysiregar.feedzmore.repository.*;
import com.rizkysiregar.feedzmore.service.FileStorageService;
import com.rizkysiregar.feedzmore.service.PostService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.swing.text.html.Option;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;


@Service
@RequiredArgsConstructor
@Slf4j
public class PostServiceImpl implements PostService {

    private final PostPaginationRepository postPaginationRepository;
    private final PostCrudRepository postCrudRepository;
    private final UserRepository userRepository;
    private final LikeRepository likeRepository;
    private final CommentRepository commentRepository;
    private final FileStorageService fileStorageService;

    @Override
    public PaginatedResponse<PostDTO> getAllPost(int page, int size, UUID userId) {
        log.info("FETCH.ALL.FEED: {}, size= {} ,userId={}", page, size, userId);
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<PostDTO> postPage = postPaginationRepository.findAllPost(pageable, userId);
        return buildPaginatedResponse(postPage);
    }

    @Override
    public PaginatedResponse<PostDTO> getAllPostByUserId(int page, int size, String username) {
        log.info("FETCH.FEED.BY.USERNAME =  {}, size= {} , username= {}", page, size, username);

        UserEntity user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(GlobalMessage.USER_NOT_FOUND, HttpStatus.NOT_FOUND));

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<PostDTO> postPage = postPaginationRepository.findAllPostByUserId(pageable, user.getId());
        return buildPaginatedResponse(postPage);
    }

    private PaginatedResponse<PostDTO> buildPaginatedResponse(Page<PostDTO> postPage) {
        List<PostDTO> updatedContent = postPage.getContent().stream()
                .peek(post -> {
                    try {
                        post.setImageUrl(fileStorageService.getPresignUrl(post.getImageUrl()));
                    } catch (Exception e) {
                        log.error("## cannot get presign url for objectName= {}, because of= {}", post.getImageUrl(), e.getMessage());
                    }

                }).toList();

        return new PaginatedResponse<>(
                updatedContent,
                postPage.getNumber(),
                postPage.getSize(),
                postPage.getTotalElements(),
                postPage.getTotalPages(),
                postPage.isLast());
    }

    @Override
    public AddPostDTO addNewPost(AddPostRequest addPostRequest, MultipartFile image) throws Exception {
        log.info("NEW.FEED.POST: {}", addPostRequest.getUsername());

        if (userRepository.findByUsername(addPostRequest.getUsername()).isPresent()) {
            if (image.isEmpty()) {
                log.error("Image is empty");
                throw new BusinessException(GlobalMessage.BAD_REQUEST, HttpStatus.BAD_REQUEST);
            }

            if (image.getSize() > 5 * 1024 * 1024) { // Limit to max 5 mb
                log.error("Image too large");
                throw new BusinessException(GlobalMessage.BAD_REQUEST_FILE_TO_LARGE, HttpStatus.BAD_REQUEST);
            }

            String objectName = fileStorageService.uploadFile(image, addPostRequest.getUsername(), ObjectStorageTypeGroup.POST_FEED);
            Optional<UserEntity> user = userRepository.findByUsername(addPostRequest.getUsername());
            user.ifPresentOrElse(
                    userEntity -> {
                        PostEntity postEntity = PostEntity.builder()
                                .caption(addPostRequest.getCaption())
                                .createdAt(LocalDateTime.now())
                                .imageUrl(objectName)
                                .user(userEntity)
                                .build();
                        postCrudRepository.save(postEntity);
                    },
                    () -> {
                        throw new BusinessException(GlobalMessage.BAD_REQUEST, HttpStatus.BAD_REQUEST);
                    }
            );

            return AddPostDTO.builder()
                    .status(GlobalMessage.CREATED.getMessage())
                    .build();
        } else {
            log.error("::. Username not found {}", addPostRequest.getUsername());
            throw new BusinessException(GlobalMessage.BAD_REQUEST, HttpStatus.BAD_REQUEST);
        }
    }
}
