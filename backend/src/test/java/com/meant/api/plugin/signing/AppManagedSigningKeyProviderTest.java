package com.meant.api.plugin.signing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class AppManagedSigningKeyProviderTest {

    private static final Instant NOW = Instant.parse("2026-06-29T12:00:00Z");

    @Test
    void loadsActivePrivateKeyAndAdvertisesRotationGraceWithoutPrivateMaterial() {
        char[] privateJwk = privateJwk("transport-active").toCharArray();
        AppManagedSigningKeyProvider provider = provider(
                new SigningKeyProperties.Key(
                        "transport-active",
                        SigningKeyPurpose.TRANSPORT,
                        SigningKeyStatus.ACTIVE,
                        privateJwk,
                        null,
                        null
                ),
                new SigningKeyProperties.Key(
                        "transport-old",
                        SigningKeyPurpose.TRANSPORT,
                        SigningKeyStatus.RETIRING,
                        null,
                        publicJwk("transport-old").toCharArray(),
                        NOW.plus(Duration.ofHours(1))
                ),
                new SigningKeyProperties.Key(
                        "transport-revoked",
                        SigningKeyPurpose.TRANSPORT,
                        SigningKeyStatus.REVOKED,
                        null,
                        publicJwk("transport-revoked").toCharArray(),
                        NOW.plus(Duration.ofHours(1))
                )
        );

        assertThat(provider.activePrivateKey(SigningKeyPurpose.TRANSPORT).kid()).isEqualTo("transport-active");
        assertThat(privateJwk).containsOnly('\0');
        assertThat(provider.publicKeys())
                .extracting(PublicSigningKey::kid)
                .containsExactly("transport-active", "transport-old");
        assertThat(provider.publicKeys().getFirst().jwk().alg()).isEqualTo("ES256");
        assertThat(provider.publicKeys().getFirst().jwk().kty()).isEqualTo("EC");
    }

    @Test
    void separatesKeysByPurpose() {
        AppManagedSigningKeyProvider provider = provider(new SigningKeyProperties.Key(
                "ap2-active",
                SigningKeyPurpose.AP2_ISSUER,
                SigningKeyStatus.ACTIVE,
                privateJwk("ap2-active").toCharArray(),
                null,
                null
        ));

        assertThat(provider.activePrivateKey(SigningKeyPurpose.AP2_ISSUER).kid()).isEqualTo("ap2-active");
        assertThatThrownBy(() -> provider.activePrivateKey(SigningKeyPurpose.TRANSPORT))
                .isInstanceOf(SigningException.class)
                .hasMessageContaining("transport");
    }

    @Test
    void rejectsKidReuseAcrossPurposes() {
        assertThatThrownBy(() -> provider(
                new SigningKeyProperties.Key(
                        "shared",
                        SigningKeyPurpose.TRANSPORT,
                        SigningKeyStatus.ACTIVE,
                        privateJwk("shared").toCharArray(),
                        null,
                        null
                ),
                new SigningKeyProperties.Key(
                        "shared",
                        SigningKeyPurpose.AP2_ISSUER,
                        SigningKeyStatus.RETIRING,
                        null,
                        publicJwk("shared").toCharArray(),
                        NOW.plus(Duration.ofHours(1))
                )
        ))
                .isInstanceOf(SigningException.class)
                .hasMessageContaining("multiple purposes");
    }

    @Test
    void rejectsNonEs256AlgorithmDeclaredByJwk() {
        assertThatThrownBy(() -> provider(new SigningKeyProperties.Key(
                "wrong-alg",
                SigningKeyPurpose.TRANSPORT,
                SigningKeyStatus.ACTIVE,
                privateJwk("wrong-alg").replace("\"alg\":\"ES256\"", "\"alg\":\"RS256\"").toCharArray(),
                null,
                null
        )))
                .isInstanceOf(SigningException.class)
                .hasMessageContaining("non-ES256");
    }

    private AppManagedSigningKeyProvider provider(SigningKeyProperties.Key... keys) {
        SigningKeyProperties properties = new SigningKeyProperties(
                Duration.ofMinutes(5),
                Duration.ofSeconds(30),
                List.of(keys)
        );
        return new AppManagedSigningKeyProvider(
                properties,
                new ObjectMapper(),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    static String privateJwk(String kid) {
        return """
                {"kty":"EC","alg":"ES256","crv":"P-256","kid":"%s","d":"UpuF81l-kOxbjf7T4mNSv0r5tN67Gim7rnf6EFpcYDs","x":"qIVYZVLCrPZHGHjP17CTW0_-D9Lfw0EkjqF7xB4FivA","y":"Mc4nN9LTDOBhfoUeg8Ye9WedFRhnZXZJA12Qp0zZ6F0"}
                """.formatted(kid).trim();
    }

    static String publicJwk(String kid) {
        return """
                {"kty":"EC","crv":"P-256","kid":"%s","x":"qIVYZVLCrPZHGHjP17CTW0_-D9Lfw0EkjqF7xB4FivA","y":"Mc4nN9LTDOBhfoUeg8Ye9WedFRhnZXZJA12Qp0zZ6F0"}
                """.formatted(kid).trim();
    }
}
