package com.meant.api.module.user.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.meant.api.module.merchant.service.dto.MerchantSemanticProductResult;
import com.meant.api.module.user.entity.UserProductSearch;
import com.meant.api.module.user.entity.UserProductSearchResultItem;
import com.meant.api.module.user.repository.UserProductRecommendationExplanationRepository;
import com.meant.api.module.user.repository.UserProductRecommendationFilterMatchRepository;
import com.meant.api.module.user.repository.UserProductSearchRepository;
import com.meant.api.module.user.repository.UserProductSearchResultItemRepository;
import com.meant.api.module.user.service.dto.UserProductRecommendationExplanationResult;
import com.meant.api.module.user.service.dto.UserProductSearchProductSnapshot;
import com.meant.api.module.user.service.dto.UserProductSearchResult;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;

class UserProductSearchPersistenceServiceTest {

    @Test
    void saveSearchSkipsProductsWithoutExplanations() {
        List<UserProductSearchResultItem> savedItems = new ArrayList<>();
        UserProductSearchPersistenceService service = new UserProductSearchPersistenceService(
                searchRepository(),
                resultItemRepository(savedItems),
                unusedRepository(UserProductRecommendationExplanationRepository.class),
                unusedRepository(UserProductRecommendationFilterMatchRepository.class)
        );
        UserProductRecommendationExplanationResult explanation = new UserProductRecommendationExplanationResult(
                "merchant.example:tee",
                "hash-tee",
                "Organic cotton matches your profile.",
                List.of("organic-cotton"),
                List.of()
        );

        UserProductSearchResult result = service.saveSearch(
                UUID.randomUUID(),
                "cotton basics",
                "cotton basics",
                "profile-hash",
                "v1",
                Instant.now(),
                Instant.now().plusSeconds(3600),
                List.of(
                        snapshot("merchant.example:tee", "hash-tee", "tee", "Organic Cotton Tee", 1),
                        snapshot("merchant.example:socks", "hash-socks", "socks", "Organic Cotton Socks", 2)
                ),
                Map.of(explanation.productKey(), explanation)
        );

        assertThat(result.products()).singleElement()
                .satisfies(product -> {
                    assertThat(product.productKey()).isEqualTo("merchant.example:tee");
                    assertThat(product.whyMeantForYou()).isEqualTo("Organic cotton matches your profile.");
                });
        assertThat(savedItems).singleElement()
                .extracting(UserProductSearchResultItem::getProductKey)
                .isEqualTo("merchant.example:tee");
    }

    private UserProductSearchRepository searchRepository() {
        return repository(UserProductSearchRepository.class, (proxy, method, args) -> switch (method.getName()) {
            case "findByUserIdAndNormalizedQueryAndProfileHashAndSearchVersion" -> Optional.empty();
            case "save" -> args[0];
            default -> throw new UnsupportedOperationException(method.getName());
        });
    }

    private UserProductSearchResultItemRepository resultItemRepository(
            List<UserProductSearchResultItem> savedItems
    ) {
        return repository(UserProductSearchResultItemRepository.class, (proxy, method, args) -> switch (method.getName()) {
            case "deleteBySearchId" -> null;
            case "saveAll" -> {
                savedItems.clear();
                Iterable<UserProductSearchResultItem> items = resultItems(args[0]);
                StreamSupport.stream(items.spliterator(), false)
                        .forEach(savedItems::add);
                yield savedItems;
            }
            default -> throw new UnsupportedOperationException(method.getName());
        });
    }

    @SuppressWarnings("unchecked")
    private Iterable<UserProductSearchResultItem> resultItems(Object value) {
        return (Iterable<UserProductSearchResultItem>) value;
    }

    private <T> T unusedRepository(Class<T> type) {
        return repository(type, (proxy, method, args) -> {
            throw new UnsupportedOperationException(method.getName());
        });
    }

    private <T> T repository(Class<T> type, InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(
                type.getClassLoader(),
                new Class<?>[]{type},
                handler
        ));
    }

    private UserProductSearchProductSnapshot snapshot(
            String productKey,
            String productHash,
            String productId,
            String title,
            int rank
    ) {
        MerchantSemanticProductResult product = new MerchantSemanticProductResult(
                UUID.randomUUID(),
                "merchant.example",
                "Merchant",
                "https://merchant.example/mcp",
                1,
                0.9d,
                0.8d,
                productId,
                title,
                "<p>Organic cotton.</p>",
                "https://merchant.example/products/" + productId,
                "https://merchant.example/" + productId + ".jpg",
                3800L,
                3800L,
                "USD",
                true,
                null,
                "Organic cotton.",
                "https://merchant.example/" + productId + ".jpg",
                List.of(),
                List.of(),
                "38.00",
                "38.00",
                "USD",
                1,
                false,
                List.of(),
                "variant-1",
                "Default",
                List.of(),
                "38.00",
                "USD",
                "https://merchant.example/" + productId + ".jpg",
                title,
                true,
                rank,
                0.92d,
                rank
        );
        return new UserProductSearchProductSnapshot(productKey, productHash, product);
    }
}
