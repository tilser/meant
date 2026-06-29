package com.meant.api.plugin.signing;

import com.nimbusds.jose.Algorithm;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.KeyUse;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Component
public class AppManagedSigningKeyProvider implements SigningKeyProvider {

    private static final TypeReference<Map<String, Object>> JWK_TYPE = new TypeReference<>() {
    };

    private final List<SigningKey> keys;
    private final Clock clock;

    @Autowired
    public AppManagedSigningKeyProvider(SigningKeyProperties properties, ObjectMapper objectMapper) {
        this(properties, objectMapper, Clock.systemUTC());
    }

    AppManagedSigningKeyProvider(SigningKeyProperties properties, ObjectMapper objectMapper, Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.keys = List.copyOf(loadKeys(properties, objectMapper));
        validateKeyOwnership(keys);
        validateActiveKeys(keys);
    }

    @Override
    public SigningKey activePrivateKey(SigningKeyPurpose purpose) {
        return keys.stream()
                .filter(key -> key.purpose() == purpose)
                .filter(SigningKey::canSign)
                .findFirst()
                .orElseThrow(() -> new SigningException(
                        "No active signing key is configured for purpose " + purpose.value()
                ));
    }

    @Override
    public Optional<SigningKey> key(String kid, SigningKeyPurpose purpose) {
        return keys.stream()
                .filter(key -> key.kid().equals(kid))
                .filter(key -> key.purpose() == purpose)
                .findFirst();
    }

    @Override
    public List<PublicSigningKey> publicKeys() {
        return keys.stream()
                .filter(key -> key.canAdvertise(clock.instant()))
                .map(SigningKey::toPublicSigningKey)
                .toList();
    }

    private List<SigningKey> loadKeys(SigningKeyProperties properties, ObjectMapper objectMapper) {
        Objects.requireNonNull(properties, "properties must not be null");
        Objects.requireNonNull(objectMapper, "objectMapper must not be null");

        List<SigningKey> loadedKeys = new ArrayList<>();
        for (SigningKeyProperties.Key keyProperties : properties.keys()) {
            loadedKeys.add(loadKey(keyProperties, objectMapper));
        }
        return loadedKeys;
    }

    private SigningKey loadKey(SigningKeyProperties.Key keyProperties, ObjectMapper objectMapper) {
        ECKey privateKey = parseKey(keyProperties.privateJwk(), objectMapper, keyProperties.kid(), true);
        ECKey configuredPublicKey = parseKey(keyProperties.publicJwk(), objectMapper, keyProperties.kid(), false);
        if (privateKey == null && configuredPublicKey == null) {
            throw new SigningException("Signing key " + keyProperties.kid() + " has no configured key material");
        }
        ECKey publicKey = configuredPublicKey == null ? privateKey.toPublicJWK() : configuredPublicKey;

        if (privateKey != null && configuredPublicKey != null && !samePublicCoordinates(privateKey, configuredPublicKey)) {
            throw new SigningException("Configured public key does not match private key for kid " + keyProperties.kid());
        }

        return new SigningKey(
                keyProperties.kid(),
                keyProperties.purpose(),
                keyProperties.status(),
                privateKey,
                publicKey,
                keyProperties.advertiseUntil()
        );
    }

    private ECKey parseKey(char[] rawJwk, ObjectMapper objectMapper, String kid, boolean requirePrivate) {
        if (!hasText(rawJwk)) {
            return null;
        }

        byte[] jwkBytes = null;
        try {
            jwkBytes = toUtf8Bytes(rawJwk);
            Map<String, Object> jwk = objectMapper.readValue(jwkBytes, JWK_TYPE);
            return sanitizeKey(ECKey.parse(jwk), kid, requirePrivate);
        } catch (CharacterCodingException exception) {
            throw new SigningException("Signing key " + kid + " could not be UTF-8 encoded", exception);
        } catch (JacksonException | ParseException | IllegalArgumentException exception) {
            throw new SigningException("Signing key " + kid + " is not a valid ES256 EC JWK", exception);
        } finally {
            if (jwkBytes != null) {
                Arrays.fill(jwkBytes, (byte) 0);
            }
            Arrays.fill(rawJwk, '\0');
        }
    }

    private ECKey sanitizeKey(ECKey key, String kid, boolean requirePrivate) {
        if (key == null) {
            throw new SigningException("Signing key " + kid + " must not be null");
        }
        if (!Curve.P_256.equals(key.getCurve())) {
            throw new SigningException("Signing key " + kid + " must use P-256");
        }
        if (requirePrivate && !key.isPrivate()) {
            throw new SigningException("Signing key " + kid + " must contain private key material");
        }
        if (!requirePrivate && key.isPrivate()) {
            throw new SigningException("Public signing key " + kid + " must not contain private key material");
        }
        String keyId = key.getKeyID();
        if (keyId != null && !kid.equals(keyId)) {
            throw new SigningException("Signing key kid " + keyId + " does not match configured kid " + kid);
        }
        Algorithm algorithm = key.getAlgorithm();
        if (algorithm != null && !JWSAlgorithm.ES256.getName().equals(algorithm.getName())) {
            throw new SigningException("Signing key " + kid + " must not declare non-ES256 alg " + algorithm.getName());
        }
        return new ECKey.Builder(key)
                .algorithm(JWSAlgorithm.ES256)
                .keyUse(KeyUse.SIGNATURE)
                .keyID(kid)
                .build();
    }

    private static byte[] toUtf8Bytes(char[] chars) throws CharacterCodingException {
        ByteBuffer buffer = StandardCharsets.UTF_8.newEncoder().encode(CharBuffer.wrap(chars));
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        Arrays.fill(buffer.array(), (byte) 0);
        return bytes;
    }

    private void validateKeyOwnership(List<SigningKey> keys) {
        Map<String, SigningKeyPurpose> kidPurposes = new LinkedHashMap<>();
        for (SigningKey key : keys) {
            SigningKeyPurpose existingPurpose = kidPurposes.putIfAbsent(key.kid(), key.purpose());
            if (existingPurpose != null && existingPurpose != key.purpose()) {
                throw new SigningException("Signing key kid " + key.kid() + " is configured for multiple purposes");
            }
        }
    }

    private void validateActiveKeys(List<SigningKey> keys) {
        Map<SigningKeyPurpose, String> activeKids = new LinkedHashMap<>();
        for (SigningKey key : keys) {
            if (!key.canSign()) {
                continue;
            }
            String existingKid = activeKids.putIfAbsent(key.purpose(), key.kid());
            if (existingKid != null) {
                throw new SigningException("Multiple active signing keys are configured for purpose "
                        + key.purpose().value());
            }
        }
    }

    private boolean samePublicCoordinates(ECKey first, ECKey second) {
        return Objects.equals(first.getX(), second.getX()) && Objects.equals(first.getY(), second.getY());
    }

    private boolean hasText(char[] value) {
        if (value == null || value.length == 0) {
            return false;
        }
        for (char ch : value) {
            if (!Character.isWhitespace(ch)) {
                return true;
            }
        }
        return false;
    }
}
