package com.rizkysiregar.narrativ.service;

import com.rizkysiregar.narrativ.constant.GlobalMessage;
import com.rizkysiregar.narrativ.exception.BusinessException;
import com.rizkysiregar.narrativ.model.UserPrincipal;
import com.rizkysiregar.narrativ.model.entity.UserEntity;
import com.rizkysiregar.narrativ.model.enums.Role;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FileAccessPolicyTest {

    private final FileAccessPolicy policy = new FileAccessPolicy();
    private final UUID aliceId = UUID.randomUUID();

    private UserPrincipal principal(String username, Role role) {
        UserEntity user = new UserEntity();
        user.setId(username.equals("alice") ? aliceId : UUID.randomUUID());
        user.setUsername(username);
        user.setRole(role);
        return new UserPrincipal(user);
    }

    private void assertFails(HttpStatus status, GlobalMessage message, Runnable action) {
        BusinessException e = assertThrows(BusinessException.class, action::run);
        assertEquals(status, e.getHttpStatus());
        assertEquals(message.getMessage(), e.getMessage());
    }

    @Test
    void ownerCanReadHisPostMedia() {
        assertDoesNotThrow(() -> policy.checkRead(principal("alice", Role.USER), "alice/POST_FEED/a.jpg"));
        assertDoesNotThrow(() -> policy.checkRead(principal("alice", Role.USER), "alice/POST_FEED/b_thumb.jpg"));
    }

    @Test
    void otherUsersCannotReadPostMediaThroughTheFilesEndpoint() {
        assertFails(HttpStatus.FORBIDDEN, GlobalMessage.ACCESS_DENIED,
                () -> policy.checkRead(principal("bob", Role.USER), "alice/POST_FEED/a.jpg"));
    }

    @Test
    void everybodyCanReadProfilePictures() {
        assertDoesNotThrow(() -> policy.checkRead(principal("bob", Role.USER), "alice/PROFILE_PICTURE/x_upload.png"));
    }

    @Test
    void adminCanReadAnything() {
        assertDoesNotThrow(() -> policy.checkRead(principal("root", Role.ADMIN), "alice/POST_FEED/a.jpg"));
    }

    @Test
    void unknownGroupsAreForbidden() {
        assertFails(HttpStatus.FORBIDDEN, GlobalMessage.ACCESS_DENIED,
                () -> policy.checkRead(principal("alice", Role.USER), "alice/SECRETS/a.jpg"));
    }

    @Test
    void malformedPathsAreBadRequests() {
        UserPrincipal alice = principal("alice", Role.USER);
        for (String path : new String[]{null, " ", "/alice/POST_FEED/a.jpg", "alice/POST_FEED/../../bob/POST_FEED/a.jpg",
                "alice/POST_FEED", "alice//a.jpg", "alice\\POST_FEED\\a.jpg"}) {
            assertFails(HttpStatus.BAD_REQUEST, GlobalMessage.BAD_REQUEST, () -> policy.checkRead(alice, path));
        }
    }

    @Test
    void uploadIsOnlyForTheCallersOwnProfilePicture() {
        UserPrincipal alice = principal("alice", Role.USER);
        assertDoesNotThrow(() -> policy.checkUpload(alice, "alice", "PROFILE_PICTURE"));
        assertDoesNotThrow(() -> policy.checkUpload(alice, aliceId.toString(), "PROFILE_PICTURE"));

        assertFails(HttpStatus.FORBIDDEN, GlobalMessage.ACCESS_DENIED, () -> policy.checkUpload(alice, "bob", "PROFILE_PICTURE"));
        // post media must use the presigned flow, which validates type, size and the 5 media limit
        assertFails(HttpStatus.BAD_REQUEST, GlobalMessage.BAD_REQUEST, () -> policy.checkUpload(alice, "alice", "POST_FEED"));
        assertFails(HttpStatus.BAD_REQUEST, GlobalMessage.BAD_REQUEST, () -> policy.checkUpload(alice, "alice/../bob", "PROFILE_PICTURE"));
        assertFails(HttpStatus.BAD_REQUEST, GlobalMessage.BAD_REQUEST, () -> policy.checkUpload(alice, "", "PROFILE_PICTURE"));
    }
}
