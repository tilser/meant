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
import com.meant.api.plugin.cart.common.dto.CartBuyer;
import com.meant.api.plugin.cart.common.dto.CartContext;
import com.meant.api.plugin.cart.common.dto.CartDeliveryAddress;
import com.meant.api.plugin.cart.common.dto.CartDeliveryAddressSelection;
import com.meant.api.plugin.cart.common.dto.CartDeliveryOptionSelection;
import com.meant.api.plugin.cart.common.dto.UcpCartResponse;
import com.meant.api.plugin.cart.common.dto.UcpCartToolResult;
import com.meant.api.module.cart.service.MerchantCartPluginDispatchService;
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
        } catch (RuntimeException exception) {
            log.error("Unexpected error validating discount code candidate: {}", candidate.code(), exception);
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
                buyerIdentity(command.buyerIdentity()),
                buyerContext(command.buyerIdentity()),
                deliveryAddresses(command.deliveryAddressesToAdd()),
                deliveryAddresses(command.deliveryAddressesToReplace()),
                deliveryOptions(command.selectedDeliveryOptions()),
                List.of(code),
                List.of(),
                null
        );
    }

    /**
     * Market hint for inventory allocation — without an address_country the merchant treats
     * validation carts as an unknown market and drops the line items as sold out.
     */
    private CartContext buyerContext(SearchDiscountCodesCommand.BuyerIdentity source) {
        String countryCode = source == null ? null : source.countryCode();
        return new CartContext(
                countryCode == null || countryCode.isBlank() ? "US" : countryCode.trim().toUpperCase(Locale.ROOT));
    }

    private CartBuyer buyerIdentity(SearchDiscountCodesCommand.BuyerIdentity source) {
        if (source == null) {
            return null;
        }
        return new CartBuyer(
                blankToNull(source.firstName()), blankToNull(source.lastName()),
                blankToNull(source.email()), blankToNull(source.phoneNumber()));
    }

    private List<CartDeliveryAddressSelection> deliveryAddresses(
            List<SearchDiscountCodesCommand.DeliveryAddressSelection> sources
    ) {
        return safeNonNullList(sources).stream()
                .map(this::deliveryAddress)
                .filter(values -> values.address() != null && !values.address().empty())
                .toList();
    }

    private CartDeliveryAddressSelection deliveryAddress(SearchDiscountCodesCommand.DeliveryAddressSelection source) {
        if (source == null) {
            return new CartDeliveryAddressSelection(null, null, null);
        }
        SearchDiscountCodesCommand.DeliveryAddress address = source.deliveryAddress();
        return new CartDeliveryAddressSelection(null, source.selected(), new CartDeliveryAddress(
                blankToNull(source.id()),
                blankToNull(firstText(source.firstName(), address == null ? null : address.firstName())),
                blankToNull(firstText(source.lastName(), address == null ? null : address.lastName())),
                blankToNull(firstText(source.phoneNumber(), address == null ? null : address.phoneNumber())),
                blankToNull(firstText(source.streetAddress(), address == null ? null : address.streetAddress())),
                blankToNull(firstText(source.extendedAddress(), address == null ? null : address.extendedAddress())),
                blankToNull(firstText(source.city(), address == null ? null : address.city())),
                blankToNull(firstText(source.provinceCode(), address == null ? null : address.provinceCode())),
                blankToNull(firstText(source.postalCode(), address == null ? null : address.postalCode())),
                blankToNull(firstText(source.countryCode(), address == null ? null : address.countryCode()))));
    }

    private List<CartDeliveryOptionSelection> deliveryOptions(
            List<SearchDiscountCodesCommand.DeliveryOptionSelection> sources
    ) {
        return safeNonNullList(sources).stream()
                .map(this::deliveryOption)
                .filter(values -> values.groupId() != null && values.selectedOptionId() != null)
                .toList();
    }

    private CartDeliveryOptionSelection deliveryOption(SearchDiscountCodesCommand.DeliveryOptionSelection source) {
        if (source == null) {
            return new CartDeliveryOptionSelection(null, null, null);
        }
        return new CartDeliveryOptionSelection(null,
                blankToNull(firstText(source.groupId(), source.deliveryGroupId(), source.id())),
                blankToNull(firstText(source.optionHandle(), source.deliveryOptionHandle(), source.selectedOptionId())));
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
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
        return status == null || status.is5xxServerError() || status.value() == 408 || status.value() == 429;
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
        if (appliedCode == null) {
            return false;
        }
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
            log.warn("Could not cancel temporary discount validation cart failure={}",
                    exception.getClass().getSimpleName());
        } catch (RuntimeException exception) {
            log.warn("Unexpected error canceling temporary discount validation cart failure={}",
                    exception.getClass().getSimpleName());
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
