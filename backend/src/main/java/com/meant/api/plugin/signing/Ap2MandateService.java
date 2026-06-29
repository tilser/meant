package com.meant.api.plugin.signing;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.util.Base64URL;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.ParseException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@Service
@Validated
public class Ap2MandateService {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<List<Object>> LIST_TYPE = new TypeReference<>() {
    };
    private static final JOSEObjectType SD_JWT_TYPE = new JOSEObjectType("vc+sd-jwt");
    private static final JOSEObjectType KB_JWT_TYPE = new JOSEObjectType("kb+jwt");
    private static final String SHA_256 = "SHA-256";
    private static final String DISCLOSURE_ALGORITHM = "sha-256";

    private final SigningKeyProvider signingKeyProvider;
    private final Jcs jcs;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Autowired
    public Ap2MandateService(SigningKeyProvider signingKeyProvider, Jcs jcs, ObjectMapper objectMapper) {
        this(signingKeyProvider, jcs, objectMapper, Clock.systemUTC());
    }

    Ap2MandateService(
            SigningKeyProvider signingKeyProvider,
            Jcs jcs,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.signingKeyProvider = Objects.requireNonNull(signingKeyProvider, "signingKeyProvider must not be null");
        this.jcs = Objects.requireNonNull(jcs, "jcs must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    public byte[] checkoutMinusAp2CanonicalBytes(String checkoutJson) {
        try {
            JsonNode checkout = objectMapper.readTree(requireText(checkoutJson, "checkoutJson"));
            if (!checkout.isObject()) {
                throw new Ap2MandateException("Checkout payload must be a JSON object");
            }
            ObjectNode checkoutMinusAp2 = checkout.deepCopy().asObject();
            checkoutMinusAp2.remove("ap2");
            return jcs.canonicalizeToUtf8Bytes(objectMapper.writeValueAsBytes(checkoutMinusAp2));
        } catch (JacksonException exception) {
            throw new Ap2MandateException("Checkout payload could not be parsed", exception);
        }
    }

    public String checkoutMinusAp2CanonicalJson(String checkoutJson) {
        return new String(checkoutMinusAp2CanonicalBytes(checkoutJson), StandardCharsets.UTF_8);
    }

    public MerchantAuthorizationVerification verifyMerchantAuthorization(
            @NotNull @Valid MerchantAuthorizationVerificationRequest request
    ) {
        Objects.requireNonNull(request, "request must not be null");
        try {
            requireDetachedCompactJws(request.merchantAuthorizationJws());
            Payload detachedPayload = new Payload(checkoutMinusAp2CanonicalBytes(request.checkoutJson()));
            JWSObject merchantAuthorization = JWSObject.parse(request.merchantAuthorizationJws(), detachedPayload);
            JWSHeader header = merchantAuthorization.getHeader();
            requirePinnedMerchantHeader(header, request);
            if (!merchantAuthorization.verify(new ECDSAVerifier(request.merchantPublicKey()))) {
                throw new Ap2MandateException("Merchant AP2 authorization signature verification failed");
            }
            Instant expiresAt = headerInstant(header, "exp");
            Instant receivedAt = request.receivedAt() == null ? clock.instant() : request.receivedAt();
            if (!expiresAt.isAfter(receivedAt)) {
                throw new Ap2MandateException("Merchant AP2 authorization has expired");
            }
            return new MerchantAuthorizationVerification(
                    header.getKeyID(),
                    stringHeader(header, "iss"),
                    stringHeader(header, "merchant_id"),
                    expiresAt,
                    checkoutMinusAp2CanonicalJson(request.checkoutJson())
            );
        } catch (ParseException | JOSEException exception) {
            throw new Ap2MandateException("Merchant AP2 authorization could not be verified", exception);
        }
    }

    public MandateResult buildCheckoutMandate(@NotNull @Valid BuildMandateCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        MerchantAuthorizationVerification merchantAuthorization = verifyMerchantAuthorization(
                command.merchantAuthorizationVerificationRequest()
        );
        Map<String, Object> checkout = checkoutMap(command.checkoutJson());
        requireFullCheckoutBinding(checkout, command.merchantAuthorizationJws());

        SigningKey issuerKey = signingKeyProvider.activePrivateKey(SigningKeyPurpose.AP2_ISSUER);
        SigningKey holderKey = signingKeyProvider.activePrivateKey(SigningKeyPurpose.SD_JWT_HOLDER);
        Instant issuedAt = clock.instant();
        String holderThumbprint = thumbprint(holderKey.publicJwk());
        SpendScope spendScope = new SpendScope(
                command.checkoutId(),
                command.merchantId(),
                command.maxAmountMinor(),
                normalizedCurrency(command.currency())
        );

        List<Disclosure> disclosures = List.of(
                disclosure("checkout", checkout),
                disclosure("merchant_authorization", command.merchantAuthorizationJws()),
                disclosure("spend_scope", spendScope.toClaim())
        );
        String sdJwt = signIssuerJwt(command, issuerKey, holderThumbprint, issuedAt, disclosures);
        List<String> encodedDisclosures = disclosures.stream().map(Disclosure::encoded).toList();
        String kbJwt = signKbJwt(command, holderKey, holderThumbprint, issuedAt, sdJwt, encodedDisclosures);
        String checkoutMandate = String.join("~", mandateParts(sdJwt, encodedDisclosures, kbJwt));
        return new MandateResult(
                checkoutMandate,
                sdJwt,
                encodedDisclosures,
                kbJwt,
                merchantAuthorization,
                spendScope,
                command.expiresAt()
        );
    }

    public VerifiedMandate verifyCheckoutMandate(@NotNull @Valid VerifyMandateCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        try {
            List<String> parts = List.of(command.checkoutMandate().split("~", -1));
            if (parts.size() < 3 || parts.stream().anyMatch(String::isBlank)) {
                throw new Ap2MandateException("AP2 checkout mandate must contain SD-JWT, disclosures, and KB-JWT");
            }

            String sdJwtValue = parts.getFirst();
            List<String> disclosures = parts.subList(1, parts.size() - 1);
            String kbJwtValue = parts.getLast();
            SignedJWT sdJwt = SignedJWT.parse(sdJwtValue);
            SignedJWT kbJwt = SignedJWT.parse(kbJwtValue);
            verifyJwtSignature(sdJwt, command.issuerPublicKey(), "AP2 mandate issuer");
            verifyJwtSignature(kbJwt, command.holderPublicKey(), "AP2 mandate holder key binding");

            JWTClaimsSet sdClaims = sdJwt.getJWTClaimsSet();
            JWTClaimsSet kbClaims = kbJwt.getJWTClaimsSet();
            Instant receivedAt = command.receivedAt() == null ? clock.instant() : command.receivedAt();
            validateExpiration(sdClaims, receivedAt, "AP2 mandate");
            validateExpiration(kbClaims, receivedAt, "AP2 mandate key binding");
            requireEquals(command.audience(), firstAudience(sdClaims), "AP2 mandate audience");
            requireEquals(command.audience(), firstAudience(kbClaims), "AP2 mandate key binding audience");
            requireEquals(command.nonce(), stringClaim(kbClaims, "nonce"), "AP2 mandate key binding nonce");

            Map<String, Object> disclosedClaims = validateDisclosures(sdClaims, disclosures);
            String holderThumbprint = thumbprint(command.holderPublicKey());
            requireEquals(holderThumbprint, confirmationThumbprint(sdClaims), "AP2 mandate holder confirmation");
            requireEquals(holderThumbprint, confirmationThumbprint(kbClaims), "AP2 mandate key binding confirmation");
            requireEquals(
                    sdHash(sdJwtValue, disclosures),
                    stringClaim(kbClaims, "sd_hash"),
                    "AP2 mandate key binding sd_hash"
            );

            Object spendScopeClaim = disclosedClaims.get("spend_scope");
            Map<String, Object> spendScope = spendScopeClaim instanceof Map<?, ?> map
                    ? stringKeyMap(map)
                    : Map.of();
            requireEquals(command.checkoutId(), scalarString(spendScope.get("checkout_id")), "AP2 mandate checkout id");
            requireEquals(command.merchantId(), scalarString(spendScope.get("merchant_id")), "AP2 mandate merchant id");
            requireEquals(
                    command.currency(),
                    scalarString(spendScope.get("currency")),
                    "AP2 mandate spend-scope currency"
            );

            return new VerifiedMandate(
                    sdClaims.getIssuer(),
                    sdClaims.getSubject(),
                    firstAudience(sdClaims),
                    sdClaims.getJWTID(),
                    disclosedClaims,
                    kbClaims.getJWTID(),
                    receivedAt
            );
        } catch (ParseException | JOSEException exception) {
            throw new Ap2MandateException("AP2 checkout mandate could not be verified", exception);
        }
    }

    private void requirePinnedMerchantHeader(
            JWSHeader header,
            MerchantAuthorizationVerificationRequest request
    ) {
        requireEquals(JWSAlgorithm.ES256, header.getAlgorithm(), "Merchant AP2 authorization alg");
        requireEquals(request.expectedKid(), header.getKeyID(), "Merchant AP2 authorization kid");
        requireEquals(request.expectedKid(), request.merchantPublicKey().getKeyID(), "Merchant AP2 public key kid");
        requireEquals(request.expectedIssuer(), stringHeader(header, "iss"), "Merchant AP2 authorization issuer");
        requireEquals(
                request.expectedMerchantId(),
                stringHeader(header, "merchant_id"),
                "Merchant AP2 authorization merchant identity"
        );
        headerInstant(header, "exp");
    }

    private void requireDetachedCompactJws(String value) {
        String[] parts = requireText(value, "merchantAuthorizationJws").split("\\.", -1);
        if (parts.length != 3 || !parts[1].isEmpty()) {
            throw new Ap2MandateException("Merchant AP2 authorization must be a detached compact JWS");
        }
    }

    private void requireFullCheckoutBinding(Map<String, Object> checkout, String merchantAuthorizationJws) {
        Object ap2 = checkout.get("ap2");
        if (!(ap2 instanceof Map<?, ?> ap2Map)) {
            throw new Ap2MandateException("Checkout payload must contain top-level ap2 metadata");
        }
        String checkoutMerchantAuthorization = scalarString(stringKeyMap(ap2Map).get("merchant_authorization"));
        if (checkoutMerchantAuthorization == null) {
            checkoutMerchantAuthorization = scalarString(stringKeyMap(ap2Map).get("merchantAuthorization"));
        }
        requireEquals(
                merchantAuthorizationJws,
                checkoutMerchantAuthorization,
                "Checkout AP2 merchant_authorization"
        );
    }

    private String signIssuerJwt(
            BuildMandateCommand command,
            SigningKey issuerKey,
            String holderThumbprint,
            Instant issuedAt,
            List<Disclosure> disclosures
    ) {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(command.agentIssuer())
                .subject(command.userId())
                .audience(command.audience())
                .issueTime(Date.from(issuedAt))
                .expirationTime(Date.from(command.expiresAt()))
                .jwtID(UUID.randomUUID().toString())
                .claim("checkout_id", command.checkoutId())
                .claim("merchant_id", command.merchantId())
                .claim("checkout_hash", sha256Base64Url(jcs.canonicalizeToUtf8Bytes(command.checkoutJson())))
                .claim("_sd_alg", DISCLOSURE_ALGORITHM)
                .claim("_sd", disclosures.stream().map(Disclosure::digest).toList())
                .claim("cnf", Map.of("jkt", holderThumbprint))
                .build();
        return signJwt(issuerKey, SD_JWT_TYPE, claims);
    }

    private String signKbJwt(
            BuildMandateCommand command,
            SigningKey holderKey,
            String holderThumbprint,
            Instant issuedAt,
            String sdJwt,
            List<String> disclosures
    ) {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .audience(command.audience())
                .issueTime(Date.from(issuedAt))
                .expirationTime(Date.from(command.expiresAt()))
                .jwtID(UUID.randomUUID().toString())
                .claim("nonce", command.nonce())
                .claim("sd_hash", sdHash(sdJwt, disclosures))
                .claim("cnf", Map.of("jkt", holderThumbprint))
                .build();
        return signJwt(holderKey, KB_JWT_TYPE, claims);
    }

    private String signJwt(SigningKey key, JOSEObjectType type, JWTClaimsSet claims) {
        try {
            SignedJWT jwt = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.ES256)
                            .keyID(key.kid())
                            .type(type)
                            .build(),
                    claims
            );
            jwt.sign(new ECDSASigner(key.privateJwk()));
            return jwt.serialize();
        } catch (JOSEException exception) {
            throw new Ap2MandateException("AP2 JWT signing failed", exception);
        }
    }

    private void verifyJwtSignature(SignedJWT jwt, ECKey publicKey, String context) throws JOSEException {
        if (!JWSAlgorithm.ES256.equals(jwt.getHeader().getAlgorithm())) {
            throw new Ap2MandateException(context + " must use ES256");
        }
        requireEquals(publicKey.getKeyID(), jwt.getHeader().getKeyID(), context + " kid");
        if (!jwt.verify(new ECDSAVerifier(publicKey))) {
            throw new Ap2MandateException(context + " signature verification failed");
        }
    }

    private Map<String, Object> validateDisclosures(JWTClaimsSet claims, List<String> encodedDisclosures) {
        Object sdClaim = claims.getClaim("_sd");
        List<String> expectedDigests = sdClaim instanceof Collection<?> collection
                ? collection.stream().map(Object::toString).toList()
                : List.of();
        if (expectedDigests.isEmpty()) {
            throw new Ap2MandateException("AP2 mandate does not contain disclosure digests");
        }

        Map<String, Object> disclosedClaims = new LinkedHashMap<>();
        for (String encodedDisclosure : encodedDisclosures) {
            String digest = disclosureDigest(encodedDisclosure);
            if (!expectedDigests.contains(digest)) {
                throw new Ap2MandateException("AP2 mandate disclosure digest did not match issuer claims");
            }
            List<Object> disclosure = disclosureValues(encodedDisclosure);
            if (disclosure.size() != 3) {
                throw new Ap2MandateException("AP2 mandate disclosure must have salt, claim name, and value");
            }
            String claimName = scalarString(disclosure.get(1));
            if (claimName == null) {
                throw new Ap2MandateException("AP2 mandate disclosure claim name is required");
            }
            disclosedClaims.put(claimName, disclosure.get(2));
        }
        return disclosedClaims;
    }

    private List<Object> disclosureValues(String encodedDisclosure) {
        try {
            return objectMapper.readValue(Base64URL.from(encodedDisclosure).decode(), LIST_TYPE);
        } catch (JacksonException exception) {
            throw new Ap2MandateException("AP2 mandate disclosure could not be decoded", exception);
        }
    }

    private Disclosure disclosure(String claimName, Object claimValue) {
        try {
            String salt = UUID.randomUUID().toString();
            String encoded = Base64URL.encode(objectMapper.writeValueAsBytes(List.of(salt, claimName, claimValue)))
                    .toString();
            return new Disclosure(claimName, encoded, disclosureDigest(encoded));
        } catch (JacksonException exception) {
            throw new Ap2MandateException("AP2 mandate disclosure could not be encoded", exception);
        }
    }

    private String sdHash(String sdJwt, List<String> disclosures) {
        String presentationBeforeKeyBinding = String.join("~", mandateParts(sdJwt, disclosures, null)) + "~";
        return sha256Base64Url(presentationBeforeKeyBinding.getBytes(StandardCharsets.US_ASCII));
    }

    private List<String> mandateParts(String sdJwt, List<String> disclosures, String kbJwt) {
        List<String> parts = new ArrayList<>();
        parts.add(sdJwt);
        parts.addAll(disclosures);
        if (kbJwt != null) {
            parts.add(kbJwt);
        }
        return parts;
    }

    private Map<String, Object> checkoutMap(String checkoutJson) {
        try {
            return objectMapper.readValue(requireText(checkoutJson, "checkoutJson"), MAP_TYPE);
        } catch (JacksonException exception) {
            throw new Ap2MandateException("Checkout payload could not be parsed", exception);
        }
    }

    private void validateExpiration(JWTClaimsSet claims, Instant receivedAt, String context) {
        Date expirationTime = claims.getExpirationTime();
        if (expirationTime == null || !expirationTime.toInstant().isAfter(receivedAt)) {
            throw new Ap2MandateException(context + " has expired");
        }
    }

    private String confirmationThumbprint(JWTClaimsSet claims) {
        Object cnf = claims.getClaim("cnf");
        if (!(cnf instanceof Map<?, ?> map)) {
            throw new Ap2MandateException("AP2 mandate holder confirmation is required");
        }
        return scalarString(stringKeyMap(map).get("jkt"));
    }

    private String firstAudience(JWTClaimsSet claims) {
        List<String> audience = claims.getAudience();
        return audience == null || audience.isEmpty() ? null : audience.getFirst();
    }

    private String stringClaim(JWTClaimsSet claims, String claimName) {
        return scalarString(claims.getClaim(claimName));
    }

    private String stringHeader(JWSHeader header, String name) {
        return scalarString(header.getCustomParam(name));
    }

    private Instant headerInstant(JWSHeader header, String name) {
        Object value = header.getCustomParam(name);
        if (value instanceof Number number) {
            return Instant.ofEpochSecond(number.longValue());
        }
        String stringValue = scalarString(value);
        if (stringValue == null) {
            throw new Ap2MandateException("Merchant AP2 authorization " + name + " header is required");
        }
        try {
            return Instant.ofEpochSecond(Long.parseLong(stringValue));
        } catch (NumberFormatException exception) {
            throw new Ap2MandateException("Merchant AP2 authorization " + name + " header must be epoch seconds");
        }
    }

    private String normalizedCurrency(String currency) {
        return requireText(currency, "currency").toUpperCase(java.util.Locale.ROOT);
    }

    private String thumbprint(ECKey key) {
        try {
            return key.toPublicJWK().computeThumbprint().toString();
        } catch (JOSEException exception) {
            throw new Ap2MandateException("AP2 holder key thumbprint could not be computed", exception);
        }
    }

    private String sha256Base64Url(byte[] value) {
        try {
            return Base64URL.encode(MessageDigest.getInstance(SHA_256).digest(value)).toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new Ap2MandateException("SHA-256 digest algorithm is unavailable", exception);
        }
    }

    private String disclosureDigest(String encodedDisclosure) {
        return sha256Base64Url(encodedDisclosure.getBytes(StandardCharsets.US_ASCII));
    }

    private void requireEquals(Object expected, Object actual, String field) {
        if (!Objects.equals(expected, actual)) {
            throw new Ap2MandateException(field + " mismatch");
        }
    }

    private String requireText(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " must not be null");
        String trimmed = value.trim();
        if (trimmed.isBlank()) {
            throw new Ap2MandateException(fieldName + " must not be blank");
        }
        return trimmed;
    }

    private String scalarString(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String string) {
            return string.isBlank() ? null : string.trim();
        }
        if (value instanceof Number || value instanceof Boolean || value instanceof Character) {
            return value.toString();
        }
        return null;
    }

    private Map<String, Object> stringKeyMap(Map<?, ?> source) {
        Map<String, Object> values = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            if (key != null) {
                values.put(key.toString(), value);
            }
        });
        return values;
    }

    public record MerchantAuthorizationVerificationRequest(
            @NotBlank String checkoutJson,
            @NotBlank String merchantAuthorizationJws,
            @NotNull ECKey merchantPublicKey,
            @NotBlank String expectedKid,
            @NotBlank String expectedIssuer,
            @NotBlank String expectedMerchantId,
            Instant receivedAt
    ) {
    }

    public record BuildMandateCommand(
            @NotBlank String checkoutJson,
            @NotBlank String merchantAuthorizationJws,
            @NotNull ECKey merchantPublicKey,
            @NotBlank String expectedMerchantAuthorizationKid,
            @NotBlank String merchantAuthorizationIssuer,
            @NotBlank String merchantId,
            @NotBlank String checkoutId,
            @NotBlank String userId,
            @NotBlank String agentIssuer,
            @NotBlank String audience,
            @NotBlank String nonce,
            @NotNull @Positive Long maxAmountMinor,
            @NotBlank String currency,
            @NotNull Instant expiresAt
    ) {

        private MerchantAuthorizationVerificationRequest merchantAuthorizationVerificationRequest() {
            return new MerchantAuthorizationVerificationRequest(
                    checkoutJson,
                    merchantAuthorizationJws,
                    merchantPublicKey,
                    expectedMerchantAuthorizationKid,
                    merchantAuthorizationIssuer,
                    merchantId,
                    null
            );
        }
    }

    public record VerifyMandateCommand(
            @NotBlank String checkoutMandate,
            @NotNull ECKey issuerPublicKey,
            @NotNull ECKey holderPublicKey,
            @NotBlank String audience,
            @NotBlank String nonce,
            @NotBlank String checkoutId,
            @NotBlank String merchantId,
            @NotBlank String currency,
            Instant receivedAt
    ) {
    }

    public record MerchantAuthorizationVerification(
            String kid,
            String issuer,
            String merchantId,
            Instant expiresAt,
            String canonicalCheckoutMinusAp2
    ) {
    }

    public record SpendScope(
            String checkoutId,
            String merchantId,
            Long maxAmountMinor,
            String currency
    ) {

        private Map<String, Object> toClaim() {
            Map<String, Object> claim = new LinkedHashMap<>();
            claim.put("checkout_id", checkoutId);
            claim.put("merchant_id", merchantId);
            claim.put("max_amount_minor", maxAmountMinor);
            claim.put("currency", currency);
            claim.put("comparison", "<=");
            return claim;
        }
    }

    public record MandateResult(
            String checkoutMandate,
            String sdJwt,
            List<String> disclosures,
            String kbJwt,
            MerchantAuthorizationVerification merchantAuthorization,
            SpendScope spendScope,
            Instant expiresAt
    ) {
    }

    public record VerifiedMandate(
            String issuer,
            String subject,
            String audience,
            String jwtId,
            Map<String, Object> disclosedClaims,
            String keyBindingJwtId,
            Instant verifiedAt
    ) {
    }

    private record Disclosure(String claimName, String encoded, String digest) {
    }
}
