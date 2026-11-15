package com.rizkysiregar.feedzmore.service.appcheck;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.rizkysiregar.feedzmore.service.appcheck.AppCheckTokenVerifier.InvalidTokenException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwksAppCheckTokenVerifierTest {

    private static final String PROJECT_NUMBER = "1023550390414";
    private static final String APP_ID = "1:1023550390414:android:abc";

    private static KeyPair keys;
    private static KeyPair otherKeys;
    private static JwksAppCheckTokenVerifier verifier;

    @BeforeAll
    static void setUp() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        keys = generator.generateKeyPair();
        otherKeys = generator.generateKeyPair();

        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey((RSAPublicKey) keys.getPublic()).build();
        decoder.setJwtValidator(JwksAppCheckTokenVerifier.validators(PROJECT_NUMBER));
        verifier = new JwksAppCheckTokenVerifier(decoder);
    }

    private static JWTClaimsSet.Builder validClaims() {
        return new JWTClaimsSet.Builder()
                .issuer("https://firebaseappcheck.googleapis.com/" + PROJECT_NUMBER)
                .audience(List.of("projects/" + PROJECT_NUMBER, "projects/feedzmore"))
                .subject(APP_ID)
                .expirationTime(new Date(System.currentTimeMillis() + 3_600_000));
    }

    private static String sign(JWTClaimsSet claims, KeyPair signWith) throws Exception {
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
        jwt.sign(new RSASSASigner((RSAPrivateKey) signWith.getPrivate()));
        return jwt.serialize();
    }

    @Test
    void aValidTokenGivesTheAppId() throws Exception {
        assertEquals(APP_ID, verifier.verify(sign(validClaims().build(), keys)));
    }

    @Test
    void aTokenSignedWithAnotherKeyIsInvalid() throws Exception {
        String forged = sign(validClaims().build(), otherKeys);
        assertThrows(InvalidTokenException.class, () -> verifier.verify(forged));
    }

    @Test
    void aTokenOfAnotherProjectIsInvalid() throws Exception {
        String otherIssuer = sign(validClaims().issuer("https://firebaseappcheck.googleapis.com/999").build(), keys);
        String otherAudience = sign(validClaims().audience(List.of("projects/999")).build(), keys);
        assertThrows(InvalidTokenException.class, () -> verifier.verify(otherIssuer));
        assertThrows(InvalidTokenException.class, () -> verifier.verify(otherAudience));
    }

    @Test
    void anExpiredTokenIsInvalid() throws Exception {
        String expired = sign(validClaims().expirationTime(new Date(System.currentTimeMillis() - 3_600_000)).build(), keys);
        assertThrows(InvalidTokenException.class, () -> verifier.verify(expired));
    }

    @Test
    void aTokenWithoutAnAppIdIsInvalid() throws Exception {
        String noSubject = sign(validClaims().subject(" ").build(), keys);
        assertThrows(InvalidTokenException.class, () -> verifier.verify(noSubject));
    }

    @Test
    void garbageIsInvalid() {
        assertThrows(InvalidTokenException.class, () -> verifier.verify("not-a-jwt"));
    }
}
