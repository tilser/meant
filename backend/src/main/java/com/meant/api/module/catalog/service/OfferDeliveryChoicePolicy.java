package com.meant.api.module.catalog.service;

import com.meant.api.module.catalog.service.dto.Money;
import com.meant.api.module.catalog.service.dto.Offer;
import com.meant.api.module.catalog.service.dto.OfferDelivery;
import com.meant.api.module.catalog.service.dto.ProductRankingContext;
import java.util.Comparator;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

/** Selects one destination-eligible delivery option used coherently for cost and speed. */
@Component
public class OfferDeliveryChoicePolicy {

    Choice choose(Offer offer, ProductRankingContext context) {
        Set<String> destinations = destinations(context);
        if (destinations.isEmpty()) return Choice.unknown();
        String currency = currency(context);
        OfferDelivery selected = offer.delivery().stream()
                .filter(delivery -> delivery.destinationRegion() != null)
                .filter(delivery -> destinations.contains(normalized(delivery.destinationRegion())))
                .sorted(order(currency))
                .findFirst()
                .orElse(null);
        if (selected == null) return Choice.unknown();
        Long landed = landedPrice(offer, selected, currency);
        Integer deliveryScore = deliveryScore(selected);
        return new Choice(selected, landed, deliveryScore);
    }

    private Comparator<OfferDelivery> order(String currency) {
        return Comparator
                .comparing((OfferDelivery delivery) -> comparableCost(delivery.cost(), currency) == null)
                .thenComparing(delivery -> Objects.requireNonNullElse(comparableCost(delivery.cost(), currency), Long.MAX_VALUE))
                .thenComparing(delivery -> Objects.requireNonNullElse(delivery.maximumBusinessDays(), Integer.MAX_VALUE))
                .thenComparing(delivery -> Objects.requireNonNullElse(delivery.minimumBusinessDays(), Integer.MAX_VALUE))
                .thenComparing(delivery -> delivery.method().name())
                .thenComparing(delivery -> Objects.requireNonNullElse(delivery.destinationRegion(), ""));
    }

    private Long landedPrice(Offer offer, OfferDelivery delivery, String currency) {
        Long cost = comparableCost(delivery.cost(), currency);
        if (offer.price() == null || currency == null || !currency.equals(offer.price().currency()) || cost == null) return null;
        try {
            return Math.addExact(offer.price().minorUnits(), cost);
        } catch (ArithmeticException exception) {
            return null;
        }
    }

    private Integer deliveryScore(OfferDelivery delivery) {
        Integer days = delivery.maximumBusinessDays() == null
                ? delivery.minimumBusinessDays() : delivery.maximumBusinessDays();
        return days == null ? null : Math.max(2_000, 10_000 - Math.min(days, 40) * 200);
    }

    private Long comparableCost(Money cost, String currency) {
        return cost == null || currency == null || !currency.equals(cost.currency()) ? null : cost.minorUnits();
    }

    private Set<String> destinations(ProductRankingContext context) {
        if (context.searchContext() == null) return Set.of();
        return Stream.of(context.searchContext().addressRegion(), context.searchContext().addressCountry())
                .map(OfferDeliveryChoicePolicy::normalized).filter(Objects::nonNull).collect(Collectors.toSet());
    }

    private String currency(ProductRankingContext context) {
        return context.searchContext() == null || context.searchContext().currency() == null
                ? null : context.searchContext().currency().trim().toUpperCase(Locale.ROOT);
    }

    private static String normalized(String value) {
        return value == null || value.isBlank() ? null : value.trim().toLowerCase(Locale.ROOT);
    }

    record Choice(OfferDelivery delivery, Long landedPrice, Integer deliveryScore) {
        static Choice unknown() { return new Choice(null, null, null); }
    }
}
