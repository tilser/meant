package com.meant.api.module.cart.service.dto;

import com.meant.api.module.cart.entity.CartLine;
import com.meant.api.plugin.cart.common.dto.UcpCartResponse;
import com.meant.api.plugin.cart.common.support.UcpCartMoney;
import java.time.Instant;
import java.util.UUID;

public record CartLineResult(
        UUID cartLineId,
        String remoteCartLineId,
        String productId,
        String productTitle,
        String productBrand,
        String productVariantId,
        String variantTitle,
        Integer quantity,
        String totalAmount,
        String subtotalAmount,
        String currency,
        String offerKey,
        String canonicalProductKey,
        String selectedOptionsJson,
        String componentsJson,
        String sellingPlanJson,
        String provider,
        UUID merchantIntegrationId,
        String externalMerchantId,
        Instant createdAt,
        Instant updatedAt
) {

    public static CartLineResult from(CartLine line) {
        return from(line, null);
    }

    public static CartLineResult from(CartLine line, UcpCartResponse.Line currentLine) {
        UcpCartResponse.Merchandise merchandise = currentLine == null ? null : currentLine.merchandise();
        UcpCartResponse.Product product = merchandise == null ? null : merchandise.product();
        UcpCartResponse.Money total = currentLine == null || currentLine.cost() == null
                ? null : currentLine.cost().totalAmount();
        UcpCartResponse.Money subtotal = currentLine == null || currentLine.cost() == null
                ? null : currentLine.cost().subtotalAmount();
        return new CartLineResult(
                line.getId(),
                line.getRemoteCartLineId(),
                currentLine == null
                        ? line.getProductId()
                        : preferCurrent(product == null ? null : product.id(), line.getProductId()),
                currentLine == null
                        ? line.getProductTitle()
                        : preferCurrent(product == null ? null : product.title(), line.getProductTitle()),
                line.getProductBrand(),
                line.getProductVariantId(),
                currentLine == null
                        ? line.getVariantTitle()
                        : preferCurrent(merchandise == null ? null : merchandise.title(), line.getVariantTitle()),
                currentLine == null || currentLine.quantity() == null ? line.getQuantity() : currentLine.quantity(),
                currentLine == null ? line.getTotalAmount() : UcpCartMoney.displayAmount(total),
                currentLine == null ? line.getSubtotalAmount() : UcpCartMoney.displayAmount(subtotal),
                currentLine == null ? line.getCurrency() : UcpCartMoney.currency(total, subtotal, line.getCurrency()),
                line.getOfferKey(),
                line.getCanonicalProductKey(),
                line.getSelectedOptionsJson(),
                line.getComponentsJson(),
                line.getSellingPlanJson(),
                line.getProvider(),
                line.getMerchantIntegrationId(),
                line.getExternalMerchantId(),
                line.getCreatedAt(),
                line.getUpdatedAt()
        );
    }

    private static String preferCurrent(String current, String persisted) {
        return current == null || current.isBlank() ? persisted : current;
    }
}
