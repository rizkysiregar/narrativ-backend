package com.rizkysiregar.narrativ.service;

import com.rizkysiregar.narrativ.model.UserPrincipal;
import com.rizkysiregar.narrativ.model.enums.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.*;
import java.util.function.Function;

@Service
@Slf4j
public class JWTService {
    public static final long ACCESS_TOKEN_TTL_MS = 1000L * 60 * 15;

    @Value("${secret.key}")
    private String secretKey;

    /**
     * The subject is the user id, never the username: a username can be changed and then taken by somebody else,
     * and a token that is still valid must keep pointing at the account it was issued to.
     */
    public String generateToken(String username, UUID userId, Role role) {

        Map<String, Object> claims = new HashMap<>();
        claims.put("username", username);
        claims.put("role", role.name());

        return Jwts.builder()
                .claims()
                .add(claims)
                .subject(userId.toString())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + ACCESS_TOKEN_TTL_MS)) // 15 minutes expiry
                .and()
                .signWith(getKey())
                .compact();

    }

    private SecretKey getKey() {
        byte[] keyBytes = Decoders.BASE64.decode(secretKey);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    /** Throws IllegalArgumentException when the subject is not a user id (e.g. a token from before this change). */
    public UUID extractUserId(String token) {
        return UUID.fromString(extractClaims(token, Claims::getSubject));
    }

    private <T> T extractClaims(String token, Function<Claims, T> claimResolver) {
        final Claims claims = extractAllClaims(token);
        return claimResolver.apply(claims);
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();

    }

    public boolean validateToken(String token, UserPrincipal principal) {
        return extractUserId(token).equals(principal.getUserId()) && !isTokenExpired(token);
    }

    private boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    private Date extractExpiration(String token) {
        return extractClaims(token, Claims::getExpiration);
    }
}
