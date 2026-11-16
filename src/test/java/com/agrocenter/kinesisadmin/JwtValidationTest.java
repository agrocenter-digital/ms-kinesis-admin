package com.agrocenter.kinesisadmin;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class JwtValidationTest {
    private Jwt token(String use, String client) {
        return Jwt.withTokenValue("test-token").header("alg", "RS256")
                .claim("token_use", use).claim("client_id", client).build();
    }

    @Test void noAudienceMatchesValidClient() {
        assertFalse(Security.validateAccess(token("access", "client-1"), "client-1").hasErrors());
    }
    @Test void noAudienceAndDifferentClientAreRejectedWithoutException() {
        assertTrue(Security.validateAccess(token("access", "other"), "client-1").hasErrors());
    }
    @Test void idTokenIsRejectedEvenWhenClientMatches() {
        assertTrue(Security.validateAccess(token("id", "client-1"), "client-1").hasErrors());
    }
    @Test void matchingAudienceIsAcceptedForAccessToken() {
        Jwt jwt = Jwt.withTokenValue("test-token").header("alg", "RS256")
                .claim("token_use", "access").claim("aud", java.util.List.of("client-1")).build();
        assertFalse(Security.validateAccess(jwt, "client-1").hasErrors());
    }
}
