package com.rizkysiregar.feedzmore.model.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rizkysiregar.feedzmore.model.entity.UserEntity;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class PublicUserDTOTest {

    private UserEntity user() {
        UserEntity user = new UserEntity();
        user.setId(UUID.randomUUID());
        user.setUsername("rizky");
        user.setEmail("rizky@example.com");
        user.setPhoneNumber("081234567890");
        user.setFirstName("Rizky");
        user.setLastName("Siregar");
        user.setProfileImage("id/PROFILE_PICTURE/a.jpg");
        return user;
    }

    @Test
    void carriesTheNameAndThePictureOfTheUser() {
        UserEntity user = user();

        PublicUserDTO dto = PublicUserDTO.from(user).withProfileImageUrl("https://cdn/a.jpg");

        assertEquals(user.getId().toString(), dto.id());
        assertEquals("rizky", dto.username());
        assertEquals("Rizky Siregar", dto.fullName());
        assertEquals("https://cdn/a.jpg", dto.profileImageUrl());
    }

    @Test
    void neverSerializesTheEmailOrThePhoneNumber() throws Exception {
        String json = new ObjectMapper().writeValueAsString(PublicUserDTO.from(user()));

        JsonNode tree = new ObjectMapper().readTree(json);
        assertFalse(tree.has("email"));
        assertFalse(tree.has("phoneNumber"));
        assertFalse(json.contains("rizky@example.com"));
        assertFalse(json.contains("081234567890"));
    }

    @Test
    void theOwnersViewStillHasThem() {
        UserDetailDTO own = UserDetailDTO.from(user());

        assertEquals("rizky@example.com", own.email());
        assertEquals("081234567890", own.phoneNumber());
    }
}
