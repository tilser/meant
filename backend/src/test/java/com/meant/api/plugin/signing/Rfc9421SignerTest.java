package com.meant.api.plugin.signing;

import static org.assertj.core.api.Assertions.assertThat;

import com.authlete.hms.ComponentValueProvider;
import com.authlete.hms.HttpVerifier;
import com.authlete.hms.SignatureBase;
import com.authlete.hms.SignatureBaseBuilder;
import com.authlete.hms.SignatureEntry;
import com.authlete.hms.SignatureField;
import com.authlete.hms.SignatureInputField;
import com.authlete.hms.impl.JoseHttpVerifier;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.ECKey;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.SignatureException;
import java.text.ParseException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class Rfc9421SignerTest {

    private static final Instant NOW = Instant.parse("2026-06-29T12:00:00Z");
    private static final URI TARGET_URI = URI.create(
            "https://Merchant.Example:443/ucp/checkouts/co_123/complete?session=s1"
    );

    @Test
    void signsPinnedComponentsAndVerifiesRoundTrip() {
        Jcs jcs = new Jcs();
        byte[] body = jcs.canonicalizeToUtf8Bytes("{\"b\":2,\"a\":1}");
        Rfc9421Signer signer = signer();
        Rfc9421Signer.SigningRequest request = request(body);

        Rfc9421Signer.SignedRequest signedRequest = signer.sign(request);

        assertThat(signedRequest.body()).isEqualTo(body);
        assertThat(signedRequest.headers().get(Rfc9421Signer.CONTENT_DIGEST))
                .isEqualTo(Rfc9421Signer.contentDigest(body));
        assertThat(signedRequest.headers().get(Rfc9421Signer.SIGNATURE_INPUT))
                .isEqualTo("sig1=(\"@method\" \"@authority\" \"@path\" \"@query\" \"content-type\" "
                        + "\"content-digest\" \"idempotency-key\" \"request-id\");created=1782734400"
                        + ";expires=1782734700;keyid=\"transport-1\";alg=\"ES256\"");
        assertThat(signedRequest.signatureBase())
                .contains("\"@method\": POST")
                .contains("\"@authority\": merchant.example")
                .contains("\"@path\": /ucp/checkouts/co_123/complete")
                .contains("\"@query\": ?session=s1")
                .contains("\"content-type\": application/json")
                .contains("\"idempotency-key\": idem-1")
                .contains("\"request-id\": req-1");
        Rfc9421Signer.VerificationResult result = signer.verify(request, signedRequest.headers(), publicKey(), NOW);
        assertThat(result.valid()).isTrue();
        assertThat(result.reason()).isNull();
    }

    @Test
    void alteredCoveredHeaderIsRejected() {
        Rfc9421Signer signer = signer();
        Rfc9421Signer.SigningRequest request = request("{\"a\":1}".getBytes(StandardCharsets.UTF_8));
        Rfc9421Signer.SignedRequest signedRequest = signer.sign(request);
        Map<String, String> alteredHeaders = new LinkedHashMap<>(signedRequest.headers());
        alteredHeaders.put(Rfc9421Signer.IDEMPOTENCY_KEY, "idem-2");

        Rfc9421Signer.VerificationResult result = signer.verify(request, alteredHeaders, publicKey(), NOW);

        assertThat(result.valid()).isFalse();
        assertThat(result.reason()).contains("cryptographic verification failed");
    }

    @Test
    void alteredBodyBytesReturnInvalidVerificationResult() {
        Rfc9421Signer signer = signer();
        Rfc9421Signer.SigningRequest request = request("{\"a\":1}".getBytes(StandardCharsets.UTF_8));
        Rfc9421Signer.SignedRequest signedRequest = signer.sign(request);
        Rfc9421Signer.SigningRequest alteredBodyRequest = request("{\"a\":2}".getBytes(StandardCharsets.UTF_8));

        Rfc9421Signer.VerificationResult result = signer.verify(
                alteredBodyRequest,
                signedRequest.headers(),
                publicKey(),
                NOW
        );

        assertThat(result.valid()).isFalse();
        assertThat(result.reason()).contains("Content-Digest");
    }

    @Test
    void verificationValidationFailuresReturnInvalidResults() {
        Rfc9421Signer signer = signer();
        Rfc9421Signer.SigningRequest request = request("{\"a\":1}".getBytes(StandardCharsets.UTF_8));
        Rfc9421Signer.SignedRequest signedRequest = signer.sign(request);

        Map<String, String> wrongKidHeaders = replaceSignatureInput(
                signedRequest.headers(),
                "keyid=\"transport-1\"",
                "keyid=\"other\""
        );
        Rfc9421Signer.VerificationResult wrongKidResult = signer.verify(request, wrongKidHeaders, publicKey(), NOW);
        assertThat(wrongKidResult.valid()).isFalse();
        assertThat(wrongKidResult.reason()).contains("keyid");

        Map<String, String> wrongAlgHeaders = replaceSignatureInput(
                signedRequest.headers(),
                "alg=\"ES256\"",
                "alg=\"RS256\""
        );
        Rfc9421Signer.VerificationResult wrongAlgResult = signer.verify(request, wrongAlgHeaders, publicKey(), NOW);
        assertThat(wrongAlgResult.valid()).isFalse();
        assertThat(wrongAlgResult.reason()).contains("alg");

        Rfc9421Signer.VerificationResult staleResult = signer.verify(
                request,
                signedRequest.headers(),
                publicKey(),
                NOW.plus(Duration.ofMinutes(6))
        );
        assertThat(staleResult.valid()).isFalse();
        assertThat(staleResult.reason()).contains("stale");

        Rfc9421Signer.VerificationResult futureResult = signer.verify(
                request,
                signedRequest.headers(),
                publicKey(),
                NOW.minus(Duration.ofMinutes(1))
        );
        assertThat(futureResult.valid()).isFalse();
        assertThat(futureResult.reason()).contains("future");
    }

    @Test
    void verifyIgnoresHeadersWithNullValues() {
        Rfc9421Signer signer = signer();
        Rfc9421Signer.SigningRequest request = request("{\"a\":1}".getBytes(StandardCharsets.UTF_8));
        Rfc9421Signer.SignedRequest signedRequest = signer.sign(request);
        Map<String, String> headersWithNull = new LinkedHashMap<>(signedRequest.headers());
        headersWithNull.put("X-Optional", null);

        Rfc9421Signer.VerificationResult result = signer.verify(request, headersWithNull, publicKey(), NOW);

        assertThat(result.valid()).isTrue();
        assertThat(result.reason()).isNull();
    }

    @Test
    void rejectsUnexpectedCoveredComponentsWithInvalidResult() {
        Rfc9421Signer signer = signer();
        Rfc9421Signer.SigningRequest request = request("{\"a\":1}".getBytes(StandardCharsets.UTF_8));
        Rfc9421Signer.SignedRequest signedRequest = signer.sign(request);
        Map<String, String> wrongComponentsHeaders = replaceSignatureInput(
                signedRequest.headers(),
                "\"request-id\"",
                "\"x-request-id\""
        );

        Rfc9421Signer.VerificationResult result = signer.verify(request, wrongComponentsHeaders, publicKey(), NOW);

        assertThat(result.valid()).isFalse();
        assertThat(result.reason()).contains("covered components");
    }

    @Test
    void authleteVerifiesRfc9421AppendixEcdsaP256KnownAnswerVector() throws Exception {
        SignatureField signatureField = SignatureField.parse(
                "sig-b24=:wNmSUAhwb5LxtOtOpNa6W5xj067m5hFrj0XQ4fvpaCLx0NK"
                        + "ocgPquLgyahnzDnDAUy5eCdlYUEkLIj+32oiasw==:"
        );
        SignatureInputField signatureInputField = SignatureInputField.parse(
                "sig-b24=(\"@status\" \"content-type\" \"content-digest\" \"content-length\");created=1618884473"
                        + ";keyid=\"test-key-ecc-p256\""
        );
        SignatureEntry signatureEntry = SignatureEntry.scan(signatureField, signatureInputField).get("sig-b24");
        SignatureBase signatureBase = new SignatureBaseBuilder(new Rfc9421ResponseContext())
                .build(signatureEntry.getMetadata());
        HttpVerifier verifier = new JoseHttpVerifier(rfcAppendixPublicKey(), JWSAlgorithm.ES256);

        assertThat(signatureBase.verify(verifier, signatureEntry.getSignature())).isTrue();
    }

    private Rfc9421Signer signer() {
        SigningKeyProperties properties = new SigningKeyProperties(
                Duration.ofMinutes(5),
                Duration.ofSeconds(30),
                List.of(new SigningKeyProperties.Key(
                        "transport-1",
                        SigningKeyPurpose.TRANSPORT,
                        SigningKeyStatus.ACTIVE,
                        AppManagedSigningKeyProviderTest.privateJwk("transport-1").toCharArray(),
                        null,
                        null
                ))
        );
        AppManagedSigningKeyProvider keyProvider = new AppManagedSigningKeyProvider(
                properties,
                new ObjectMapper(),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
        return new Rfc9421Signer(keyProvider, properties, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private Rfc9421Signer.SigningRequest request(byte[] body) {
        return new Rfc9421Signer.SigningRequest(
                "post",
                TARGET_URI,
                "application/json",
                "idem-1",
                "req-1",
                body
        );
    }

    private ECKey publicKey() {
        return providerPublicKey("transport-1");
    }

    private ECKey providerPublicKey(String kid) {
        SigningKeyProperties properties = new SigningKeyProperties(
                Duration.ofMinutes(5),
                Duration.ofSeconds(30),
                List.of(new SigningKeyProperties.Key(
                        kid,
                        SigningKeyPurpose.TRANSPORT,
                        SigningKeyStatus.ACTIVE,
                        AppManagedSigningKeyProviderTest.privateJwk(kid).toCharArray(),
                        null,
                        null
                ))
        );
        return new AppManagedSigningKeyProvider(
                properties,
                new ObjectMapper(),
                Clock.fixed(NOW, ZoneOffset.UTC)
        ).activePrivateKey(SigningKeyPurpose.TRANSPORT).publicJwk();
    }

    private Map<String, String> replaceSignatureInput(Map<String, String> headers, String target, String replacement) {
        Map<String, String> updatedHeaders = new LinkedHashMap<>(headers);
        updatedHeaders.put(
                Rfc9421Signer.SIGNATURE_INPUT,
                updatedHeaders.get(Rfc9421Signer.SIGNATURE_INPUT).replace(target, replacement)
        );
        return updatedHeaders;
    }

    private ECKey rfcAppendixPublicKey() throws ParseException {
        return ECKey.parse("""
                {"kty":"EC","alg":"ES256","crv":"P-256","kid":"test-key-ecc-p256","d":"UpuF81l-kOxbjf7T4mNSv0r5tN67Gim7rnf6EFpcYDs","x":"qIVYZVLCrPZHGHjP17CTW0_-D9Lfw0EkjqF7xB4FivA","y":"Mc4nN9LTDOBhfoUeg8Ye9WedFRhnZXZJA12Qp0zZ6F0"}
                """).toPublicJWK();
    }

    private static final class Rfc9421ResponseContext extends ComponentValueProvider {

        Rfc9421ResponseContext() {
            setStatus(200);
            setHeaders(headers());
        }

        private static Map<String, List<String>> headers() {
            Map<String, List<String>> headers = new LinkedHashMap<>();
            headers.put("Date", List.of("Tue, 20 Apr 2021 02:07:56 GMT"));
            headers.put("Content-Type", List.of("application/json"));
            headers.put(
                    "Content-Digest",
                    List.of("sha-512=:mEWXIS7MaLRuGgxOBdODa3xqM1XdEvxoYhvlCFJ41QJgJc4GTsPp29l5oGX69wWdXymyU0rjJuahq4l5aGgfLQ==:")
            );
            headers.put("Content-Length", List.of("23"));
            return headers;
        }
    }
}
