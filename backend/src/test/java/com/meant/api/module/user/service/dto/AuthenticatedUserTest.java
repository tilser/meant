package com.meant.api.module.user.service.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class AuthenticatedUserTest {

    @Test
    void readsFullNameFromSupabaseProviderMetadata() {
        AuthenticatedUser user = AuthenticatedUser.fromJwt(jwt(Map.of(
                "user_metadata", Map.of("full_name", "Ada Lovelace"))));

        assertThat(user.firstName()).isEqualTo("Ada");
        assertThat(user.surname()).isEqualTo("Lovelace");
    }

    @Test
    void readsStructuredOpenIdProviderNameClaims() {
        AuthenticatedUser user = AuthenticatedUser.fromJwt(jwt(Map.of(
                "given_name", "Grace",
                "family_name", "Hopper")));

        assertThat(user.firstName()).isEqualTo("Grace");
        assertThat(user.surname()).isEqualTo("Hopper");
    }

    @Test
    void leavesNameEmptyWhenProviderHasNoName() {
        AuthenticatedUser user = AuthenticatedUser.fromJwt(jwt(Map.of()));

        assertThat(user.firstName()).isNull();
        assertThat(user.surname()).isNull();
    }

    @Test
    void acceptsAnonymousIdentityWithoutEmailOrNameClaims() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(UUID.randomUUID().toString())
                .claim("is_anonymous", true)
                .build();

        AuthenticatedUser user = AuthenticatedUser.fromJwt(jwt);

        assertThat(user.email()).isNull();
        assertThat(user.firstName()).isNull();
        assertThat(user.surname()).isNull();
        assertThat(user.anonymous()).isTrue();
    }

    @Test
    void treatsSupabaseAnonymousEmptyEmailAsMissing() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(UUID.randomUUID().toString())
                .claim("email", "  ")
                .claim("is_anonymous", true)
                .build();

        AuthenticatedUser user = AuthenticatedUser.fromJwt(jwt);

        assertThat(user.email()).isNull();
    }

    @Test
    void treatsMissingAnonymousClaimAsPermanent() {
        AuthenticatedUser user = AuthenticatedUser.fromJwt(jwt(Map.of()));

        assertThat(user.anonymous()).isFalse();
    }

    private Jwt jwt(Map<String, Object> claims) {
        Jwt.Builder builder = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(UUID.randomUUID().toString())
                .claim("email", "shopper@example.com");
        claims.forEach(builder::claim);
        return builder.build();
    }
}
