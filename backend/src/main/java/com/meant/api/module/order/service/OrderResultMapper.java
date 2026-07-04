package com.meant.api.module.order.service;

import com.meant.api.module.order.constant.OrderState;
import com.meant.api.module.order.entity.MerchantOrder;
import com.meant.api.module.order.entity.MerchantOrderLine;
import com.meant.api.module.order.service.dto.OrderLineResult;
import com.meant.api.module.order.service.dto.OrderSummaryResult;
import com.meant.api.module.order.service.dto.OrderResult;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import org.springframework.stereotype.Component;

@Component
public class OrderResultMapper {

    public OrderResult from(MerchantOrder order) {
        return new OrderResult(
                order.getId(),
                order.getMerchantId(),
                order.getMerchantDomain(),
                order.getMerchantName(),
                order.getRemoteOrderId(),
                displayId(order),
                order.getOrderNumber(),
                order.getState(),
                order.getState().displayStatus(),
                statusNote(order),
                date(order),
                order.getTotalAmount(),
                order.getSubtotalAmount(),
                order.getCurrency(),
                order.getTotalQuantity(),
                order.getOrderStatusUrl(),
                order.getLines().stream()
                        .sorted(Comparator.comparing(MerchantOrderLine::getPosition))
                        .map(line -> lineResult(order, line))
                        .toList(),
                order.getCreatedAt(),
                order.getUpdatedAt()
        );
    }

    public OrderSummaryResult summaryFrom(MerchantOrder order) {
        return new OrderSummaryResult(
                order.getId(),
                order.getMerchantId(),
                order.getMerchantDomain(),
                order.getMerchantName(),
                order.getRemoteOrderId(),
                displayId(order),
                order.getOrderNumber(),
                order.getState(),
                order.getState().displayStatus(),
                statusNote(order),
                date(order),
                order.getTotalAmount(),
                order.getSubtotalAmount(),
                order.getCurrency(),
                order.getTotalQuantity(),
                order.getOrderStatusUrl(),
                order.getCreatedAt(),
                order.getUpdatedAt()
        );
    }

    private OrderLineResult lineResult(MerchantOrder order, MerchantOrderLine line) {
        return new OrderLineResult(
                line.getId(),
                productKey(order, line),
                line.getProductId(),
                line.getProductTitle(),
                order.getMerchantName() == null ? order.getMerchantDomain() : order.getMerchantName(),
                line.getProductVariantId(),
                line.getVariantTitle(),
                line.getSku(),
                line.getImageUrl(),
                line.getProductUrl(),
                line.getQuantity(),
                line.getUnitAmount(),
                line.getTotalAmount(),
                line.getCurrency()
        );
    }

    private String productKey(MerchantOrder order, MerchantOrderLine line) {
        if (hasText(line.getProductVariantId())) {
            return order.getMerchantDomain() + ":" + line.getProductVariantId();
        }
        if (hasText(line.getProductId())) {
            return order.getMerchantDomain() + ":" + line.getProductId();
        }
        return order.getMerchantDomain() + ":" + line.getRemoteOrderLineId();
    }

    private String displayId(MerchantOrder order) {
        if (hasText(order.getOrderName())) {
            return order.getOrderName();
        }
        if (hasText(order.getOrderNumber())) {
            return "#" + order.getOrderNumber();
        }
        return order.getRemoteOrderId();
    }

    private String date(MerchantOrder order) {
        Instant date = firstInstant(order.getPlacedAt(), order.getCreatedAt());
        return DateTimeFormatter.ISO_LOCAL_DATE.withZone(ZoneOffset.UTC).format(date);
    }

    private String statusNote(MerchantOrder order) {
        OrderState state = order.getState();
        return switch (state) {
            case DELIVERED -> "Delivered by the merchant.";
            case IN_TRANSIT -> "In transit: the merchant is fulfilling this order.";
            case CANCELED -> "Canceled by the merchant.";
            case REFUNDED -> "Refunded by the merchant.";
            case UNKNOWN, PROCESSING -> "Confirmed: the merchant is preparing this order.";
        };
    }

    private Instant firstInstant(Instant first, Instant second) {
        return first == null ? second : first;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
