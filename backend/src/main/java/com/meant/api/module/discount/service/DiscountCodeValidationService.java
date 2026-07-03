package com.meant.api.module.discount.service;

import static com.meant.api.common.util.CollectionUtils.safeNonNullList;

import com.meant.api.module.cart.exception.CartException;
import com.meant.api.module.discount.constant.DiscountCodeStatus;
import com.meant.api.module.discount.service.command.SearchDiscountCodesCommand;
import com.meant.api.module.discount.service.dto.DiscountCodeCandidateEvaluation;
import com.meant.api.module.discount.service.dto.DiscountCodeCandidateSource;
import com.meant.api.module.discount.service.dto.DiscountMerchant;
import com.meant.api.module.merchant.exception.MerchantMcpToolException;
import com.meant.api.module.merchant.service.dto.MerchantCartProvider;
import com.meant.api.plugin.cart.cancel.dto.CancelCartRequest;
import com.meant.api.plugin.cart.common.dto.CartAddItem;
import com.meant.api.plugin.cart.common.dto.UcpCartResponse;
import com.meant.api.plugin.cart.common.dto.UcpCartToolResult;
import com.meant.api.plugin.cart.common.service.MerchantCartPluginDispatchService;
import com.meant.api.plugin.cart.create.dto.CreateCartRequest;
import com.meant.api.plugin.support.UcpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

@Service
@RequiredArgsConstructor
@Slf4j
@Validated
public class DiscountCodeValidationService {

    private static final String VALID_MESSAGE = "Discount code accepted by merchant.";
    private static final String NOT_APPLIED_MESSAGE = "Discount code was not applied by merchant.";
    private static final String TEMPORARY_FAILURE_MESSAGE = "Discount validation is temporarily unavailable.";

    private final MerchantCartPluginDispatchService merchantCartPluginDispatchService;
    private final DiscountCodeExpiryService expiryService;

    public DiscountCodeCandidateEvaluation validate(
            DiscountMerchant merchant,
            @NotNull @Valid SearchDiscountCodesCommand command,
            DiscountCodeCandidateSource candidate,
            Instant now
    ) {
        MerchantCartProvider provider = merchant.cartProvider();
        UcpSession session = UcpSession.start();
        UcpCartToolResult result = null;
        try {
            result = merchantCartPluginDispatchService.createCart(
                    provider,
                    createCartRequest(command, candidate.code()),
                    session
            );
            DiscountCodeStatus status = discountApplied(candidate.code(), result.response())
                    ? DiscountCodeStatus.VALID
                    : DiscountCodeStatus.INVALID;
            return evaluation(
                    candidate,
                    status,
                    status == DiscountCodeStatus.VALID ? VALID_MESSAGE : NOT_APPLIED_MESSAGE,
                    now
            );
        } catch (CartException exception) {
            DiscountCodeStatus status = retryable(exception.getStatus())
                    ? DiscountCodeStatus.FAILED_RETRYABLE
                    : DiscountCodeStatus.INVALID;
            return evaluation(candidate, status, validationMessage(status, exception), now);
        } catch (MerchantMcpToolException exception) {
            return evaluation(candidate, DiscountCodeStatus.FAILED_RETRYABLE, TEMPORARY_FAILURE_MESSAGE, now);
        } finally {
            cancelTemporaryCart(provider, session, result);
        }
    }

    private CreateCartRequest createCartRequest(
            SearchDiscountCodesCommand command,
            String code
    ) {
        return new CreateCartRequest(
                safeNonNullList(command.items()).stream()
                        .map(item -> new CartAddItem(item.productVariantId(), item.quantity()))
                        .toList(),
                command.buyerIdentity(),
                safeNonNullList(command.deliveryAddressesToAdd()),
                safeNonNullList(command.deliveryAddressesToReplace()),
                safeNonNullList(command.selectedDeliveryOptions()),
                List.of(code),
                List.of(),
                null
        );
    }

    private DiscountCodeCandidateEvaluation evaluation(
            DiscountCodeCandidateSource candidate,
            DiscountCodeStatus status,
            String validationMessage,
            Instant now
    ) {
        Instant validUntil = status == DiscountCodeStatus.VALID
                ? expiryService.validUntil(candidate.validUntilText(), now)
                : null;
        return new DiscountCodeCandidateEvaluation(
                candidate,
                status,
                validUntil,
                now,
                expiryService.expiresAt(status, validUntil, now),
                validationMessage
        );
    }

    private boolean retryable(HttpStatusCode status) {
        return status != null && status.is5xxServerError();
    }

    private String validationMessage(DiscountCodeStatus status, CartException exception) {
        if (status == DiscountCodeStatus.FAILED_RETRYABLE) {
            return TEMPORARY_FAILURE_MESSAGE;
        }
        String safeMessage = exception.getSafeMessage();
        return safeMessage == null || safeMessage.isBlank() ? NOT_APPLIED_MESSAGE : safeMessage;
    }

    private boolean discountApplied(String submittedCode, UcpCartResponse response) {
        if (response == null || response.cart() == null) {
            return false;
        }
        String normalizedSubmittedCode = submittedCode.toLowerCase(Locale.ROOT);
        return Stream.of(
                        response.cart().discountCodes(),
                        response.cart().appliedDiscounts(),
                        response.cart().discountAllocations()
                )
                .flatMap(codes -> safeNonNullList(codes).stream())
                .anyMatch(appliedCode -> matches(normalizedSubmittedCode, appliedCode));
    }

    private boolean matches(String normalizedSubmittedCode, UcpCartResponse.AppliedCode appliedCode) {
        String code = appliedCode.code();
        return code != null
                && code.toLowerCase(Locale.ROOT).equals(normalizedSubmittedCode)
                && !Boolean.FALSE.equals(appliedCode.applicable());
    }

    private void cancelTemporaryCart(
            MerchantCartProvider provider,
            UcpSession session,
            UcpCartToolResult result
    ) {
        String cartId = cartId(session, result);
        if (cartId == null || cartId.isBlank()) {
            return;
        }
        try {
            merchantCartPluginDispatchService.cancelCart(provider, new CancelCartRequest(cartId), session);
        } catch (CartException | MerchantMcpToolException exception) {
            log.warn("Could not cancel temporary discount validation cart merchantId={} cartId={}",
                    provider.merchantId(), cartId, exception);
        }
    }

    private String cartId(UcpSession session, UcpCartToolResult result) {
        if (result != null && result.response() != null && result.response().cart() != null) {
            String cartId = result.response().cart().id();
            if (cartId != null && !cartId.isBlank()) {
                return cartId;
            }
        }
        return session.cartId();
    }
}
