package com.rizkysiregar.feedzmore.model.dto;

import com.rizkysiregar.feedzmore.model.entity.UserEntity;
import com.rizkysiregar.feedzmore.model.enums.Role;
import com.rizkysiregar.feedzmore.model.enums.UserStatus;

import java.time.Instant;

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
        boolean deleted,
        UserStatus status,
        Instant bannedAt,
        String bannedBy,
        String banReason,
        Stats stats
) {
    /** Raw row counts for the admin detail view; null in list and update responses. */
    public record Stats(long postCount, long commentCount, long followerCount, long followingCount) {}

    public AdminUserDetailDTO withProfileImageUrl(String url) {
        return new AdminUserDetailDTO(id, username, email, firstName, lastName, fullName, phoneNumber, url, role,
                deleted, status, bannedAt, bannedBy, banReason, stats);
    }

    public static AdminUserDetailDTO from(UserEntity userEntity) {
        return from(userEntity, null);
    }

    public static AdminUserDetailDTO from(UserEntity userEntity, Stats stats) {
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
                userEntity.isDeleted(),
                userEntity.getStatus(),
                userEntity.getBannedAt(),
                userEntity.getBannedBy() == null ? null : userEntity.getBannedBy().toString(),
                userEntity.getBanReason(),
                stats
        );
    }
}
