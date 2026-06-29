package com.meant.api.plugin.signing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jose.util.Base64URL;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

class Ap2MandateServiceTest {

    private static final Instant NOW = Instant.parse("2026-06-29T12:00:00Z");
    private static final String MERCHANT_KID = "merchant-ap2-1";
    private static final String MERCHANT_ISSUER = "https://merchant.example/ap2";
    private static final String MERCHANT_ID = "merchant-1";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Jcs jcs = new Jcs();

    private ECKey merchantKey;
    private ECKey issuerKey;
    private ECKey holderKey;
    private Ap2MandateService service;

    @BeforeEach
    void setUp() throws JOSEException {
        merchantKey = key(MERCHANT_KID);
        issuerKey = key("ap2-issuer-1");
        holderKey = key("holder-1");
        service = new Ap2MandateService(
                signingKeyProvider(issuerKey, holderKey),
                jcs,
                objectMapper,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void verifiesDetachedMerchantAuthorizationOverCheckoutMinusTopLevelAp2() throws Exception {
        String provisionalCheckout = checkoutJson("pending");
        String merchantAuthorization = merchantAuthorization(provisionalCheckout, merchantKey, MERCHANT_KID);
        String checkout = checkoutJson(merchantAuthorization);

        Ap2MandateService.MerchantAuthorizationVerification verification = service.verifyMerchantAuthorization(
                verificationRequest(checkout, merchantAuthorization, merchantKey.toPublicJWK(), MERCHANT_KID)
        );

        assertThat(verification.kid()).isEqualTo(MERCHANT_KID);
        assertThat(verification.issuer()).isEqualTo(MERCHANT_ISSUER);
        assertThat(verification.merchantId()).isEqualTo(MERCHANT_ID);
        assertThat(verification.expiresAt()).isEqualTo(NOW.plusSeconds(300));
        assertThat(verification.canonicalCheckoutMinusAp2())
                .contains("\"nested\":{\"ap2\":\"kept\"}")
                .doesNotContain("\"merchant_authorization\"");
    }

    @Test
    void rejectsTamperedCheckoutAndWrongMerchantHeaders() throws Exception {
        String provisionalCheckout = checkoutJson("pending");
        String merchantAuthorization = merchantAuthorization(provisionalCheckout, merchantKey, MERCHANT_KID);
        String checkout = checkoutJson(merchantAuthorization);
        String tamperedCheckout = checkout.replace("\"1999\"", "\"2999\"");

        assertThatThrownBy(() -> service.verifyMerchantAuthorization(
                verificationRequest(tamperedCheckout, merchantAuthorization, merchantKey.toPublicJWK(), MERCHANT_KID)
        ))
                .isInstanceOf(Ap2MandateException.class)
                .hasMessageContaining("signature");

        assertThatThrownBy(() -> service.verifyMerchantAuthorization(
                verificationRequest(checkout, merchantAuthorization, merchantKey.toPublicJWK(), "other-kid")
        ))
                .isInstanceOf(Ap2MandateException.class)
                .hasMessageContaining("kid");
    }

    @Test
    void rejectsWrongAlgorithmAndWrongVerificationKey() throws Exception {
        String provisionalCheckout = checkoutJson("pending");
        String merchantAuthorization = merchantAuthorization(provisionalCheckout, merchantKey, MERCHANT_KID);
        String checkout = checkoutJson(merchantAuthorization);
        String wrongAlgAuthorization = replaceHeader(merchantAuthorization, Map.of("alg", "RS256"));
        ECKey wrongKey = key(MERCHANT_KID);

        assertThatThrownBy(() -> service.verifyMerchantAuthorization(
                verificationRequest(checkout, wrongAlgAuthorization, merchantKey.toPublicJWK(), MERCHANT_KID)
        ))
                .isInstanceOf(Ap2MandateException.class)
                .hasMessageContaining("alg");

        assertThatThrownBy(() -> service.verifyMerchantAuthorization(
                verificationRequest(checkout, merchantAuthorization, wrongKey.toPublicJWK(), MERCHANT_KID)
        ))
                .isInstanceOf(Ap2MandateException.class)
                .hasMessageContaining("signature");
    }

    @Test
    void buildsAndVerifiesSdJwtKbMandateWithNestedMerchantAuthorizationAndSpendScope() throws Exception {
        String provisionalCheckout = checkoutJson("pending");
        String merchantAuthorization = merchantAuthorization(provisionalCheckout, merchantKey, MERCHANT_KID);
        String checkout = checkoutJson(merchantAuthorization);

        Ap2MandateService.MandateResult result = service.buildCheckoutMandate(new Ap2MandateService.BuildMandateCommand(
                checkout,
                merchantAuthorization,
                merchantKey.toPublicJWK(),
                MERCHANT_KID,
                MERCHANT_ISSUER,
                MERCHANT_ID,
                "co_123",
                "user-1",
                "https://agent.example",
                "https://merchant.example/ucp",
                "nonce-1",
                1999L,
                "USD",
                NOW.plusSeconds(300)
        ));

        Ap2MandateService.VerifiedMandate verified = service.verifyCheckoutMandate(
                new Ap2MandateService.VerifyMandateCommand(
                        result.checkoutMandate(),
                        issuerKey.toPublicJWK(),
                        holderKey.toPublicJWK(),
                        "https://merchant.example/ucp",
                        "nonce-1",
                        "co_123",
                        MERCHANT_ID,
                        "USD",
                        NOW
                )
        );

        assertThat(result.spendScope().maxAmountMinor()).isEqualTo(1999L);
        assertThat(result.checkoutMandate()).contains("~");
        assertThat(verified.issuer()).isEqualTo("https://agent.example");
        assertThat(verified.subject()).isEqualTo("user-1");
        assertThat(verified.disclosedClaims()).containsKey("checkout");
        assertThat(verified.disclosedClaims()).containsEntry("merchant_authorization", merchantAuthorization);
        assertThat(verified.disclosedClaims().get("checkout").toString()).contains("merchant_authorization");
        assertThat(verified.disclosedClaims().get("spend_scope").toString())
                .contains("max_amount_minor=1999")
                .contains("comparison=<=");
    }

    @Test
    void rejectsExpiredMandateKeyBinding() throws Exception {
        String provisionalCheckout = checkoutJson("pending");
        String merchantAuthorization = merchantAuthorization(provisionalCheckout, merchantKey, MERCHANT_KID);
        String checkout = checkoutJson(merchantAuthorization);
        Ap2MandateService.MandateResult result = service.buildCheckoutMandate(new Ap2MandateService.BuildMandateCommand(
                checkout,
                merchantAuthorization,
                merchantKey.toPublicJWK(),
                MERCHANT_KID,
                MERCHANT_ISSUER,
                MERCHANT_ID,
                "co_123",
                "user-1",
                "https://agent.example",
                "https://merchant.example/ucp",
                "nonce-1",
                1999L,
                "USD",
                NOW.plusSeconds(60)
        ));

        assertThatThrownBy(() -> service.verifyCheckoutMandate(new Ap2MandateService.VerifyMandateCommand(
                result.checkoutMandate(),
                issuerKey.toPublicJWK(),
                holderKey.toPublicJWK(),
                "https://merchant.example/ucp",
                "nonce-1",
                "co_123",
                MERCHANT_ID,
                "USD",
                NOW.plusSeconds(61)
        )))
                .isInstanceOf(Ap2MandateException.class)
                .hasMessageContaining("expired");
    }

    private Ap2MandateService.MerchantAuthorizationVerificationRequest verificationRequest(
            String checkout,
            String merchantAuthorization,
            ECKey publicKey,
            String expectedKid
    ) {
        return new Ap2MandateService.MerchantAuthorizationVerificationRequest(
                checkout,
                merchantAuthorization,
                publicKey,
                expectedKid,
                MERCHANT_ISSUER,
                MERCHANT_ID,
                NOW
        );
    }

    private String merchantAuthorization(String checkoutJson, ECKey key, String kid) throws Exception {
        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.ES256)
                .keyID(kid)
                .customParam("iss", MERCHANT_ISSUER)
                .customParam("merchant_id", MERCHANT_ID)
                .customParam("exp", NOW.plusSeconds(300).getEpochSecond())
                .build();
        JWSObject jws = new JWSObject(header, new Payload(service.checkoutMinusAp2CanonicalBytes(checkoutJson)));
        jws.sign(new ECDSASigner(key));
        return jws.serialize(true);
    }

    private String checkoutJson(String merchantAuthorization) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "id", "co_123",
                "merchant_id", MERCHANT_ID,
                "total_amount", Map.of("amount_minor", "1999", "currency", "USD"),
                "nested", Map.of("ap2", "kept"),
                "ap2", Map.of("merchant_authorization", merchantAuthorization)
        ));
    }

    private String replaceHeader(String compactJws, Map<String, Object> replacements) throws Exception {
        String[] parts = compactJws.split("\\.", -1);
        Map<String, Object> header = objectMapper.readValue(
                Base64URL.from(parts[0]).decode(),
                new TypeReference<Map<String, Object>>() {
                }
        );
        header.putAll(replacements);
        parts[0] = Base64URL.encode(objectMapper.writeValueAsBytes(header)).toString();
        return String.join(".", parts);
    }

    private ECKey key(String kid) throws JOSEException {
        ECKey generated = new ECKeyGenerator(Curve.P_256).generate();
        return new ECKey.Builder(generated)
                .keyID(kid)
                .algorithm(JWSAlgorithm.ES256)
                .keyUse(KeyUse.SIGNATURE)
                .build();
    }

    private SigningKeyProvider signingKeyProvider(ECKey issuerKey, ECKey holderKey) {
        SigningKey issuer = signingKey(issuerKey, SigningKeyPurpose.AP2_ISSUER);
        SigningKey holder = signingKey(holderKey, SigningKeyPurpose.SD_JWT_HOLDER);
        return new SigningKeyProvider() {
            @Override
            public SigningKey activePrivateKey(SigningKeyPurpose purpose) {
                return purpose == SigningKeyPurpose.AP2_ISSUER ? issuer : holder;
            }

            @Override
            public Optional<SigningKey> key(String kid, SigningKeyPurpose purpose) {
                SigningKey key = purpose == SigningKeyPurpose.AP2_ISSUER ? issuer : holder;
                return key.kid().equals(kid) ? Optional.of(key) : Optional.empty();
            }

            @Override
            public List<PublicSigningKey> publicKeys() {
                return List.of(issuer.toPublicSigningKey(), holder.toPublicSigningKey());
            }
        };
    }

    private SigningKey signingKey(ECKey key, SigningKeyPurpose purpose) {
        return new SigningKey(
                key.getKeyID(),
                purpose,
                SigningKeyStatus.ACTIVE,
                key,
                key.toPublicJWK(),
                null
        );
    }
}
