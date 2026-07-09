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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
    private Map<String, Object> buyerContext(SearchDiscountCodesCommand.BuyerIdentity source) {
        String countryCode = source == null ? null : source.countryCode();
        Map<String, Object> context = new LinkedHashMap<>();
        context.put(
                "address_country",
                countryCode == null || countryCode.isBlank() ? "US" : countryCode.trim().toUpperCase(Locale.ROOT)
        );
        return context;
    }

    private Map<String, Object> buyerIdentity(SearchDiscountCodesCommand.BuyerIdentity source) {
        if (source == null) {
            return null;
        }
        Map<String, Object> values = new LinkedHashMap<>();
        put(values, "email", source.email());
        put(values, "phone_number", source.phoneNumber());
        put(values, "first_name", source.firstName());
        put(values, "last_name", source.lastName());
        put(values, "country_code", source.countryCode());
        return emptyToNull(values);
    }

    private List<Map<String, Object>> deliveryAddresses(
            List<SearchDiscountCodesCommand.DeliveryAddressSelection> sources
    ) {
        return safeNonNullList(sources).stream()
                .map(this::deliveryAddress)
                .filter(values -> !values.isEmpty())
                .toList();
    }

    private Map<String, Object> deliveryAddress(SearchDiscountCodesCommand.DeliveryAddressSelection source) {
        if (source == null) {
            return Map.of();
        }
        SearchDiscountCodesCommand.DeliveryAddress address = source.deliveryAddress();
        Map<String, Object> values = new LinkedHashMap<>();
        put(values, "id", source.id());
        put(values, "selected", source.selected());
        put(values, "first_name", firstText(source.firstName(), address == null ? null : address.firstName()));
        put(values, "last_name", firstText(source.lastName(), address == null ? null : address.lastName()));
        put(values, "phone_number", firstText(source.phoneNumber(), address == null ? null : address.phoneNumber()));
        put(values, "street_address", firstText(source.streetAddress(), address == null ? null : address.streetAddress()));
        put(values, "extended_address", firstText(source.extendedAddress(), address == null ? null : address.extendedAddress()));
        put(values, "address_locality", firstText(source.city(), address == null ? null : address.city()));
        put(values, "address_region", firstText(source.provinceCode(), address == null ? null : address.provinceCode()));
        put(values, "postal_code", firstText(source.postalCode(), address == null ? null : address.postalCode()));
        put(values, "address_country", firstText(source.countryCode(), address == null ? null : address.countryCode()));
        return values;
    }

    private List<Map<String, Object>> deliveryOptions(
            List<SearchDiscountCodesCommand.DeliveryOptionSelection> sources
    ) {
        return safeNonNullList(sources).stream()
                .map(this::deliveryOption)
                .filter(values -> !values.isEmpty())
                .toList();
    }

    private Map<String, Object> deliveryOption(SearchDiscountCodesCommand.DeliveryOptionSelection source) {
        if (source == null) {
            return Map.of();
        }
        Map<String, Object> values = new LinkedHashMap<>();
        put(values, "group_id", firstText(source.groupId(), source.deliveryGroupId(), source.id()));
        put(values, "option_handle", firstText(
                source.optionHandle(),
                source.deliveryOptionHandle(),
                source.selectedOptionId()
        ));
        return values;
    }

    private Map<String, Object> emptyToNull(Map<String, Object> values) {
        return values.isEmpty() ? null : values;
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private void put(Map<String, Object> destination, String key, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        destination.put(key, value);
    }

    private void put(Map<String, Object> destination, String key, Boolean value) {
        if (value != null) {
            destination.put(key, value);
        }
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
            log.warn("Could not cancel temporary discount validation cart merchantId={} cartId={}",
                    provider.merchantId(), cartId, exception);
        } catch (RuntimeException exception) {
            log.warn("Unexpected error canceling temporary discount validation cart merchantId={} cartId={}",
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
