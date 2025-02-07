package com.rizkysiregar.feedzmore.mapper;

import com.rizkysiregar.feedzmore.model.dto.CommentDTO;
import com.rizkysiregar.feedzmore.model.entity.CommentEntity;

import java.util.List;
import java.util.stream.Collectors;

public class CommentMapper {
    public static CommentDTO toDto(CommentEntity entity) {
        return new CommentDTO(
                entity.getUser().getUsername(),
                entity.getUser().getProfileImage(),
                entity.getContent()
        );
    }

    public static List<CommentDTO> toDtoList(List<CommentEntity> entities) {
        return entities.stream()
                .map(CommentMapper::toDto)
                .collect(Collectors.toList());
    }
}
