package com.rizkysiregar.feedzmore.service.impl;


import com.rizkysiregar.feedzmore.model.dto.PostDTO;
import com.rizkysiregar.feedzmore.model.entity.PostEntity;
import com.rizkysiregar.feedzmore.model.response.PaginatedResponse;
import com.rizkysiregar.feedzmore.repository.PostRepository;
import com.rizkysiregar.feedzmore.service.PostService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import java.util.List;


@Service
@RequiredArgsConstructor
@Slf4j
public class PostServiceImpl implements PostService {
    private final PostRepository postRepository;

    @Override
    public PaginatedResponse<PostDTO> getAllPost(int page, int size) {
        log.info(":: Fetching post pagination with page=  " + page + ", size= " + size);
        Pageable pageable = PageRequest.of(page, size);
        Page<PostEntity> postPage = postRepository.findAll(pageable);

        List<PostDTO> postDto = postPage.getContent().stream().map(post -> {
            PostDTO dto = new PostDTO();
            dto.setId(post.getId());
            dto.setUsername(post.getUser().getUsername());
            dto.setCaption(post.getCaption());
            dto.setImageUrl(post.getImageUrl());
            dto.setCreatedAt(post.getCreatedAt());
            return dto;
        }).toList();

        return new PaginatedResponse<>(
                postDto,
                postPage.getNumber(),
                postPage.getSize(),
                postPage.getTotalElements(),
                postPage.getTotalPages(),
                postPage.isLast());
    }
}
