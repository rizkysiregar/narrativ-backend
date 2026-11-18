package com.rizkysiregar.narrativ.service;

import com.rizkysiregar.narrativ.model.UserPrincipal;
import com.rizkysiregar.narrativ.model.entity.UserEntity;
import com.rizkysiregar.narrativ.model.enums.Role;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Date;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JWTServiceTest {

    private static final String SECRET = "c2VjcmV0LWtleS1mb3ItdGVzdHMtb25seS0zMi1ieXRlcyE=";

    private final JWTService jwtService = new JWTService();

    @BeforeEach
    void setup() {
        ReflectionTestUtils.setField(jwtService, "secretKey", SECRET);
    }

    private static UserPrincipal principal(UUID id, String username) {
        UserEntity user = new UserEntity();
        user.setId(id);
        user.setUsername(username);
        user.setRole(Role.USER);
        return new UserPrincipal(user);
    }

    @Test
    void theSubjectIsTheUserIdSoARenameDoesNotMatter() {
        UUID id = UUID.randomUUID();
        String token = jwtService.generateToken("alice", id, Role.USER);

        assertEquals(id, jwtService.extractUserId(token));
        // the same account under its new name still matches, somebody who took the old name does not
        assertTrue(jwtService.validateToken(token, principal(id, "alice_renamed")));
        assertFalse(jwtService.validateToken(token, principal(UUID.randomUUID(), "alice")));
    }

    @Test
    void aTokenFromBeforeTheChangeCarryingAUsernameIsRejected() {
        String legacy = Jwts.builder().subject("alice")
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRET)))
                .compact();

        assertThrows(IllegalArgumentException.class, () -> jwtService.extractUserId(legacy));
    }
}
