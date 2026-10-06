package com.rizkysiregar.feedzmore.mapper;

import com.rizkysiregar.feedzmore.model.response.CommentResponse;
import com.rizkysiregar.feedzmore.model.entity.CommentEntity;

import java.util.List;
import java.util.stream.Collectors;

public class CommentMapper {
    public static CommentResponse toDto(CommentEntity entity) {
        return new CommentResponse(
                entity.getUser().getUsername(),
                entity.getUser().getProfileImage(),
                entity.getContent()
        );
    }

    public static List<CommentResponse> toDtoList(List<CommentEntity> entities) {
        return entities.stream()
                .map(CommentMapper::toDto)
                .collect(Collectors.toList());
    }
}
