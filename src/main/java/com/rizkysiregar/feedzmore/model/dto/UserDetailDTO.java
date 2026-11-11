package com.rizkysiregar.feedzmore.model.dto;

import com.rizkysiregar.feedzmore.model.entity.UserEntity;

public record UserDetailDTO(
        String id,
        String username,
        String email,
        String firstName,
        String lastName,
        String fullName,
        String phoneNumber,
        String profileImageUrl
) {
    public UserDetailDTO withProfileImageUrl(String url) {
        return new UserDetailDTO(id, username, email, firstName, lastName, fullName, phoneNumber, url);
    }

    public static UserDetailDTO from(UserEntity userEntity) {
        return new UserDetailDTO(
                userEntity.getId().toString(),
                userEntity.getUsername(),
                userEntity.getEmail(),
                userEntity.getFirstName(),
                userEntity.getLastName(),
                userEntity.getFirstName() + " " + userEntity.getLastName(),
                userEntity.getPhoneNumber(),
                userEntity.getProfileImage()
        );
    }
}
