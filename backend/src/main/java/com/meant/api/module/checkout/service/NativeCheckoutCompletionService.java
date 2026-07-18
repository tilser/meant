package com.meant.api.module.checkout.service;

import com.meant.api.module.merchant.constant.CommerceOperation;
import com.meant.api.module.merchant.service.dto.MerchantCartProvider;
import com.meant.api.plugin.checkout.extension.buyerconsent.dto.BuyerConsentArtifact;
import com.meant.api.plugin.checkout.cancel.dto.CancelCheckoutRequest;
import com.meant.api.plugin.checkout.common.dto.UcpCheckoutToolResult;
import com.meant.api.module.checkout.entity.CheckoutCanaryOutcome;
import com.meant.api.module.checkout.exception.CheckoutSafetyException;
import com.meant.api.module.checkout.service.CheckoutTotalsReconciler.ExpectedCheckout;
import com.meant.api.module.checkout.service.CheckoutTotalsReconciler.ExpectedLineItem;
import com.meant.api.module.checkout.service.command.AuthorizeCheckoutCompletionCommand;
import com.meant.api.module.checkout.service.command.NativeCheckoutCancellationCommand;
import com.meant.api.module.checkout.service.command.NativeCheckoutCompletionCommand;
import com.meant.api.module.checkout.service.command.ReserveIdempotencyKeyCommand;
import com.meant.api.module.checkout.service.command.StartCheckoutCompletionCommand;
import com.meant.api.module.checkout.service.dto.NativeCheckoutInterpretation;
import com.meant.api.module.checkout.service.dto.NativeCheckoutResult;
import com.meant.api.module.checkout.service.dto.NativeCheckoutStatus;
import com.meant.api.module.checkout.service.dto.CheckoutToolCallContext;
import com.meant.api.plugin.checkout.complete.dto.CompleteCheckoutRequest;
import com.meant.api.plugin.checkout.get.dto.GetCheckoutRequest;
import com.meant.api.plugin.signing.Ap2MandateException;
import com.meant.api.plugin.support.UcpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.text.ParseException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import tools.jackson.core.JacksonException;

@Service
@Validated
@RequiredArgsConstructor
public class NativeCheckoutCompletionService {

    private final MerchantCheckoutPluginDispatchService dispatchService;
    private final CheckoutCompletionStateStore completionStateStore;
    private final IdempotencyKeyStore idempotencyKeyStore;
    private final BuyerConsentService buyerConsentService;
    private final CheckoutTotalsReconciler totalsReconciler;
    private final NativeCheckoutAp2MandateBuilder ap2MandateBuilder;
    private final NativeCheckoutRequestSigner requestSigner;
    private final NativeCheckoutResultInterpreter resultInterpreter;
    private final NativeCheckoutCanaryRecorder canaryRecorder;
    private final NativeCheckoutIdempotencyResponseRecorder idempotencyResponseRecorder;

    public NativeCheckoutResult complete(
            @NotNull MerchantCartProvider provider,
            @NotNull @Valid NativeCheckoutCompletionCommand command,
            @NotNull UcpSession session
    ) {
        CheckoutToolCallContext callContext = CheckoutToolCallContext.forBuyer(command.buyerIp());
        if (!provider.executionPolicy().isAvailable(CommerceOperation.DIRECT_CHECKOUT_COMPLETION)) {
            return featureDisabledResult(provider, command.checkoutId(), ap2MandateBuilder.isRequired(command, session), session);
        }

        BuyerConsentArtifact consent = buyerConsentService.findArtifact(command.buyerConsentId(), command.userId());
        requireConsentMatchesProvider(provider, command, consent);
        StartCheckoutCompletionCommand stateCommand = new StartCheckoutCompletionCommand(command.checkoutId());
        try {
            completionStateStore.authorize(new AuthorizeCheckoutCompletionCommand(command.checkoutId(), command.cartId()));
        } catch (CheckoutSafetyException exception) {
            return statusFirstAfterLocalStateConflict(
                    provider, command.checkoutId(), session, stateCommand, exception, callContext);
        }

        UcpCheckoutToolResult refreshedCheckout = dispatchService.getCheckout(
                provider,
                new GetCheckoutRequest(command.checkoutId()),
                session,
                callContext
        );
        NativeCheckoutInterpretation terminalStatus = resultInterpreter.terminalStatusResult(refreshedCheckout, true, false);
        if (terminalStatus != null) {
            canaryRecorder.record(provider, terminalStatus);
            completeLocalStateIfPossible(stateCommand, terminalStatus.result());
            return terminalStatus.result();
        }
        totalsReconciler.rejectIfMismatch(expectedCheckout(consent), refreshedCheckout.response());

        if (!completionStateStore.tryStartCompletion(stateCommand)) {
            return statusFirstWithoutRepost(
                    provider, command.checkoutId(), session, true, stateCommand, callContext);
        }

        boolean remoteCompletionPosted = false;
        String idempotencyKey = idempotencyKey(command);
        try {
            boolean ap2Required = ap2MandateBuilder.isRequired(command, session);
            String checkoutMandate = ap2Required
                    ? ap2MandateBuilder.buildCheckoutMandate(provider, command, consent, refreshedCheckout)
                    : null;
            CompleteCheckoutRequest completeRequest = new CompleteCheckoutRequest(
                    command.checkoutId(),
                    command.paymentInstruments(),
                    checkoutMandate,
                    command.signals()
            );
            byte[] canonicalBody = requestSigner.canonicalCompleteBody(completeRequest, session);
            idempotencyKeyStore.reserve(new ReserveIdempotencyKeyCommand(
                    idempotencyKey,
                    canonicalBody,
                    command.checkoutId(),
                    command.buyerConsentId(),
                    consent.totalAmountMinor(),
                    consent.currency(),
                    provider.merchantId()
            ));
            Map<String, String> signedHeaders = requestSigner.signedHeaders(provider, canonicalBody, idempotencyKey);
            UcpCheckoutToolResult completed;
            try {
                remoteCompletionPosted = true;
                completed = dispatchService.completeCheckout(
                        provider,
                        completeRequest,
                        session,
                        signedHeaders,
                        callContext
                );
            } catch (RuntimeException exception) {
                return statusFirstAfterAmbiguousFailure(
                        provider,
                        command.checkoutId(),
                        session,
                        stateCommand,
                        idempotencyKey,
                        exception,
                        callContext
                );
            }
            return handleCompletionResponse(provider, completed, stateCommand, idempotencyKey);
        } catch (Ap2MandateException | ParseException | JacksonException | IllegalArgumentException exception) {
            completionStateStore.releaseCompletionStart(stateCommand);
            canaryRecorder.record(
                    provider,
                    command.checkoutId(),
                    CheckoutCanaryOutcome.UNRECOVERABLE_ERROR,
                    null,
                    simpleErrorCode(exception),
                    false
            );
            throw new CheckoutSafetyException("Native checkout completion could not be prepared", exception);
        } catch (RuntimeException exception) {
            if (!remoteCompletionPosted) {
                completionStateStore.releaseCompletionStart(stateCommand);
                throw exception;
            }
            return statusFirstAfterAmbiguousFailure(
                    provider, command.checkoutId(), session, stateCommand, idempotencyKey, exception, callContext);
        }
    }

    public NativeCheckoutResult cancel(
            @NotNull MerchantCartProvider provider,
            @NotNull @Valid NativeCheckoutCancellationCommand command,
            @NotNull UcpSession session
    ) {
        CheckoutToolCallContext callContext = CheckoutToolCallContext.forBuyer(command.buyerIp());
        if (!provider.executionPolicy().isAvailable(CommerceOperation.DIRECT_CHECKOUT_COMPLETION)) {
            return featureDisabledResult(
                    provider,
                    command.checkoutId(),
                    ap2MandateBuilder.isRequired(command.ap2SecurityLock(), session),
                    session
            );
        }
        StartCheckoutCompletionCommand stateCommand = new StartCheckoutCompletionCommand(command.checkoutId());
        try {
            completionStateStore.authorize(new AuthorizeCheckoutCompletionCommand(command.checkoutId(), command.cartId()));
        } catch (CheckoutSafetyException exception) {
            return blockedCancelStatus(provider, command.checkoutId(), session, exception, callContext);
        }
        if (!completionStateStore.tryCancel(stateCommand)) {
            return blockedCancelStatus(provider, command.checkoutId(), session, null, callContext);
        }

        CancelCheckoutRequest cancelRequest = new CancelCheckoutRequest(command.checkoutId(), command.reason());
        byte[] canonicalBody = requestSigner.canonicalCancelBody(cancelRequest);
        Map<String, String> signedHeaders = requestSigner.signedHeaders(
                provider,
                canonicalBody,
                "ucp-cancel-" + UUID.nameUUIDFromBytes(canonicalBody)
        );
        UcpCheckoutToolResult canceled = dispatchService.cancelCheckout(
                provider,
                cancelRequest,
                session,
                signedHeaders,
                callContext
        );
        NativeCheckoutInterpretation result = resultInterpreter.cancelResult(canceled);
        canaryRecorder.record(provider, result);
        if (result.result().status() == NativeCheckoutStatus.CANCELED) {
            completionStateStore.markCanceled(stateCommand);
        }
        return result.result();
    }

    private NativeCheckoutResult featureDisabledResult(
            MerchantCartProvider provider,
            String checkoutId,
            boolean ap2SecurityLock,
            UcpSession session
    ) {
        if (ap2SecurityLock) {
            canaryRecorder.record(
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
        canaryRecorder.record(
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
            throw new CheckoutSafetyException("Buyer consent merchant did not match checkout merchant");
        }
        if (!Objects.equals(consent.checkoutId(), command.checkoutId())) {
            throw new CheckoutSafetyException("Buyer consent checkout did not match requested checkout");
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
            StartCheckoutCompletionCommand stateCommand,
            CheckoutToolCallContext callContext
    ) {
        UcpCheckoutToolResult status = dispatchService.getCheckout(
                provider, new GetCheckoutRequest(checkoutId), session, callContext);
        NativeCheckoutInterpretation result = resultInterpreter.terminalStatusResult(status, nativeAttempted, true);
        if (result != null) {
            canaryRecorder.record(provider, result);
            completeLocalStateIfPossible(stateCommand, result.result());
            return result.result();
        }
        NativeCheckoutInterpretation processing = resultInterpreter.processingResult(status, nativeAttempted);
        canaryRecorder.record(provider, processing);
        return processing.result();
    }

    private NativeCheckoutResult statusFirstAfterLocalStateConflict(
            MerchantCartProvider provider,
            String checkoutId,
            UcpSession session,
            StartCheckoutCompletionCommand stateCommand,
            CheckoutSafetyException originalException,
            CheckoutToolCallContext callContext
    ) {
        try {
            return statusFirstWithoutRepost(provider, checkoutId, session, true, stateCommand, callContext);
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
            RuntimeException originalException,
            CheckoutToolCallContext callContext
    ) {
        try {
            UcpCheckoutToolResult status = dispatchService.getCheckout(
                    provider, new GetCheckoutRequest(checkoutId), session, callContext);
            NativeCheckoutInterpretation terminal = resultInterpreter.terminalStatusResult(status, true, true);
            if (terminal != null) {
                canaryRecorder.record(provider, terminal);
                if (terminal.result().status() == NativeCheckoutStatus.COMPLETED) {
                    completionStateStore.markCompletedFromRemoteStatus(stateCommand);
                }
                idempotencyResponseRecorder.recordCompletionResponse(idempotencyKey, terminal, status.rawResponse());
                return terminal.result();
            }
            NativeCheckoutInterpretation processing = resultInterpreter.processingResult(status, true);
            idempotencyResponseRecorder.recordCompletionResponse(idempotencyKey, processing, status.rawResponse());
            canaryRecorder.record(provider, processing);
            return processing.result();
        } catch (RuntimeException statusException) {
            canaryRecorder.record(
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
        NativeCheckoutInterpretation interpretation = resultInterpreter.completionResponse(result);
        if (isTerminalCompletionStatus(interpretation.result().status())) {
            canaryRecorder.record(provider, interpretation);
            if (interpretation.result().status() == NativeCheckoutStatus.COMPLETED) {
                completionStateStore.markCompletedFromRemoteStatus(stateCommand);
            }
            idempotencyResponseRecorder.recordCompletionResponse(idempotencyKey, interpretation, result.rawResponse());
            return interpretation.result();
        }
        if (interpretation.result().status() == NativeCheckoutStatus.RECOVERABLE_ERROR) {
            completionStateStore.releaseCompletionStart(stateCommand);
        }
        idempotencyResponseRecorder.recordCompletionResponse(idempotencyKey, interpretation, result.rawResponse());
        canaryRecorder.record(provider, interpretation);
        return interpretation.result();
    }

    private boolean isTerminalCompletionStatus(NativeCheckoutStatus status) {
        return status == NativeCheckoutStatus.COMPLETED
                || status == NativeCheckoutStatus.CANCELED
                || status == NativeCheckoutStatus.SCA_REQUIRED;
    }

    private NativeCheckoutResult blockedCancelStatus(
            MerchantCartProvider provider,
            String checkoutId,
            UcpSession session,
            RuntimeException originalException,
            CheckoutToolCallContext callContext
    ) {
        NativeCheckoutResult status = statusFirstWithoutRepost(
                provider,
                checkoutId,
                session,
                true,
                new StartCheckoutCompletionCommand(checkoutId),
                callContext
        );
        if (status.status() == NativeCheckoutStatus.COMPLETED || status.status() == NativeCheckoutStatus.CANCELED) {
            return status;
        }
        CheckoutSafetyException blocked = new CheckoutSafetyException(
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

    private String idempotencyKey(NativeCheckoutCompletionCommand command) {
        if (StringUtils.hasText(command.idempotencyKey())) {
            return command.idempotencyKey().trim();
        }
        return "ucp-complete-" + command.buyerConsentId();
    }

    private String simpleErrorCode(Throwable throwable) {
        return throwable.getClass().getSimpleName();
    }
}
