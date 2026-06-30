package com.meant.api.module.order.constant;

import java.util.Locale;

public enum OrderState {
    UNKNOWN,
    PROCESSING,
    IN_TRANSIT,
    DELIVERED,
    CANCELED,
    REFUNDED;

    public static OrderState fromRemote(
            String status,
            String financialStatus,
            String fulfillmentStatus,
            java.time.Instant canceledAt,
            java.time.Instant closedAt
    ) {
        if (canceledAt != null || contains(status, "cancel")) {
            return CANCELED;
        }
        if (contains(financialStatus, "refund")) {
            return REFUNDED;
        }
        if (contains(fulfillmentStatus, "deliver") || contains(status, "deliver")) {
            return DELIVERED;
        }
        if ((contains(fulfillmentStatus, "fulfill") && !contains(fulfillmentStatus, "unfulfill"))
                || closedAt != null) {
            return DELIVERED;
        }
        if (contains(fulfillmentStatus, "transit")
                || contains(fulfillmentStatus, "ship")
                || contains(status, "transit")
                || contains(status, "ship")) {
            return IN_TRANSIT;
        }
        if (hasText(status) || hasText(financialStatus) || hasText(fulfillmentStatus)) {
            return PROCESSING;
        }
        return UNKNOWN;
    }

    public static OrderState transition(OrderState current, OrderState next) {
        if (next == null || next == UNKNOWN) {
            return current == null ? UNKNOWN : current;
        }
        if (current == null || current == UNKNOWN) {
            return next;
        }
        if (next == REFUNDED) {
            return REFUNDED;
        }
        if (current == REFUNDED) {
            return REFUNDED;
        }
        if (next == CANCELED) {
            return CANCELED;
        }
        if (current == CANCELED) {
            return CANCELED;
        }
        return rank(next) >= rank(current) ? next : current;
    }

    public String displayStatus() {
        return switch (this) {
            case DELIVERED -> "Delivered";
            case IN_TRANSIT -> "In transit";
            case CANCELED -> "Canceled";
            case REFUNDED -> "Refunded";
            case UNKNOWN, PROCESSING -> "Processing";
        };
    }

    private static int rank(OrderState state) {
        return switch (state) {
            case UNKNOWN -> 0;
            case PROCESSING -> 10;
            case IN_TRANSIT -> 20;
            case DELIVERED -> 30;
            case CANCELED, REFUNDED -> 40;
        };
    }

    private static boolean contains(String value, String needle) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(needle);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
