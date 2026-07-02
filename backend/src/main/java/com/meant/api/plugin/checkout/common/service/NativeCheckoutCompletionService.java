package com.meant.api.plugin.checkout.common.service;

import com.meant.api.module.merchant.service.dto.MerchantCartProvider;
import com.meant.api.plugin.checkout.extension.buyerconsent.dto.BuyerConsentArtifact;
import com.meant.api.plugin.checkout.cancel.dto.CancelCheckoutRequest;
import com.meant.api.plugin.checkout.common.dto.UcpCheckoutResponse;
import com.meant.api.plugin.checkout.common.dto.UcpCheckoutToolResult;
import com.meant.api.plugin.checkout.common.entity.CheckoutCanaryOutcome;
import com.meant.api.plugin.checkout.common.entity.CheckoutIdempotencyStatus;
import com.meant.api.plugin.checkout.common.exception.UcpCheckoutSafetyException;
import com.meant.api.plugin.checkout.common.service.CheckoutCanaryService.CanaryEventCommand;
import com.meant.api.plugin.checkout.common.service.CheckoutTotalsReconciler.ExpectedCheckout;
import com.meant.api.plugin.checkout.common.service.CheckoutTotalsReconciler.ExpectedLineItem;
import com.meant.api.plugin.checkout.common.service.command.AuthorizeCheckoutCompletionCommand;
import com.meant.api.plugin.checkout.common.service.command.NativeCheckoutCancellationCommand;
import com.meant.api.plugin.checkout.common.service.command.NativeCheckoutCompletionCommand;
import com.meant.api.plugin.checkout.common.service.command.RecordIdempotencyResponseCommand;
import com.meant.api.plugin.checkout.common.service.command.ReserveIdempotencyKeyCommand;
import com.meant.api.plugin.checkout.common.service.command.StartCheckoutCompletionCommand;
import com.meant.api.plugin.checkout.common.service.dto.NativeCheckoutResult;
import com.meant.api.plugin.checkout.common.service.dto.NativeCheckoutStatus;
import com.meant.api.plugin.checkout.complete.CompleteCheckoutCapability;
import com.meant.api.plugin.checkout.complete.dto.CompleteCheckoutRequest;
import com.meant.api.plugin.checkout.extension.ap2mandate.Ap2MandateExtensionSupport;
import com.meant.api.plugin.checkout.get.dto.GetCheckoutRequest;
import com.meant.api.plugin.signing.Ap2MandateException;
import com.meant.api.plugin.signing.Ap2MandateService;
import com.meant.api.plugin.signing.Jcs;
import com.meant.api.plugin.signing.Rfc9421Signer;
import com.meant.api.plugin.spi.CapabilityId;
import com.meant.api.plugin.support.UcpSession;
import com.nimbusds.jose.jwk.ECKey;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Service
@Validated
@RequiredArgsConstructor
public class NativeCheckoutCompletionService {

    private static final CapabilityId AP2_MANDATE = Ap2MandateExtensionSupport.ID;
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private static final String UCP_AGENT_HEADER = "UCP-Agent";
    private static final String UCP_AGENT_VALUE = "meant";

    private final MerchantCheckoutPluginDispatchService dispatchService;
    private final CompleteCheckoutCapability completeCheckoutCapability;
    private final CheckoutCompletionStateStore completionStateStore;
    private final IdempotencyKeyStore idempotencyKeyStore;
    private final BuyerConsentService buyerConsentService;
    private final CheckoutTotalsReconciler totalsReconciler;
    private final Ap2MandateService ap2MandateService;
    private final Rfc9421Signer rfc9421Signer;
    private final Jcs jcs;
    private final CheckoutCanaryService checkoutCanaryService;
    private final ObjectMapper objectMapper;

    public NativeCheckoutResult complete(
            @NotNull MerchantCartProvider provider,
            @NotNull @Valid NativeCheckoutCompletionCommand command,
            @NotNull UcpSession session
    ) {
        if (!provider.nativeCheckoutEnabled()) {
            return featureDisabledResult(provider, command.checkoutId(), ap2Required(command, session), session);
        }

        BuyerConsentArtifact consent = buyerConsentService.findArtifact(command.buyerConsentId(), command.userId());
        requireConsentMatchesProvider(provider, command, consent);
        StartCheckoutCompletionCommand stateCommand = new StartCheckoutCompletionCommand(command.checkoutId());
        try {
            completionStateStore.authorize(new AuthorizeCheckoutCompletionCommand(command.checkoutId(), command.cartId()));
        } catch (UcpCheckoutSafetyException exception) {
            return statusFirstAfterLocalStateConflict(provider, command.checkoutId(), session, stateCommand, exception);
        }

        UcpCheckoutToolResult refreshedCheckout = dispatchService.getCheckout(
                provider,
                new GetCheckoutRequest(command.checkoutId()),
                session
        );
        NativeCheckoutResult terminalStatus = terminalStatusResult(provider, refreshedCheckout, true, false);
        if (terminalStatus != null) {
            completeLocalStateIfPossible(stateCommand, terminalStatus);
            return terminalStatus;
        }
        totalsReconciler.rejectIfMismatch(expectedCheckout(consent), refreshedCheckout.rawResponse());

        if (!completionStateStore.tryStartCompletion(stateCommand)) {
            return statusFirstWithoutRepost(provider, command.checkoutId(), session, true, stateCommand);
        }

        boolean remoteCompletionPosted = false;
        String idempotencyKey = idempotencyKey(command);
        try {
            boolean ap2Required = ap2Required(command, session);
            String checkoutMandate = ap2Required
                    ? checkoutMandate(provider, command, consent, refreshedCheckout)
                    : null;
            CompleteCheckoutRequest completeRequest = new CompleteCheckoutRequest(
                    command.checkoutId(),
                    command.paymentInstruments(),
                    checkoutMandate,
                    command.signals()
            );
            byte[] canonicalBody = canonicalCompleteBody(completeRequest, session);
            idempotencyKeyStore.reserve(new ReserveIdempotencyKeyCommand(
                    idempotencyKey,
                    canonicalBody,
                    command.checkoutId(),
                    command.buyerConsentId(),
                    consent.totalAmountMinor(),
                    consent.currency(),
                    provider.merchantId()
            ));
            Map<String, String> signedHeaders = signedHeaders(provider, canonicalBody, idempotencyKey);
            UcpCheckoutToolResult completed;
            try {
                remoteCompletionPosted = true;
                completed = dispatchService.completeCheckout(
                        provider,
                        completeRequest,
                        session,
                        signedHeaders
                );
            } catch (RuntimeException exception) {
                return statusFirstAfterAmbiguousFailure(
                        provider,
                        command.checkoutId(),
                        session,
                        stateCommand,
                        idempotencyKey,
                        exception
                );
            }
            return handleCompletionResponse(provider, completed, stateCommand, idempotencyKey);
        } catch (Ap2MandateException | ParseException | JacksonException | IllegalArgumentException exception) {
            completionStateStore.releaseCompletionStart(stateCommand);
            recordCanary(
                    provider,
                    command.checkoutId(),
                    CheckoutCanaryOutcome.UNRECOVERABLE_ERROR,
                    null,
                    simpleErrorCode(exception),
                    false
            );
            throw new UcpCheckoutSafetyException("Native checkout completion could not be prepared", exception);
        } catch (RuntimeException exception) {
            if (!remoteCompletionPosted) {
                completionStateStore.releaseCompletionStart(stateCommand);
                throw exception;
            }
            return statusFirstAfterAmbiguousFailure(provider, command.checkoutId(), session, stateCommand, idempotencyKey, exception);
        }
    }

    public NativeCheckoutResult cancel(
            @NotNull MerchantCartProvider provider,
            @NotNull @Valid NativeCheckoutCancellationCommand command,
            @NotNull UcpSession session
    ) {
        if (!provider.nativeCheckoutEnabled()) {
            return featureDisabledResult(
                    provider,
                    command.checkoutId(),
                    command.ap2SecurityLock() || session.activeCapabilities().supports(AP2_MANDATE),
                    session
            );
        }
        StartCheckoutCompletionCommand stateCommand = new StartCheckoutCompletionCommand(command.checkoutId());
        try {
            completionStateStore.authorize(new AuthorizeCheckoutCompletionCommand(command.checkoutId(), command.cartId()));
        } catch (UcpCheckoutSafetyException exception) {
            return blockedCancelStatus(provider, command.checkoutId(), session, exception);
        }
        if (!completionStateStore.tryCancel(stateCommand)) {
            return blockedCancelStatus(provider, command.checkoutId(), session, null);
        }

        CancelCheckoutRequest cancelRequest = new CancelCheckoutRequest(command.checkoutId(), command.reason());
        byte[] canonicalBody = canonicalCancelBody(cancelRequest);
        Map<String, String> signedHeaders = signedHeaders(
                provider,
                canonicalBody,
                "ucp-cancel-" + UUID.nameUUIDFromBytes(canonicalBody)
        );
        UcpCheckoutToolResult canceled = dispatchService.cancelCheckout(
                provider,
                cancelRequest,
                session,
                signedHeaders
        );
        NativeCheckoutResult result = cancelResult(provider, canceled);
        if (result.status() == NativeCheckoutStatus.CANCELED) {
            completionStateStore.markCanceled(stateCommand);
        }
        return result;
    }

    private NativeCheckoutResult featureDisabledResult(
            MerchantCartProvider provider,
            String checkoutId,
            boolean ap2SecurityLock,
            UcpSession session
    ) {
        if (ap2SecurityLock) {
            recordCanary(
                    provider,
                    checkoutId,
                    CheckoutCanaryOutcome.UNRECOVERABLE_ERROR,
                    null,
                    "ap2_security_lock",
                    false
            );
            return new NativeCheckoutResult(
                    NativeCheckoutStatus.SECURITY_LOCKED,
                    checkoutId,
                    null,
                    null,
                    List.of("AP2 was negotiated; native checkout cannot fall back to handoff."),
                    false
            );
        }
        recordCanary(
                provider,
                checkoutId,
                CheckoutCanaryOutcome.HANDOFF_FALLBACK,
                null,
                null,
                false
        );
        return NativeCheckoutResult.handoff(checkoutId, session.continueUrl());
    }

    private void requireConsentMatchesProvider(
            MerchantCartProvider provider,
            NativeCheckoutCompletionCommand command,
            BuyerConsentArtifact consent
    ) {
        if (!Objects.equals(consent.merchantId(), provider.merchantId())) {
            throw new UcpCheckoutSafetyException("Buyer consent merchant did not match checkout merchant");
        }
        if (!Objects.equals(consent.checkoutId(), command.checkoutId())) {
            throw new UcpCheckoutSafetyException("Buyer consent checkout did not match requested checkout");
        }
    }

    private ExpectedCheckout expectedCheckout(BuyerConsentArtifact consent) {
        return new ExpectedCheckout(
                consent.checkoutId(),
                consent.merchantId().toString(),
                consent.totalAmountMinor(),
                consent.currency(),
                consent.lineItems().stream()
                        .map(line -> new ExpectedLineItem(
                                line.id(),
                                line.productVariantId(),
                                line.quantity(),
                                line.totalAmountMinor(),
                                line.currency()
                        ))
                        .toList(),
                consent.taxAmountMinor(),
                null,
                null,
                consent.shippingAddress(),
                consent.shippingMethod(),
                null,
                consent.totalAmountMinor()
        );
    }

    private NativeCheckoutResult statusFirstWithoutRepost(
            MerchantCartProvider provider,
            String checkoutId,
            UcpSession session,
            boolean nativeAttempted,
            StartCheckoutCompletionCommand stateCommand
    ) {
        UcpCheckoutToolResult status = dispatchService.getCheckout(provider, new GetCheckoutRequest(checkoutId), session);
        NativeCheckoutResult result = terminalStatusResult(provider, status, nativeAttempted, true);
        if (result != null) {
            completeLocalStateIfPossible(stateCommand, result);
            return result;
        }
        return processingResult(provider, status, nativeAttempted);
    }

    private NativeCheckoutResult statusFirstAfterLocalStateConflict(
            MerchantCartProvider provider,
            String checkoutId,
            UcpSession session,
            StartCheckoutCompletionCommand stateCommand,
            UcpCheckoutSafetyException originalException
    ) {
        try {
            return statusFirstWithoutRepost(provider, checkoutId, session, true, stateCommand);
        } catch (RuntimeException statusException) {
            originalException.addSuppressed(statusException);
            throw originalException;
        }
    }

    private NativeCheckoutResult statusFirstAfterAmbiguousFailure(
            MerchantCartProvider provider,
            String checkoutId,
            UcpSession session,
            StartCheckoutCompletionCommand stateCommand,
            String idempotencyKey,
            RuntimeException originalException
    ) {
        try {
            UcpCheckoutToolResult status = dispatchService.getCheckout(provider, new GetCheckoutRequest(checkoutId), session);
            NativeCheckoutResult terminal = terminalStatusResult(provider, status, true, true);
            if (terminal != null) {
                if (terminal.status() == NativeCheckoutStatus.COMPLETED) {
                    completionStateStore.markCompletedFromRemoteStatus(stateCommand);
                }
                recordTerminalIdempotencyResponse(idempotencyKey, terminal, status.rawResponse());
                return terminal;
            }
            idempotencyKeyStore.recordResponse(new RecordIdempotencyResponseCommand(
                    idempotencyKey,
                    CheckoutIdempotencyStatus.COMPLETION_IN_FLIGHT,
                    status.rawResponse(),
                    null
            ));
            return processingResult(provider, status, true);
        } catch (RuntimeException statusException) {
            recordCanary(
                    provider,
                    checkoutId,
                    CheckoutCanaryOutcome.PROTOCOL_ERROR,
                    null,
                    simpleErrorCode(originalException),
                    false
            );
            originalException.addSuppressed(statusException);
            throw originalException;
        }
    }

    private NativeCheckoutResult handleCompletionResponse(
            MerchantCartProvider provider,
            UcpCheckoutToolResult result,
            StartCheckoutCompletionCommand stateCommand,
            String idempotencyKey
    ) {
        UcpCheckoutResponse.CheckoutMessage unrecoverable = firstUnrecoverableMessage(result.response());
        if (unrecoverable != null) {
            NativeCheckoutResult nativeResult = new NativeCheckoutResult(
                    NativeCheckoutStatus.UNRECOVERABLE_ERROR,
                    checkoutId(result.response()),
                    null,
                    continueUrl(result.response()),
                    messages(result.response()),
                    true
            );
            idempotencyKeyStore.recordResponse(new RecordIdempotencyResponseCommand(
                    idempotencyKey,
                    CheckoutIdempotencyStatus.FAILED,
                    result.rawResponse(),
                    null
            ));
            recordCanary(
                    provider,
                    nativeResult.checkoutId(),
                    chargeMismatch(unrecoverable) ? CheckoutCanaryOutcome.CHARGE_MISMATCH : CheckoutCanaryOutcome.UNRECOVERABLE_ERROR,
                    status(result.response()),
                    unrecoverable.code(),
                    chargeMismatch(unrecoverable)
            );
            return nativeResult;
        }
        NativeCheckoutResult terminal = terminalStatusResult(provider, result, true, true);
        if (terminal != null) {
            if (terminal.status() == NativeCheckoutStatus.COMPLETED) {
                completionStateStore.markCompletedFromRemoteStatus(stateCommand);
            }
            recordTerminalIdempotencyResponse(idempotencyKey, terminal, result.rawResponse());
            return terminal;
        }
        UcpCheckoutResponse.CheckoutMessage recoverable = firstRecoverableMessage(result.response());
        if (recoverable != null) {
            idempotencyKeyStore.recordResponse(new RecordIdempotencyResponseCommand(
                    idempotencyKey,
                    CheckoutIdempotencyStatus.COMPLETION_IN_FLIGHT,
                    result.rawResponse(),
                    null
            ));
            NativeCheckoutResult nativeResult = new NativeCheckoutResult(
                    NativeCheckoutStatus.RECOVERABLE_ERROR,
                    checkoutId(result.response()),
                    null,
                    continueUrl(result.response()),
                    messages(result.response()),
                    true
            );
            recordCanary(
                    provider,
                    nativeResult.checkoutId(),
                    CheckoutCanaryOutcome.RECOVERABLE_ERROR,
                    status(result.response()),
                    recoverable.code(),
                    false
            );
            return nativeResult;
        }
        idempotencyKeyStore.recordResponse(new RecordIdempotencyResponseCommand(
                idempotencyKey,
                CheckoutIdempotencyStatus.COMPLETION_IN_FLIGHT,
                result.rawResponse(),
                null
        ));
        return processingResult(provider, result, true);
    }

    private NativeCheckoutResult terminalStatusResult(
            MerchantCartProvider provider,
            UcpCheckoutToolResult result,
            boolean nativeAttempted,
            boolean allowSca
    ) {
        UcpCheckoutResponse response = result.response();
        String status = normalizedStatus(status(response));
        if ("completed".equals(status) || hasText(orderRef(response))) {
            NativeCheckoutResult nativeResult = new NativeCheckoutResult(
                    NativeCheckoutStatus.COMPLETED,
                    checkoutId(response),
                    orderRef(response),
                    null,
                    messages(response),
                    nativeAttempted
            );
            recordCanary(provider, nativeResult.checkoutId(), CheckoutCanaryOutcome.COMPLETED, status, null, false);
            return nativeResult;
        }
        if ("canceled".equals(status) || "cancelled".equals(status)) {
            NativeCheckoutResult nativeResult = new NativeCheckoutResult(
                    NativeCheckoutStatus.CANCELED,
                    checkoutId(response),
                    null,
                    null,
                    messages(response),
                    nativeAttempted
            );
            recordCanary(provider, nativeResult.checkoutId(), CheckoutCanaryOutcome.CANCELED, status, null, false);
            return nativeResult;
        }
        if (allowSca && hasText(continueUrl(response))) {
            NativeCheckoutResult nativeResult = new NativeCheckoutResult(
                    NativeCheckoutStatus.SCA_REQUIRED,
                    checkoutId(response),
                    null,
                    continueUrl(response),
                    messages(response),
                    nativeAttempted
            );
            recordCanary(provider, nativeResult.checkoutId(), CheckoutCanaryOutcome.SCA_REQUIRED, status, null, false);
            return nativeResult;
        }
        return null;
    }

    private NativeCheckoutResult processingResult(
            MerchantCartProvider provider,
            UcpCheckoutToolResult result,
            boolean nativeAttempted
    ) {
        NativeCheckoutResult nativeResult = new NativeCheckoutResult(
                NativeCheckoutStatus.PROCESSING,
                checkoutId(result.response()),
                null,
                continueUrl(result.response()),
                messages(result.response()),
                nativeAttempted
        );
        recordCanary(
                provider,
                nativeResult.checkoutId(),
                CheckoutCanaryOutcome.PROCESSING,
                status(result.response()),
                null,
                false
        );
        return nativeResult;
    }

    private NativeCheckoutResult cancelResult(MerchantCartProvider provider, UcpCheckoutToolResult result) {
        NativeCheckoutResult terminal = terminalStatusResult(provider, result, true, true);
        if (terminal != null) {
            return terminal;
        }
        return new NativeCheckoutResult(
                NativeCheckoutStatus.CANCELED,
                checkoutId(result.response()),
                null,
                null,
                messages(result.response()),
                true
        );
    }

    private NativeCheckoutResult blockedCancelStatus(
            MerchantCartProvider provider,
            String checkoutId,
            UcpSession session,
            RuntimeException originalException
    ) {
        NativeCheckoutResult status = statusFirstWithoutRepost(
                provider,
                checkoutId,
                session,
                true,
                new StartCheckoutCompletionCommand(checkoutId)
        );
        if (status.status() == NativeCheckoutStatus.COMPLETED || status.status() == NativeCheckoutStatus.CANCELED) {
            return status;
        }
        UcpCheckoutSafetyException blocked = new UcpCheckoutSafetyException(
                "Checkout cancellation is blocked once completion is in flight"
        );
        if (originalException != null) {
            blocked.addSuppressed(originalException);
        }
        throw blocked;
    }

    private void completeLocalStateIfPossible(
            StartCheckoutCompletionCommand stateCommand,
            NativeCheckoutResult terminalStatus
    ) {
        if (terminalStatus.status() != NativeCheckoutStatus.COMPLETED) {
            return;
        }
        completionStateStore.markCompletedFromRemoteStatus(stateCommand);
    }

    private void recordTerminalIdempotencyResponse(
            String idempotencyKey,
            NativeCheckoutResult terminal,
            String rawResponse
    ) {
        CheckoutIdempotencyStatus status = terminalIdempotencyStatus(terminal.status());
        if (status == null) {
            return;
        }
        idempotencyKeyStore.recordResponse(new RecordIdempotencyResponseCommand(
                idempotencyKey,
                status,
                rawResponse,
                terminal.status() == NativeCheckoutStatus.COMPLETED ? terminal.orderRef() : null
        ));
    }

    private CheckoutIdempotencyStatus terminalIdempotencyStatus(NativeCheckoutStatus status) {
        return switch (status) {
            case COMPLETED -> CheckoutIdempotencyStatus.COMPLETED;
            case CANCELED -> CheckoutIdempotencyStatus.FAILED;
            case SCA_REQUIRED -> CheckoutIdempotencyStatus.COMPLETION_IN_FLIGHT;
            default -> null;
        };
    }

    private boolean ap2Required(NativeCheckoutCompletionCommand command, UcpSession session) {
        return command.ap2SecurityLock() || Ap2MandateExtensionSupport.active(session.activeCapabilities());
    }

    private String checkoutMandate(
            MerchantCartProvider provider,
            NativeCheckoutCompletionCommand command,
            BuyerConsentArtifact consent,
            UcpCheckoutToolResult refreshedCheckout
    ) throws ParseException, JacksonException {
        NativeCheckoutCompletionCommand.Ap2MandateInput input = command.ap2Mandate();
        if (input == null) {
            throw new Ap2MandateException("AP2 was negotiated but mandate input was not supplied");
        }
        String checkoutPayloadJson = checkoutPayloadJson(refreshedCheckout);
        String merchantAuthorizationJws = hasText(input.merchantAuthorizationJws())
                ? input.merchantAuthorizationJws().trim()
                : merchantAuthorizationJws(checkoutPayloadJson);
        ECKey merchantPublicKey = ECKey.parse(input.merchantPublicJwk());
        return ap2MandateService.buildCheckoutMandate(new Ap2MandateService.BuildMandateCommand(
                checkoutPayloadJson,
                merchantAuthorizationJws,
                merchantPublicKey,
                input.expectedMerchantAuthorizationKid(),
                input.merchantAuthorizationIssuer(),
                provider.merchantId().toString(),
                command.checkoutId(),
                command.userId().toString(),
                input.agentIssuer(),
                input.audience(),
                input.nonce(),
                consent.totalAmountMinor(),
                consent.currency(),
                input.expiresAt()
        )).checkoutMandate();
    }

    private byte[] canonicalCompleteBody(CompleteCheckoutRequest request, UcpSession session) throws JacksonException {
        Object arguments = completeCheckoutCapability.buildArguments(request, session.activeCapabilities());
        return jcs.canonicalizeToUtf8Bytes(objectMapper.writeValueAsBytes(arguments));
    }

    private byte[] canonicalCancelBody(CancelCheckoutRequest request) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("checkout_id", request.checkoutId());
            if (hasText(request.reason())) {
                body.put("reason", request.reason().trim());
            }
            return jcs.canonicalizeToUtf8Bytes(objectMapper.writeValueAsBytes(body));
        } catch (JacksonException exception) {
            throw new UcpCheckoutSafetyException("Cancel checkout payload could not be canonicalized", exception);
        }
    }

    private Map<String, String> signedHeaders(MerchantCartProvider provider, byte[] body, String idempotencyKey) {
        String requestId = UUID.randomUUID().toString();
        Rfc9421Signer.SignedRequest signedRequest = rfc9421Signer.sign(new Rfc9421Signer.SigningRequest(
                "POST",
                signingEndpoint(provider),
                MediaType.APPLICATION_JSON_VALUE,
                idempotencyKey,
                requestId,
                body
        ));
        Map<String, String> headers = new LinkedHashMap<>(signedRequest.headers());
        headers.put(UCP_AGENT_HEADER, UCP_AGENT_VALUE);
        return headers;
    }

    private URI signingEndpoint(MerchantCartProvider provider) {
        String endpoint = firstText(provider.profileMcpEndpoint(), provider.advertisedMcpEndpoint());
        if (!hasText(endpoint)) {
            endpoint = "https://" + provider.domain() + "/api/mcp";
        }
        return URI.create(endpoint);
    }

    private String idempotencyKey(NativeCheckoutCompletionCommand command) {
        if (hasText(command.idempotencyKey())) {
            return command.idempotencyKey().trim();
        }
        return "ucp-complete-" + command.buyerConsentId();
    }

    private String checkoutPayloadJson(UcpCheckoutToolResult result) throws JacksonException {
        Map<String, Object> root = objectMapper.readValue(result.rawResponse(), MAP_TYPE);
        if (root == null) {
            throw new Ap2MandateException("Checkout response payload is empty or invalid");
        }
        Object checkout = root.get("checkout");
        Object payload = checkout instanceof Map<?, ?> ? checkout : root;
        return objectMapper.writeValueAsString(payload);
    }

    private String merchantAuthorizationJws(String checkoutPayloadJson) throws JacksonException {
        Map<String, Object> checkout = objectMapper.readValue(checkoutPayloadJson, MAP_TYPE);
        Object ap2 = checkout.get("ap2");
        if (ap2 instanceof Map<?, ?> ap2Map) {
            String authorization = scalarString(firstMapValue(ap2Map, "merchant_authorization", "merchantAuthorization"));
            if (hasText(authorization)) {
                return authorization;
            }
        }
        throw new Ap2MandateException("Checkout did not contain AP2 merchant_authorization");
    }

    private String status(UcpCheckoutResponse response) {
        UcpCheckoutResponse.Checkout checkout = response.resolvedCheckout();
        if (checkout != null && hasText(checkout.status())) {
            return checkout.status();
        }
        return response.status();
    }

    private String checkoutId(UcpCheckoutResponse response) {
        UcpCheckoutResponse.Checkout checkout = response.resolvedCheckout();
        if (checkout != null && hasText(checkout.id())) {
            return checkout.id();
        }
        return response.checkoutId();
    }

    private String continueUrl(UcpCheckoutResponse response) {
        UcpCheckoutResponse.Checkout checkout = response.resolvedCheckout();
        if (checkout != null && hasText(checkout.continueUrl())) {
            return checkout.continueUrl();
        }
        return response.continueUrl();
    }

    private String orderRef(UcpCheckoutResponse response) {
        UcpCheckoutResponse.Checkout checkout = response.resolvedCheckout();
        String orderRef = checkout == null ? null : firstText(checkout.orderId(), orderRef(checkout.order()));
        return firstText(orderRef, response.orderId(), orderRef(response.order()));
    }

    private String orderRef(Map<String, Object> order) {
        if (order == null) {
            return null;
        }
        return scalarString(firstMapValue(order, "id", "order_id", "orderId", "name", "reference", "ref"));
    }

    private UcpCheckoutResponse.CheckoutMessage firstRecoverableMessage(UcpCheckoutResponse response) {
        return allMessages(response).stream()
                .filter(UcpCheckoutResponse.CheckoutMessage::isRecoverable)
                .findFirst()
                .orElse(null);
    }

    private UcpCheckoutResponse.CheckoutMessage firstUnrecoverableMessage(UcpCheckoutResponse response) {
        return allMessages(response).stream()
                .filter(UcpCheckoutResponse.CheckoutMessage::isUnrecoverable)
                .findFirst()
                .orElse(null);
    }

    private List<String> messages(UcpCheckoutResponse response) {
        return allMessages(response).stream()
                .map(UcpCheckoutResponse.CheckoutMessage::message)
                .filter(this::hasText)
                .toList();
    }

    private List<UcpCheckoutResponse.CheckoutMessage> allMessages(UcpCheckoutResponse response) {
        List<UcpCheckoutResponse.CheckoutMessage> values = new ArrayList<>();
        if (response.messages() != null) {
            values.addAll(response.messages());
        }
        UcpCheckoutResponse.Checkout checkout = response.resolvedCheckout();
        if (checkout != null && checkout.messages() != null) {
            values.addAll(checkout.messages());
        }
        return values;
    }

    private boolean chargeMismatch(UcpCheckoutResponse.CheckoutMessage message) {
        return matches(message.code(), "charge_mismatch") || matches(message.code(), "amount_mismatch");
    }

    private void recordCanary(
            MerchantCartProvider provider,
            String checkoutId,
            CheckoutCanaryOutcome outcome,
            String remoteStatus,
            String errorCode,
            boolean chargeMismatch
    ) {
        checkoutCanaryService.record(new CanaryEventCommand(
                provider.merchantId(),
                checkoutId,
                outcome,
                remoteStatus,
                errorCode,
                chargeMismatch,
                provider.nativeCheckoutEnabled()
        ));
    }

    private Object firstMapValue(Map<?, ?> values, String... keys) {
        for (String key : keys) {
            for (Map.Entry<?, ?> entry : values.entrySet()) {
                if (entry.getKey() != null && key.equalsIgnoreCase(entry.getKey().toString())) {
                    return entry.getValue();
                }
            }
        }
        return null;
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

    private String normalizedStatus(String status) {
        return status == null ? "" : status.trim().toLowerCase(Locale.ROOT);
    }

    private String firstText(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private boolean matches(String value, String expected) {
        return value != null && value.trim().equalsIgnoreCase(expected);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String simpleErrorCode(Throwable throwable) {
        return throwable.getClass().getSimpleName();
    }
}
