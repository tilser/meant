package com.meant.api.module.user.service.dto;

import com.meant.api.module.user.constant.UserProductSearchAgent;
import java.util.List;

public record UserProductSearchStreamEvent(
        String type,
        String agent,
        String label,
        String productKey,
        UserProductSearchProductResult product,
        List<UserProductSearchProductResult> products,
        String query,
        String normalizedQuery,
        String profileHash,
        Boolean cached,
        Integer offset,
        Integer limit,
        Integer nextOffset,
        Boolean hasMore,
        String message
) {

    public UserProductSearchStreamEvent {
        products = products == null ? List.of() : List.copyOf(products);
    }

    public static UserProductSearchStreamEvent phase(String agent, String label) {
        return new UserProductSearchStreamEvent(
                "phase",
                agent,
                label,
                null,
                null,
                List.of(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    public static UserProductSearchStreamEvent product(
            String agent,
            String label,
            UserProductSearchProductResult product
    ) {
        return new UserProductSearchStreamEvent(
                "product",
                agent,
                label,
                product.productKey(),
                product,
                List.of(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    public static UserProductSearchStreamEvent productUpdate(
            String agent,
            String label,
            UserProductSearchProductResult product
    ) {
        return new UserProductSearchStreamEvent(
                "product_update",
                agent,
                label,
                product.productKey(),
                product,
                List.of(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    public static UserProductSearchStreamEvent rankUpdate(
            String agent,
            String label,
            List<UserProductSearchProductResult> products
    ) {
        return new UserProductSearchStreamEvent(
                "rank_update",
                agent,
                label,
                null,
                null,
                products,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    public static UserProductSearchStreamEvent done(UserProductSearchResult result) {
        return new UserProductSearchStreamEvent(
                "done",
                UserProductSearchAgent.CURATOR.getValue(),
                "Curated results ready",
                null,
                null,
                result.products(),
                result.query(),
                result.normalizedQuery(),
                result.profileHash(),
                result.cached(),
                result.offset(),
                result.limit(),
                result.nextOffset(),
                result.hasMore(),
                null
        );
    }

    public static UserProductSearchStreamEvent error(String message) {
        return new UserProductSearchStreamEvent(
                "error",
                UserProductSearchAgent.SEARCH.getValue(),
                "Search failed",
                null,
                null,
                List.of(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                message
        );
    }
}
