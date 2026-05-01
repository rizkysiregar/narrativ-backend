package com.rizkysiregar.feedzmore.model.dto;


import com.rizkysiregar.feedzmore.model.entity.UserEntity;
import com.rizkysiregar.feedzmore.model.enums.Role;

public record AdminUserDetailDTO(
        String id,
        String username,
        String email,
        String firstName,
        String lastName,
        String fullName,
        String phoneNumber,
        String profileImageUrl,
        Role role,
        boolean deleted
) {
    public static AdminUserDetailDTO from(UserEntity userEntity) {
        return new AdminUserDetailDTO(
                userEntity.getId().toString(),
                userEntity.getUsername(),
                userEntity.getEmail(),
                userEntity.getFirstName(),
                userEntity.getLastName(),
                userEntity.getFirstName() + " " + userEntity.getLastName(),
                userEntity.getPhoneNumber(),
                userEntity.getProfileImage(),
                userEntity.getRole(),
                userEntity.isDeleted()
        );
    }
}
