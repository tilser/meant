package com.meant.api.module.checkout.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.meant.api.module.merchant.service.dto.MerchantCartProvider;
import com.meant.api.plugin.checkout.cancel.dto.CancelCheckoutRequest;
import com.meant.api.plugin.checkout.common.dto.UcpCheckoutResponse;
import com.meant.api.plugin.checkout.common.dto.UcpCheckoutToolResult;
import com.meant.api.module.checkout.entity.CheckoutCanaryOutcome;
import com.meant.api.module.checkout.entity.CheckoutCompletionState;
import com.meant.api.module.checkout.entity.CheckoutIdempotencyKey;
import com.meant.api.module.checkout.entity.CheckoutIdempotencyStatus;
import com.meant.api.module.checkout.exception.CheckoutSafetyException;
import com.meant.api.module.checkout.repository.CheckoutCompletionStateRepository;
import com.meant.api.module.checkout.repository.CheckoutIdempotencyKeyRepository;
import com.meant.api.module.checkout.service.CheckoutCanaryService.CanaryEventCommand;
import com.meant.api.module.checkout.service.CheckoutTotalsReconciler.ExpectedCheckout;
import com.meant.api.module.checkout.service.command.AuthorizeCheckoutCompletionCommand;
import com.meant.api.module.checkout.service.command.NativeCheckoutCancellationCommand;
import com.meant.api.module.checkout.service.command.NativeCheckoutCompletionCommand;
import com.meant.api.module.checkout.service.command.RecordIdempotencyResponseCommand;
import com.meant.api.module.checkout.service.command.ReserveIdempotencyKeyCommand;
import com.meant.api.module.checkout.service.command.StartCheckoutCompletionCommand;
import com.meant.api.module.checkout.service.dto.NativeCheckoutResult;
import com.meant.api.module.checkout.service.dto.NativeCheckoutStatus;
import com.meant.api.plugin.checkout.complete.CompleteCheckoutCapability;
import com.meant.api.plugin.checkout.complete.dto.CheckoutSignals;
import com.meant.api.plugin.checkout.complete.dto.CompleteCheckoutRequest;
import com.meant.api.plugin.checkout.extension.buyerconsent.dto.BuyerConsentArtifact;
import com.meant.api.plugin.checkout.extension.buyerconsent.dto.BuyerConsentShippingAddress;
import com.meant.api.plugin.checkout.get.dto.GetCheckoutRequest;
import com.meant.api.plugin.payment.common.dto.PaymentCredential;
import com.meant.api.plugin.payment.common.dto.PaymentInstrument;
import com.meant.api.plugin.payment.common.dto.PaymentScaLiability;
import com.meant.api.plugin.payment.common.dto.TokenPaymentCredentialDetails;
import com.meant.api.plugin.signing.Ap2MandateException;
import com.meant.api.plugin.signing.JsonWebKey;
import com.meant.api.plugin.signing.Jcs;
import com.meant.api.plugin.signing.PublicSigningKey;
import com.meant.api.plugin.signing.Rfc9421Signer;
import com.meant.api.plugin.signing.SigningException;
import com.meant.api.plugin.signing.SigningKey;
import com.meant.api.plugin.signing.SigningKeyProperties;
import com.meant.api.plugin.signing.SigningKeyProvider;
import com.meant.api.plugin.signing.SigningKeyPurpose;
import com.meant.api.plugin.signing.SigningKeyStatus;
import com.meant.api.plugin.support.UcpSession;
import com.nimbusds.jose.jwk.ECKey;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

class NativeCheckoutCompletionServiceTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID MERCHANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID CART_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final UUID CONSENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000004");
    private static final String CHECKOUT_ID = "co_123";
    private static final Instant NOW = Instant.parse("2026-06-30T10:00:00Z");

    private final ObjectMapper objectMapper = new ObjectMapper();
    private FakeDispatchService dispatchService;
    private FakeCompletionStateStore completionStateStore;
    private FakeIdempotencyKeyStore idempotencyKeyStore;
    private FakeCanaryService checkoutCanaryService;
    private NativeCheckoutCompletionService service;

    @BeforeEach
    void setUp() {
        Jcs jcs = new Jcs();
        dispatchService = new FakeDispatchService();
        completionStateStore = new FakeCompletionStateStore();
        idempotencyKeyStore = new FakeIdempotencyKeyStore();
        checkoutCanaryService = new FakeCanaryService();
        service = completionService(new CheckoutTotalsReconciler(objectMapper, jcs), jcs);
    }

    private NativeCheckoutCompletionService completionService(CheckoutTotalsReconciler totalsReconciler, Jcs jcs) {
        return new NativeCheckoutCompletionService(
                dispatchService,
                completionStateStore,
                idempotencyKeyStore,
                new FakeBuyerConsentService(consent(), objectMapper),
                totalsReconciler,
                new NativeCheckoutAp2MandateBuilder(null, objectMapper),
                new NativeCheckoutRequestSigner(new CompleteCheckoutCapability(objectMapper), signer(), jcs, objectMapper),
                new NativeCheckoutResultInterpreter(),
                new NativeCheckoutCanaryRecorder(checkoutCanaryService),
                new NativeCheckoutIdempotencyResponseRecorder(idempotencyKeyStore)
        );
    }

    @Test
    void completedResponseMarksStateAndStoresFinalOrderReference() {
        dispatchService.getResults.add(toolResult(openCheckoutJson()));
        dispatchService.completeResult = toolResult(completedCheckoutJson("order-123"));

        NativeCheckoutResult result = service.complete(provider(true), command(false), UcpSession.cart("cart-1", null, null));

        assertThat(result.status()).isEqualTo(NativeCheckoutStatus.COMPLETED);
        assertThat(result.orderRef()).isEqualTo("order-123");
        assertThat(completionStateStore.markCompletedCount).isEqualTo(1);
        assertThat(idempotencyKeyStore.recordCommands)
                .extracting(RecordIdempotencyResponseCommand::status)
                .contains(CheckoutIdempotencyStatus.COMPLETED);
    }

    @Test
    void lostResponseOnSuccessQueriesStatusBeforeAnyRetryAndDoesNotRepost() {
        dispatchService.getResults.add(toolResult(openCheckoutJson()));
        dispatchService.getResults.add(toolResult(completedCheckoutJson("order-456")));
        dispatchService.completeException = new RuntimeException("connection reset");

        NativeCheckoutResult result = service.complete(provider(true), command(false), UcpSession.cart("cart-1", null, null));

        assertThat(result.status()).isEqualTo(NativeCheckoutStatus.COMPLETED);
        assertThat(result.orderRef()).isEqualTo("order-456");
        assertThat(dispatchService.completeCount).isEqualTo(1);
        assertThat(dispatchService.getCount).isEqualTo(2);
    }

    @Test
    void completedStatusAfterExistingInFlightStateReconcilesWithoutReposting() {
        completionStateStore.authorizeException = new CheckoutSafetyException(
                "Checkout completion cannot be re-authorized after completion starts"
        );
        dispatchService.getResults.add(toolResult(completedCheckoutJson("order-789")));

        NativeCheckoutResult result = service.complete(provider(true), command(false), UcpSession.cart("cart-1", null, null));

        assertThat(result.status()).isEqualTo(NativeCheckoutStatus.COMPLETED);
        assertThat(result.orderRef()).isEqualTo("order-789");
        assertThat(dispatchService.completeCount).isZero();
        assertThat(completionStateStore.markCompletedCount).isEqualTo(1);
    }

    @Test
    void nullRawCheckoutPayloadFailsCleanlyWhenBuildingAp2Mandate() {
        Jcs jcs = new Jcs();
        service = completionService(new NoopCheckoutTotalsReconciler(objectMapper, jcs), jcs);
        dispatchService.getResults.add(toolResult("null", openCheckoutJson()));

        assertThatThrownBy(() -> service.complete(
                provider(true),
                commandWithAp2Mandate(),
                UcpSession.cart("cart-1", null, null)
        )).isInstanceOf(CheckoutSafetyException.class)
                .hasMessageContaining("could not be prepared")
                .hasCauseInstanceOf(Ap2MandateException.class);
    }

    @Test
    void ap2SecurityLockBlocksFeatureFlagHandoffFallback() {
        NativeCheckoutResult result = service.complete(
                provider(false),
                command(true),
                UcpSession.cart("cart-1", null, "https://merchant.example/continue")
        );

        assertThat(result.status()).isEqualTo(NativeCheckoutStatus.SECURITY_LOCKED);
        assertThat(result.nativeAttempted()).isFalse();
        assertThat(dispatchService.completeCount).isZero();
    }

    @Test
    void recoverableMessageReturnsRecoverableWithoutMarkingCompleted() {
        dispatchService.getResults.add(toolResult(openCheckoutJson()));
        dispatchService.completeResult = toolResult(messageCheckoutJson("recoverable", "processor_busy"));

        NativeCheckoutResult result = service.complete(provider(true), command(false), UcpSession.cart("cart-1", null, null));

        assertThat(result.status()).isEqualTo(NativeCheckoutStatus.RECOVERABLE_ERROR);
        assertThat(completionStateStore.markCompletedCount).isZero();
        assertThat(completionStateStore.releaseCompletionStartCount).isEqualTo(1);
    }

    @Test
    void extensionInteractionErrorReturnsRecoverableWithoutMarkingCompleted() {
        dispatchService.getResults.add(toolResult(openCheckoutJson()));
        dispatchService.completeResult = toolResult(extensionInteractionErrorCheckoutJson());

        NativeCheckoutResult result = service.complete(provider(true), command(false), UcpSession.cart("cart-1", null, null));

        assertThat(result.status()).isEqualTo(NativeCheckoutStatus.RECOVERABLE_ERROR);
        assertThat(result.messages())
                .containsExactly("An extension interaction is required to complete the checkout.");
        assertThat(completionStateStore.markCompletedCount).isZero();
        assertThat(completionStateStore.releaseCompletionStartCount).isEqualTo(1);
    }

    @Test
    void unrecoverableMessageRecordsFailedResponse() {
        dispatchService.getResults.add(toolResult(openCheckoutJson()));
        dispatchService.completeResult = toolResult(messageCheckoutJson("unrecoverable", "payment_declined"));

        NativeCheckoutResult result = service.complete(provider(true), command(false), UcpSession.cart("cart-1", null, null));

        assertThat(result.status()).isEqualTo(NativeCheckoutStatus.UNRECOVERABLE_ERROR);
        assertThat(idempotencyKeyStore.recordCommands)
                .extracting(RecordIdempotencyResponseCommand::status)
                .contains(CheckoutIdempotencyStatus.FAILED);
    }

    @Test
    void chargeMismatchMessageRecordsChargeMismatchCanary() {
        dispatchService.getResults.add(toolResult(openCheckoutJson()));
        dispatchService.completeResult = toolResult(messageCheckoutJson("unrecoverable", "charge_mismatch"));

        NativeCheckoutResult result = service.complete(provider(true), command(false), UcpSession.cart("cart-1", null, null));

        assertThat(result.status()).isEqualTo(NativeCheckoutStatus.UNRECOVERABLE_ERROR);
        assertThat(checkoutCanaryService.commands)
                .extracting(CanaryEventCommand::outcome)
                .contains(CheckoutCanaryOutcome.CHARGE_MISMATCH);
        assertThat(checkoutCanaryService.commands)
                .filteredOn(CanaryEventCommand::chargeMismatch)
                .hasSize(1);
    }

    @Test
    void canceledCompletionResponseRecordsFailedIdempotencyResponse() {
        dispatchService.getResults.add(toolResult(openCheckoutJson()));
        dispatchService.completeResult = toolResult(canceledCheckoutJson());

        NativeCheckoutResult result = service.complete(provider(true), command(false), UcpSession.cart("cart-1", null, null));

        assertThat(result.status()).isEqualTo(NativeCheckoutStatus.CANCELED);
        assertThat(idempotencyKeyStore.recordCommands)
                .extracting(RecordIdempotencyResponseCommand::status)
                .containsExactly(CheckoutIdempotencyStatus.FAILED);
    }

    @Test
    void scaCompletionResponseRecordsInFlightIdempotencyResponse() {
        dispatchService.getResults.add(toolResult(openCheckoutJson()));
        dispatchService.completeResult = toolResult(scaCheckoutJson());

        NativeCheckoutResult result = service.complete(provider(true), command(false), UcpSession.cart("cart-1", null, null));

        assertThat(result.status()).isEqualTo(NativeCheckoutStatus.SCA_REQUIRED);
        assertThat(idempotencyKeyStore.recordCommands)
                .extracting(RecordIdempotencyResponseCommand::status)
                .containsExactly(CheckoutIdempotencyStatus.COMPLETION_IN_FLIGHT);
    }

    @Test
    void statusFirstCanceledAfterAmbiguousFailureRecordsFailedIdempotencyResponse() {
        dispatchService.getResults.add(toolResult(openCheckoutJson()));
        dispatchService.getResults.add(toolResult(canceledCheckoutJson()));
        dispatchService.completeException = new RuntimeException("connection reset");

        NativeCheckoutResult result = service.complete(provider(true), command(false), UcpSession.cart("cart-1", null, null));

        assertThat(result.status()).isEqualTo(NativeCheckoutStatus.CANCELED);
        assertThat(dispatchService.completeCount).isEqualTo(1);
        assertThat(dispatchService.getCount).isEqualTo(2);
        assertThat(idempotencyKeyStore.recordCommands)
                .extracting(RecordIdempotencyResponseCommand::status)
                .containsExactly(CheckoutIdempotencyStatus.FAILED);
    }

    @Test
    void statusFirstScaAfterAmbiguousFailureRecordsInFlightIdempotencyResponse() {
        dispatchService.getResults.add(toolResult(openCheckoutJson()));
        dispatchService.getResults.add(toolResult(scaCheckoutJson()));
        dispatchService.completeException = new RuntimeException("connection reset");

        NativeCheckoutResult result = service.complete(provider(true), command(false), UcpSession.cart("cart-1", null, null));

        assertThat(result.status()).isEqualTo(NativeCheckoutStatus.SCA_REQUIRED);
        assertThat(dispatchService.completeCount).isEqualTo(1);
        assertThat(dispatchService.getCount).isEqualTo(2);
        assertThat(idempotencyKeyStore.recordCommands)
                .extracting(RecordIdempotencyResponseCommand::status)
                .containsExactly(CheckoutIdempotencyStatus.COMPLETION_IN_FLIGHT);
    }

    @Test
    void cancelAfterCompletionInFlightQueriesStatusAndBlocksWhenNotCompleted() {
        completionStateStore.cancelResult = false;
        dispatchService.getResults.add(toolResult(processingCheckoutJson()));

        assertThatThrownBy(() -> service.cancel(
                provider(true),
                new NativeCheckoutCancellationCommand(CART_ID, CHECKOUT_ID, "buyer_changed_mind", false),
                UcpSession.cart("cart-1", null, null)
        )).isInstanceOf(CheckoutSafetyException.class)
                .hasMessageContaining("blocked once completion is in flight");
        assertThat(dispatchService.cancelCount).isZero();
    }

    @Test
    void cancelRetryReturnsRemoteCanceledStatusWithoutPostingCancelAgain() {
        completionStateStore.cancelResult = false;
        dispatchService.getResults.add(toolResult(canceledCheckoutJson()));

        NativeCheckoutResult result = service.cancel(
                provider(true),
                new NativeCheckoutCancellationCommand(CART_ID, CHECKOUT_ID, "buyer_changed_mind", false),
                UcpSession.cart("cart-1", null, null)
        );

        assertThat(result.status()).isEqualTo(NativeCheckoutStatus.CANCELED);
        assertThat(dispatchService.cancelCount).isZero();
    }

    private NativeCheckoutCompletionCommand command(boolean ap2SecurityLock) {
        return new NativeCheckoutCompletionCommand(
                CART_ID,
                USER_ID,
                CONSENT_ID,
                CHECKOUT_ID,
                List.of(paymentInstrument()),
                "idem-1",
                ap2SecurityLock,
                null,
                new CheckoutSignals("test", null)
        );
    }

    private NativeCheckoutCompletionCommand commandWithAp2Mandate() {
        return new NativeCheckoutCompletionCommand(
                CART_ID,
                USER_ID,
                CONSENT_ID,
                CHECKOUT_ID,
                List.of(paymentInstrument()),
                "idem-1",
                true,
                new NativeCheckoutCompletionCommand.Ap2MandateInput(
                        new JsonWebKey("EC", "merchant-key", null, null, null, null, null, null, null, List.of()),
                        "merchant-key",
                        "merchant.example",
                        "agent.example",
                        "merchant.example",
                        "nonce-1",
                        NOW.plus(Duration.ofMinutes(10)),
                        "merchant-authorization-jws"
                ),
                new CheckoutSignals("test", null)
        );
    }

    private PaymentInstrument paymentInstrument() {
        return new PaymentInstrument(
                "card",
                1999L,
                "USD",
                new PaymentCredential("card_token", "payment-token", new TokenPaymentCredentialDetails("test")),
                PaymentScaLiability.shiftedTo("issuer", "test")
        );
    }

    private BuyerConsentArtifact consent() {
        return new BuyerConsentArtifact(
                CONSENT_ID,
                USER_ID,
                MERCHANT_ID,
                CHECKOUT_ID,
                List.of(new BuyerConsentArtifact.LineItem("line-1", "variant-1", 1, 1499L, "USD")),
                1999L,
                "USD",
                200L,
                new BuyerConsentShippingAddress(null, null, null, "10001", "US"),
                "standard",
                "instrument-hash",
                NOW,
                NOW.plus(Duration.ofMinutes(10)),
                "terms-hash"
        );
    }

    private MerchantCartProvider provider(boolean nativeEnabled) {
        return new MerchantCartProvider(
                MERCHANT_ID,
                "merchant.example",
                "https://merchant.example/api/mcp",
                null,
                nativeEnabled
        );
    }

    private UcpCheckoutToolResult toolResult(String json) {
        return toolResult(json, json);
    }

    private UcpCheckoutToolResult toolResult(String rawJson, String responseJson) {
        try {
            return new UcpCheckoutToolResult(
                    "https://merchant.example/api/mcp",
                    rawJson,
                    objectMapper.readValue(responseJson, UcpCheckoutResponse.class)
            );
        } catch (JacksonException exception) {
            throw new AssertionError(exception);
        }
    }

    private Rfc9421Signer signer() {
        SigningKeyProperties properties = new SigningKeyProperties(
                Duration.ofMinutes(5),
                Duration.ofSeconds(30),
                List.of(new SigningKeyProperties.Key(
                        "transport-1",
                        SigningKeyPurpose.TRANSPORT,
                        SigningKeyStatus.ACTIVE,
                        privateJwk("transport-1").toCharArray(),
                        null,
                        null
                ))
        );
        return new Rfc9421Signer(signingKeyProvider(), properties);
    }

    private SigningKeyProvider signingKeyProvider() {
        return new SigningKeyProvider() {
            @Override
            public SigningKey activePrivateKey(SigningKeyPurpose purpose) {
                if (purpose != SigningKeyPurpose.TRANSPORT) {
                    throw new SigningException("unexpected purpose");
                }
                return signingKey();
            }

            @Override
            public Optional<SigningKey> key(String kid, SigningKeyPurpose purpose) {
                SigningKey key = signingKey();
                return key.kid().equals(kid) && key.purpose() == purpose ? Optional.of(key) : Optional.empty();
            }

            @Override
            public List<PublicSigningKey> publicKeys() {
                return List.of(signingKey().toPublicSigningKey());
            }
        };
    }

    private SigningKey signingKey() {
        try {
            ECKey privateKey = ECKey.parse(privateJwk("transport-1"));
            return new SigningKey(
                    "transport-1",
                    SigningKeyPurpose.TRANSPORT,
                    SigningKeyStatus.ACTIVE,
                    privateKey,
                    privateKey.toPublicJWK(),
                    null
            );
        } catch (java.text.ParseException exception) {
            throw new AssertionError(exception);
        }
    }

    private String openCheckoutJson() {
        return """
                {
                  "checkout": {
                    "id": "co_123",
                    "merchant_id": "%s",
                    "status": "open",
                    "total_amount": {"amount_minor": 1999, "currency": "USD"},
                    "tax_amount": {"amount_minor": 200, "currency": "USD"},
                    "line_items": [
                      {
                        "id": "line-1",
                        "product_variant_id": "variant-1",
                        "quantity": 1,
                        "total_amount": {"amount_minor": 1499, "currency": "USD"}
                      }
                    ],
                    "shipping_address": {"country": "US", "postal_code": "10001"},
                    "shipping_method": {"handle": "standard"},
                    "messages": []
                  },
                  "errors": []
                }
                """.formatted(MERCHANT_ID);
    }

    private String completedCheckoutJson(String orderId) {
        return """
                {
                  "checkout": {
                    "id": "co_123",
                    "status": "completed",
                    "order_id": "%s",
                    "messages": []
                  },
                  "errors": []
                }
                """.formatted(orderId);
    }

    private String processingCheckoutJson() {
        return """
                {
                  "checkout": {
                    "id": "co_123",
                    "status": "processing",
                    "messages": []
                  },
                  "errors": []
                }
                """;
    }

    private String canceledCheckoutJson() {
        return """
                {
                  "checkout": {
                    "id": "co_123",
                    "status": "canceled",
                    "messages": []
                  },
                  "errors": []
                }
                """;
    }

    private String scaCheckoutJson() {
        return """
                {
                  "checkout": {
                    "id": "co_123",
                    "status": "requires_escalation",
                    "continue_url": "https://merchant.example/sca",
                    "messages": []
                  },
                  "errors": []
                }
                """;
    }

    private String messageCheckoutJson(String severity, String code) {
        return """
                {
                  "checkout": {
                    "id": "co_123",
                    "status": "open",
                    "messages": [
                      {"severity": "%s", "code": "%s", "message": "merchant message"}
                    ]
                  },
                  "errors": []
                }
                """.formatted(severity, code);
    }

    private String extensionInteractionErrorCheckoutJson() {
        return """
                {
                  "checkout": {
                    "id": "co_123",
                    "status": "incomplete",
                    "messages": []
                  },
                  "errors": [
                    {
                      "code": "extension_interaction_required",
                      "message": "An extension interaction is required to complete the checkout."
                    }
                  ]
                }
                """;
    }

    private String privateJwk(String kid) {
        return """
                {"kty":"EC","alg":"ES256","crv":"P-256","kid":"%s","d":"UpuF81l-kOxbjf7T4mNSv0r5tN67Gim7rnf6EFpcYDs","x":"qIVYZVLCrPZHGHjP17CTW0_-D9Lfw0EkjqF7xB4FivA","y":"Mc4nN9LTDOBhfoUeg8Ye9WedFRhnZXZJA12Qp0zZ6F0"}
                """.formatted(kid).trim();
    }

    private static final class FakeDispatchService extends MerchantCheckoutPluginDispatchService {

        private final Queue<UcpCheckoutToolResult> getResults = new ArrayDeque<>();
        private UcpCheckoutToolResult completeResult;
        private RuntimeException completeException;
        private int getCount;
        private int completeCount;
        private int cancelCount;

        private FakeDispatchService() {
            super(null, null, null);
        }

        @Override
        public UcpCheckoutToolResult getCheckout(
                MerchantCartProvider provider,
                GetCheckoutRequest request,
                UcpSession session
        ) {
            getCount++;
            return getResults.remove();
        }

        @Override
        public UcpCheckoutToolResult completeCheckout(
                MerchantCartProvider provider,
                CompleteCheckoutRequest request,
                UcpSession session,
                Map<String, String> signedHeaders
        ) {
            completeCount++;
            if (completeException != null) {
                throw completeException;
            }
            return completeResult;
        }

        @Override
        public UcpCheckoutToolResult cancelCheckout(
                MerchantCartProvider provider,
                CancelCheckoutRequest request,
                UcpSession session,
                Map<String, String> signedHeaders
        ) {
            cancelCount++;
            throw new AssertionError("cancel_checkout should not be posted in this test");
        }
    }

    private static final class FakeCompletionStateStore extends CheckoutCompletionStateStore {

        private boolean startResult = true;
        private boolean cancelResult = true;
        private int markCompletedCount;
        private int releaseCompletionStartCount;
        private CheckoutSafetyException authorizeException;

        private FakeCompletionStateStore() {
            super((CheckoutCompletionStateRepository) null);
        }

        @Override
        public CheckoutCompletionState authorize(AuthorizeCheckoutCompletionCommand command) {
            if (authorizeException != null) {
                throw authorizeException;
            }
            return null;
        }

        @Override
        public boolean tryStartCompletion(StartCheckoutCompletionCommand command) {
            return startResult;
        }

        @Override
        public boolean tryCancel(StartCheckoutCompletionCommand command) {
            return cancelResult;
        }

        @Override
        public CheckoutCompletionState markCompleted(StartCheckoutCompletionCommand command) {
            markCompletedCount++;
            return null;
        }

        @Override
        public CheckoutCompletionState markCompletedFromRemoteStatus(StartCheckoutCompletionCommand command) {
            markCompletedCount++;
            return null;
        }

        @Override
        public CheckoutCompletionState releaseCompletionStart(StartCheckoutCompletionCommand command) {
            releaseCompletionStartCount++;
            return null;
        }
    }

    private static final class FakeIdempotencyKeyStore extends IdempotencyKeyStore {

        private final List<RecordIdempotencyResponseCommand> recordCommands = new ArrayList<>();

        private FakeIdempotencyKeyStore() {
            super((CheckoutIdempotencyKeyRepository) null);
        }

        @Override
        public CheckoutIdempotencyKey reserve(ReserveIdempotencyKeyCommand command) {
            return null;
        }

        @Override
        public CheckoutIdempotencyKey recordResponse(RecordIdempotencyResponseCommand command) {
            recordCommands.add(command);
            return null;
        }
    }

    private static final class FakeBuyerConsentService extends BuyerConsentService {

        private final BuyerConsentArtifact artifact;

        private FakeBuyerConsentService(BuyerConsentArtifact artifact, ObjectMapper objectMapper) {
            super(null, objectMapper);
            this.artifact = artifact;
        }

        @Override
        public BuyerConsentArtifact findArtifact(UUID consentId, UUID userId) {
            return artifact;
        }
    }

    private static final class NoopCheckoutTotalsReconciler extends CheckoutTotalsReconciler {

        private NoopCheckoutTotalsReconciler(ObjectMapper objectMapper, Jcs jcs) {
            super(objectMapper, jcs);
        }

        @Override
        public void rejectIfMismatch(ExpectedCheckout expected, Object checkout) {
        }
    }

    private static final class FakeCanaryService extends CheckoutCanaryService {

        private final List<CanaryEventCommand> commands = new ArrayList<>();

        private FakeCanaryService() {
            super(null);
        }

        @Override
        public void record(CanaryEventCommand command) {
            commands.add(command);
        }
    }
}
