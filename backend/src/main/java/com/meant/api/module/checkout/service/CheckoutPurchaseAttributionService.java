package com.meant.api.module.checkout.service;

import com.meant.api.module.cart.entity.Cart;
import com.meant.api.module.cart.entity.CartLine;
import com.meant.api.module.cart.repository.CartRepository;
import com.meant.api.module.catalog.service.dto.ProductAttribute;
import com.meant.api.module.checkout.exception.CheckoutAttributionException;
import com.meant.api.module.checkout.repository.CheckoutPurchaseAttributionRepository;
import com.meant.api.module.checkout.service.command.RecordCheckoutOpenedCommand;
import com.meant.api.module.user.service.UserInventoryService;
import com.meant.api.module.user.service.command.ImportPurchasedInventoryItemsCommand;
import com.meant.api.module.user.service.dto.UserInventoryCommerceReference;
import com.meant.api.module.user.service.dto.UserInventorySelectedOption;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Service
@Validated
@RequiredArgsConstructor
public class CheckoutPurchaseAttributionService {
    private static final TypeReference<List<ProductAttribute>> PRODUCT_ATTRIBUTES = new TypeReference<>() {
    };

    private final CartRepository cartRepository;
    private final CheckoutPurchaseAttributionRepository attributionRepository;
    private final UserInventoryService userInventoryService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Transactional
    public boolean record(@NotNull @Valid RecordCheckoutOpenedCommand command) {
        Cart cart = cartRepository.findForCheckoutUpdate(command.cartId(), command.userId())
                .orElseThrow(() -> CheckoutAttributionException.forbidden(
                        "Checkout cart is missing or belongs to another user"));
        if (!Objects.equals(cart.getCheckoutAttemptId(), command.checkoutAttemptId())) {
            throw CheckoutAttributionException.conflict("Checkout attempt is stale");
        }

        List<ImportPurchasedInventoryItemsCommand.PurchasedItem> items = cart.getLines().stream()
                .filter(line -> line.getQuantity() != null && line.getQuantity() > 0)
                .map(line -> purchasedItem(cart, line))
                .toList();
        if (items.isEmpty()) {
            throw CheckoutAttributionException.conflict("Checkout cart has no attributable lines");
        }

        Instant purchasedAt = clock.instant();
        int reserved = attributionRepository.reserve(
                UUID.randomUUID(),
                command.userId(),
                command.cartId(),
                command.checkoutAttemptId(),
                command.rail().name(),
                command.trigger().name(),
                command.embeddedSessionId(),
                purchasedAt,
                purchasedAt
        );
        if (reserved == 0) {
            return false;
        }

        userInventoryService.importPurchasedItems(new ImportPurchasedInventoryItemsCommand(
                command.userId(), command.checkoutAttemptId(), purchasedAt, items));
        return true;
    }

    private ImportPurchasedInventoryItemsCommand.PurchasedItem purchasedItem(Cart cart, CartLine line) {
        return new ImportPurchasedInventoryItemsCommand.PurchasedItem(
                productKey(cart, line),
                null,
                firstText(line.getProductTitle(), line.getVariantTitle(), line.getExternalProductId(),
                        line.getProductVariantId()),
                firstText(line.getProductBrand(), cart.getMerchantDomain()),
                line.getImageUrl(),
                line.getProductUrl(),
                line.getQuantity(),
                commerceReference(cart, line)
        );
    }

    private UserInventoryCommerceReference commerceReference(Cart cart, CartLine line) {
        String provider = firstText(line.getProvider(), cart.getProvider());
        if (!hasText(provider) || !hasText(line.getExternalProductId())
                || !hasText(line.getSourceType()) || !hasText(line.getSourceIdentity())) {
            return null;
        }
        return new UserInventoryCommerceReference(
                provider,
                line.getMerchantIntegrationId() == null
                        ? cart.getMerchantIntegrationId() : line.getMerchantIntegrationId(),
                firstText(line.getExternalMerchantId(), cart.getExternalMerchantId()),
                cart.getRoutingDomain(),
                cart.getMerchantDomain(),
                line.getCanonicalProductKey(),
                line.getOfferKey(),
                line.getSourceType(),
                line.getSourceIdentity(),
                line.getExternalProductId(),
                firstText(line.getExternalVariantId(), line.getProductVariantId()),
                selectedOptions(line.getSelectedOptionsJson())
        );
    }

    private List<UserInventorySelectedOption> selectedOptions(String value) {
        if (!hasText(value)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(value, PRODUCT_ATTRIBUTES).stream()
                    .filter(Objects::nonNull)
                    .filter(option -> hasText(option.name()) && hasText(option.value()))
                    .map(option -> new UserInventorySelectedOption(
                            option.group(), option.name(), option.value()))
                    .toList();
        } catch (JacksonException exception) {
            return List.of();
        }
    }

    private String productKey(Cart cart, CartLine line) {
        if (hasText(line.getOfferKey())) {
            return line.getOfferKey().trim();
        }
        return firstText(cart.getMerchantDomain(), cart.getProvider(), cart.getId().toString())
                + ':' + line.getProductVariantId();
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
