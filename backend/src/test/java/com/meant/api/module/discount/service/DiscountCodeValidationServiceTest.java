package com.meant.api.module.discount.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.meant.api.module.cart.exception.CartException;
import com.meant.api.module.discount.constant.DiscountCodeStatus;
import com.meant.api.module.discount.properties.DiscountCodeSearchProperties;
import com.meant.api.module.discount.service.command.SearchDiscountCodesCommand;
import com.meant.api.module.discount.service.dto.DiscountCodeCandidateSource;
import com.meant.api.module.discount.service.dto.DiscountMerchant;
import com.meant.api.module.merchant.service.dto.MerchantCartProvider;
import com.meant.api.plugin.cart.cancel.dto.CancelCartRequest;
import com.meant.api.plugin.cart.cancel.dto.CancelCartResponse;
import com.meant.api.plugin.cart.common.dto.UcpCartResponse;
import com.meant.api.plugin.cart.common.dto.UcpCartToolResult;
import com.meant.api.plugin.cart.common.service.MerchantCartPluginDispatchService;
import com.meant.api.plugin.cart.create.dto.CreateCartRequest;
import com.meant.api.plugin.support.UcpSession;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

class DiscountCodeValidationServiceTest {

    @Test
    void validatesAcceptedRejectedAndContinuesAfterInvalidCode() {
        FakeMerchantCartPluginDispatchService merchantCartPluginDispatchService =
                new FakeMerchantCartPluginDispatchService();
        DiscountCodeValidationService service = new DiscountCodeValidationService(
                merchantCartPluginDispatchService,
                new DiscountCodeExpiryService(properties())
        );
        DiscountMerchant merchant = merchant();
        SearchDiscountCodesCommand command = command(merchant.id());
        Instant now = Instant.parse("2026-07-03T10:00:00Z");

        var accepted = service.validate(merchant, command, candidate("SAVE10"), now);
        var rejected = service.validate(merchant, command, candidate("BADCODE"), now);
        var nextAccepted = service.validate(merchant, command, candidate("NEXT10"), now);

        assertThat(accepted.status()).isEqualTo(DiscountCodeStatus.VALID);
        assertThat(rejected.status()).isEqualTo(DiscountCodeStatus.INVALID);
        assertThat(nextAccepted.status()).isEqualTo(DiscountCodeStatus.VALID);
        assertThat(merchantCartPluginDispatchService.canceledCartIds).containsExactly("cart-1", "cart-2");
    }

    @Test
    void retryableHttpFailuresAreNotCachedAsInvalid() {
        FakeMerchantCartPluginDispatchService merchantCartPluginDispatchService =
                new FakeMerchantCartPluginDispatchService();
        DiscountCodeValidationService service = new DiscountCodeValidationService(
                merchantCartPluginDispatchService,
                new DiscountCodeExpiryService(properties())
        );
        DiscountMerchant merchant = merchant();
        SearchDiscountCodesCommand command = command(merchant.id());
        Instant now = Instant.parse("2026-07-03T10:00:00Z");

        var rateLimited = service.validate(merchant, command, candidate("RATE_LIMITED"), now);
        var timedOut = service.validate(merchant, command, candidate("TIMEOUT"), now);

        assertThat(rateLimited.status()).isEqualTo(DiscountCodeStatus.FAILED_RETRYABLE);
        assertThat(timedOut.status()).isEqualTo(DiscountCodeStatus.FAILED_RETRYABLE);
    }

    @Test
    void unexpectedCandidateFailureDoesNotStopNextCandidate() {
        FakeMerchantCartPluginDispatchService merchantCartPluginDispatchService =
                new FakeMerchantCartPluginDispatchService();
        DiscountCodeValidationService service = new DiscountCodeValidationService(
                merchantCartPluginDispatchService,
                new DiscountCodeExpiryService(properties())
        );
        DiscountMerchant merchant = merchant();
        SearchDiscountCodesCommand command = command(merchant.id());
        Instant now = Instant.parse("2026-07-03T10:00:00Z");

        var failed = service.validate(merchant, command, candidate("BOOM"), now);
        var nextAccepted = service.validate(merchant, command, candidate("NEXT10"), now);

        assertThat(failed.status()).isEqualTo(DiscountCodeStatus.FAILED_RETRYABLE);
        assertThat(nextAccepted.status()).isEqualTo(DiscountCodeStatus.VALID);
        assertThat(merchantCartPluginDispatchService.canceledCartIds).containsExactly("cart-2");
    }

    private UcpCartToolResult cartResult(String cartId, String code) {
        UcpCartResponse response = new UcpCartResponse(
                null,
                new UcpCartResponse.Cart(
                        cartId,
                        null,
                        null,
                        null,
                        List.of(),
                        null,
                        0,
                        null,
                        null,
                        List.of(new UcpCartResponse.AppliedCode(code, null, true, null)),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of()
                ),
                List.of(),
                List.of()
        );
        return new UcpCartToolResult("https://merchant.example/api/mcp", "{}", response);
    }

    private DiscountCodeCandidateSource candidate(String code) {
        return new DiscountCodeCandidateSource(
                code,
                "Title",
                "Description",
                "https://merchant.example/codes",
                0.9,
                "",
                "",
                ""
        );
    }

    private DiscountMerchant merchant() {
        return new DiscountMerchant(
                UUID.randomUUID(),
                "merchant.example",
                "Merchant",
                "https://merchant.example/api/mcp",
                null,
                false
        );
    }

    private SearchDiscountCodesCommand command(UUID merchantId) {
        return new SearchDiscountCodesCommand(
                UUID.randomUUID(),
                merchantId,
                null,
                List.of(new SearchDiscountCodesCommand.Item("gid://shopify/ProductVariant/1", 1)),
                null,
                List.of(),
                List.of(),
                List.of()
        );
    }

    private DiscountCodeSearchProperties properties() {
        return new DiscountCodeSearchProperties(
                Duration.ofHours(48),
                Duration.ofHours(12),
                Duration.ofHours(1),
                10,
                "test-model:online",
                8
        );
    }

    private class FakeMerchantCartPluginDispatchService extends MerchantCartPluginDispatchService {

        private final List<String> canceledCartIds = new ArrayList<>();

        FakeMerchantCartPluginDispatchService() {
            super(null, null, null);
        }

        @Override
        public UcpCartToolResult createCart(
                MerchantCartProvider provider,
                CreateCartRequest request,
                UcpSession session
        ) {
            String code = request.discountCodes().getFirst();
            if ("BADCODE".equals(code)) {
                throw CartException.rejected("Discount code BADCODE was not accepted by the merchant.");
            }
            if ("RATE_LIMITED".equals(code)) {
                throw new StatusCartException(HttpStatus.TOO_MANY_REQUESTS);
            }
            if ("TIMEOUT".equals(code)) {
                throw new StatusCartException(HttpStatus.REQUEST_TIMEOUT);
            }
            if ("BOOM".equals(code)) {
                throw new IllegalStateException("Unexpected transport failure");
            }
            return cartResult("NEXT10".equals(code) ? "cart-2" : "cart-1", code);
        }

        @Override
        public CancelCartResponse cancelCart(
                MerchantCartProvider provider,
                CancelCartRequest request,
                UcpSession session
        ) {
            canceledCartIds.add(request.cartId());
            return null;
        }
    }

    private static class StatusCartException extends CartException {

        private final HttpStatusCode status;

        private StatusCartException(HttpStatusCode status) {
            super("Cart request failed");
            this.status = status;
        }

        @Override
        public HttpStatusCode getStatus() {
            return status;
        }
    }
}
