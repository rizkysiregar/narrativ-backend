package com.rizkysiregar.narrativ.model.dto;

import com.rizkysiregar.narrativ.model.entity.UserEntity;

/**
 * What any signed in user may see of another user. Email and phone number are private: they are only returned
 * to the owner ({@link UserDetailDTO}, {@code /users/me}) and to admins ({@link AdminUserDetailDTO}).
 */
public record PublicUserDTO(
        String id,
        String username,
        String firstName,
        String lastName,
        String fullName,
        String profileImageUrl
) {
    public PublicUserDTO withProfileImageUrl(String url) {
        return new PublicUserDTO(id, username, firstName, lastName, fullName, url);
    }

    public static PublicUserDTO from(UserEntity userEntity) {
        return new PublicUserDTO(
                userEntity.getId().toString(),
                userEntity.getUsername(),
                userEntity.getFirstName(),
                userEntity.getLastName(),
                userEntity.getFirstName() + " " + userEntity.getLastName(),
                userEntity.getProfileImage()
        );
    }
}
