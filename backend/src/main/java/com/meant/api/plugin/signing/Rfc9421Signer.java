package com.meant.api.plugin.signing;

import com.authlete.hms.ComponentIdentifier;
import com.authlete.hms.ComponentValueProvider;
import com.authlete.hms.SignatureBase;
import com.authlete.hms.SignatureBaseBuilder;
import com.authlete.hms.SignatureEntry;
import com.authlete.hms.SignatureField;
import com.authlete.hms.SignatureInputField;
import com.authlete.hms.SignatureMetadata;
import com.authlete.hms.SignatureMetadataParameters;
import com.authlete.hms.impl.JoseHttpSigner;
import com.authlete.hms.impl.JoseHttpVerifier;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.ECKey;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class Rfc9421Signer {

    public static final String CONTENT_TYPE = "Content-Type";
    public static final String CONTENT_DIGEST = "Content-Digest";
    public static final String IDEMPOTENCY_KEY = "Idempotency-Key";
    public static final String REQUEST_ID = "Request-Id";
    public static final String SIGNATURE_INPUT = "Signature-Input";
    public static final String SIGNATURE = "Signature";

    private static final String SIGNATURE_LABEL = "sig1";
    private static final String SIGNATURE_ALGORITHM = "ES256";
    private static final List<ComponentIdentifier> COVERED_COMPONENTS = List.of(
            new ComponentIdentifier("@method"),
            new ComponentIdentifier("@authority"),
            new ComponentIdentifier("@path"),
            new ComponentIdentifier("@query"),
            new ComponentIdentifier("content-type"),
            new ComponentIdentifier("content-digest"),
            new ComponentIdentifier("idempotency-key"),
            new ComponentIdentifier("request-id")
    );

    private final SigningKeyProvider signingKeyProvider;
    private final SigningKeyProperties properties;
    private final Clock clock;

    @Autowired
    public Rfc9421Signer(SigningKeyProvider signingKeyProvider, SigningKeyProperties properties) {
        this(signingKeyProvider, properties, Clock.systemUTC());
    }

    Rfc9421Signer(SigningKeyProvider signingKeyProvider, SigningKeyProperties properties, Clock clock) {
        this.signingKeyProvider = Objects.requireNonNull(signingKeyProvider, "signingKeyProvider must not be null");
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    public SignedRequest sign(SigningRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        SigningKey signingKey = signingKeyProvider.activePrivateKey(SigningKeyPurpose.TRANSPORT);
        Instant created = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        Instant expires = created.plus(properties.signatureTtl());
        String contentDigest = contentDigest(request.body());

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put(CONTENT_TYPE, request.contentType());
        headers.put(CONTENT_DIGEST, contentDigest);
        headers.put(IDEMPOTENCY_KEY, request.idempotencyKey());
        headers.put(REQUEST_ID, request.requestId());

        try {
            SignatureMetadata metadata = metadata(signingKey.kid(), created, expires);
            SignatureBase signatureBase = signatureBase(request.method(), request.uri(), headers, metadata);
            byte[] signature = signatureBase.sign(new JoseHttpSigner(signingKey.privateJwk(), JWSAlgorithm.ES256));

            headers.put(SIGNATURE_INPUT, new SignatureInputField(Map.of(SIGNATURE_LABEL, metadata)).serialize());
            headers.put(SIGNATURE, new SignatureField(Map.of(SIGNATURE_LABEL, signature)).serialize());

            return new SignedRequest(request.body(), headers, signatureBase.serialize(), created, expires, signingKey.kid());
        } catch (SignatureException exception) {
            throw new SigningException("RFC 9421 request signing failed", exception);
        }
    }

    public boolean verify(SigningRequest request, Map<String, String> headers, ECKey publicKey, Instant receivedAt) {
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(headers, "headers must not be null");
        Objects.requireNonNull(publicKey, "publicKey must not be null");
        Objects.requireNonNull(receivedAt, "receivedAt must not be null");

        validateContentDigest(request.body(), requireHeader(headers, CONTENT_DIGEST));

        try {
            SignatureField signatureField = SignatureField.parse(requireHeader(headers, SIGNATURE));
            SignatureInputField signatureInputField = SignatureInputField.parse(requireHeader(headers, SIGNATURE_INPUT));
            SignatureEntry entry = signatureEntry(signatureField, signatureInputField);
            validateMetadata(entry.getMetadata(), publicKey, receivedAt);
            SignatureBase signatureBase = signatureBase(request.method(), request.uri(), headers, entry.getMetadata());
            return signatureBase.verify(new JoseHttpVerifier(publicKey, JWSAlgorithm.ES256), entry.getSignature());
        } catch (SignatureException | IllegalArgumentException exception) {
            throw new SigningException("RFC 9421 request verification failed", exception);
        }
    }

    public static String contentDigest(byte[] body) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(body == null ? new byte[0] : body);
            return "sha-256=:" + Base64.getEncoder().encodeToString(digest) + ":";
        } catch (NoSuchAlgorithmException exception) {
            throw new SigningException("SHA-256 digest algorithm is unavailable", exception);
        }
    }

    private SignatureMetadata metadata(String kid, Instant created, Instant expires) {
        SignatureMetadataParameters parameters = new SignatureMetadataParameters()
                .setCreated(created)
                .setExpires(expires)
                .setKeyid(kid)
                .setAlg(SIGNATURE_ALGORITHM);
        return new SignatureMetadata(COVERED_COMPONENTS, parameters);
    }

    private SignatureBase signatureBase(
            String method,
            URI uri,
            Map<String, String> headers,
            SignatureMetadata metadata
    ) throws SignatureException {
        ComponentValueProvider context = new ComponentValueProvider()
                .setMethod(method)
                .setTargetUri(uri)
                .setHeaders(headerValues(headers));
        return new SignatureBaseBuilder(context).build(metadata);
    }

    private Map<String, List<String>> headerValues(Map<String, String> headers) {
        Map<String, List<String>> values = new LinkedHashMap<>();
        headers.forEach((name, value) -> values.put(name, List.of(value)));
        return values;
    }

    private SignatureEntry signatureEntry(SignatureField signatureField, SignatureInputField signatureInputField) {
        Map<String, SignatureEntry> entries = SignatureEntry.scan(signatureField, signatureInputField);
        SignatureEntry entry = entries.get(SIGNATURE_LABEL);
        if (entry == null || entries.size() != 1) {
            throw new SigningException("Exactly one RFC 9421 signature labeled " + SIGNATURE_LABEL + " is required");
        }
        return entry;
    }

    private void validateMetadata(SignatureMetadata metadata, ECKey publicKey, Instant receivedAt) {
        if (!COVERED_COMPONENTS.equals(metadata)) {
            throw new SigningException("RFC 9421 covered components do not match the pinned UCP component set");
        }
        SignatureMetadataParameters parameters = metadata.getParameters();
        if (!SIGNATURE_ALGORITHM.equals(parameters.getAlg())) {
            throw new SigningException("RFC 9421 signature alg must be " + SIGNATURE_ALGORITHM);
        }
        if (!Objects.equals(publicKey.getKeyID(), parameters.getKeyid())) {
            throw new SigningException("RFC 9421 signature keyid does not match the verification key");
        }

        Instant created = parameters.getCreated();
        if (created == null) {
            throw new SigningException("RFC 9421 signature created parameter is required");
        }
        Instant expires = parameters.getExpires();
        if (created.isAfter(receivedAt.plus(properties.clockSkew()))) {
            throw new SigningException("RFC 9421 signature created time is in the future");
        }
        if (created.isBefore(receivedAt.minus(properties.signatureTtl()).minus(properties.clockSkew()))) {
            throw new SigningException("RFC 9421 signature created time is stale");
        }
        if (expires != null && expires.isBefore(receivedAt.minus(properties.clockSkew()))) {
            throw new SigningException("RFC 9421 signature has expired");
        }
        if (expires != null && expires.isAfter(created.plus(properties.signatureTtl()).plus(properties.clockSkew()))) {
            throw new SigningException("RFC 9421 signature expires beyond the allowed TTL");
        }
    }

    private void validateContentDigest(byte[] body, String contentDigest) {
        if (!contentDigest(body).equals(contentDigest)) {
            throw new SigningException("Content-Digest does not match the actual request body bytes");
        }
    }

    private String requireHeader(Map<String, String> headers, String headerName) {
        return headers.entrySet().stream()
                .filter(entry -> entry.getKey().equalsIgnoreCase(headerName))
                .map(Map.Entry::getValue)
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElseThrow(() -> new SigningException("Missing required RFC 9421 header " + headerName));
    }

    public record SigningRequest(
            String method,
            URI uri,
            String contentType,
            String idempotencyKey,
            String requestId,
            byte[] body
    ) {

        public SigningRequest {
            method = requireText(method, "method").toUpperCase(Locale.ROOT);
            Objects.requireNonNull(uri, "uri must not be null");
            if (!uri.isAbsolute()) {
                throw new IllegalArgumentException("uri must be absolute");
            }
            contentType = requireText(contentType, "contentType");
            idempotencyKey = requireText(idempotencyKey, "idempotencyKey");
            requestId = requireText(requestId, "requestId");
            body = body == null ? new byte[0] : Arrays.copyOf(body, body.length);
        }
    }

    public record SignedRequest(
            byte[] body,
            Map<String, String> headers,
            String signatureBase,
            Instant created,
            Instant expires,
            String keyId
    ) {

        public SignedRequest {
            body = body == null ? new byte[0] : Arrays.copyOf(body, body.length);
            headers = Collections.unmodifiableMap(new LinkedHashMap<>(headers));
            signatureBase = requireText(signatureBase, "signatureBase");
            Objects.requireNonNull(created, "created must not be null");
            Objects.requireNonNull(expires, "expires must not be null");
            keyId = requireText(keyId, "keyId");
        }
    }

    private static String requireText(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " must not be null");
        String trimmed = value.trim();
        if (trimmed.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return trimmed;
    }
}
