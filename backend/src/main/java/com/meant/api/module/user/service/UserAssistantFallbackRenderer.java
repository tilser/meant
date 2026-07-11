package com.meant.api.module.user.service;

import com.meant.api.module.user.service.dto.ShoppingFilterResult;
import com.meant.api.module.user.service.dto.UserAssistantPageContext;
import com.meant.api.module.user.service.dto.UserAssistantRoute;
import com.meant.api.module.user.service.dto.UserAssistantToolContext;
import com.meant.api.module.user.service.dto.UserProductSearchProductResult;
import com.meant.api.module.user.service.dto.UserSavedProductResult;
import com.meant.api.module.user.service.dto.UserSettingsResult;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;

@Service
public class UserAssistantFallbackRenderer {

    public String fallbackAnswer(
            UserAssistantRoute route,
            UserSettingsResult settings,
            UserAssistantPageContext pageContext,
            UserAssistantToolContext toolContext,
            List<UserProductSearchProductResult> products
    ) {
        if (route.isSearch() && !products.isEmpty()) {
            String productTitles = String.join(", ", products.stream()
                    .map(UserProductSearchProductResult::title)
                    .filter(title -> title != null && !title.isBlank())
                    .toList());
            return "I found " + products.size() + " strong option"
                    + (products.size() == 1 ? "" : "s")
                    + ": "
                    + (productTitles.isBlank() ? "the top results" : productTitles)
                    + ". The first result is the safest place to start for your current preferences.";
        }
        if (toolContext != null && toolContext.hasSavedProducts()) {
            return savedProductsFallbackAnswer(toolContext.savedProducts());
        }
        List<UserAssistantPageContext.Order> orders = pageContext == null
                ? List.of()
                : safeList(pageContext.orders());
        if (!orders.isEmpty()) {
            UserAssistantPageContext.Order order = orders.getFirst();
            return "Your latest visible order is " + order.id() + ", marked " + order.status()
                    + ". " + value(order.statusNote());
        }
        List<UserAssistantPageContext.Product> visibleProducts = pageContext == null
                ? List.of()
                : safeList(pageContext.visibleProducts());
        if (!visibleProducts.isEmpty()) {
            UserAssistantPageContext.Product product = visibleProducts.stream()
                    .max((left, right) -> Integer.compare(score(left.match()), score(right.match())))
                    .orElseThrow();
            return "From the products visible here, I would start with " + product.name()
                    + ". It has the strongest current match"
                    + (product.match() == null ? "" : " at " + product.match() + "%")
                    + (product.note() == null || product.note().isBlank() ? "." : ": " + product.note());
        }
        List<String> preferences = settings.filters().stream()
                .map(ShoppingFilterResult::label)
                .limit(4)
                .toList();
        return "I can help with product searches, saved items, cart context, and preferences. Your active preferences include "
                + (preferences.isEmpty() ? "none yet" : String.join(", ", preferences))
                + ".";
    }

    private String savedProductsFallbackAnswer(List<UserSavedProductResult> savedProducts) {
        UserSavedProductResult product = savedProducts.stream()
                .max((left, right) -> Integer.compare(score(left.match()), score(right.match())))
                .orElseThrow();
        StringBuilder answer = new StringBuilder();
        answer.append("From your saved products, I would start with ")
                .append(value(product.name()));
        if (product.match() != null) {
            answer.append(". It has the strongest saved match at ")
                    .append(product.match())
                    .append("%");
        }
        if (product.commercialFactsAuthoritative() && product.priceFrom() != null) {
            answer.append(" and starts around ")
                    .append(savedProductPrice(product));
        }
        if (product.note() != null && !product.note().isBlank()) {
            answer.append(". ")
                    .append(product.note());
        } else if (!product.satisfies().isEmpty()) {
            answer.append(". It fits ")
                    .append(String.join(", ", product.satisfies().stream().limit(3).toList()))
                    .append(".");
        } else {
            answer.append(".");
        }
        return answer.toString();
    }

    private String savedProductPrice(UserSavedProductResult product) {
        return product.priceFrom() == null ? "price unavailable" : String.format(Locale.US, "$%.2f", product.priceFrom());
    }

    private String value(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }

    private int score(Integer value) {
        return value == null ? -1 : value;
    }

    private static <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }
}
