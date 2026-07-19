package com.meant.api.module.agent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.meant.api.module.catalog.service.dto.CanonicalProduct;
import com.meant.api.module.user.constant.UserInventoryCategory;
import com.meant.api.module.user.service.UserCanonicalProductReferencePersistenceService;
import com.meant.api.module.user.service.UserCommerceContextService;
import com.meant.api.module.user.service.UserInventoryProductRehydrationService;
import com.meant.api.module.user.service.dto.UserCommerceContextResult;
import com.meant.api.module.user.service.dto.UserInventoryCommerceReference;
import com.meant.api.module.user.service.dto.UserInventoryProductRehydrationResult;
import com.meant.api.module.user.service.dto.UserInventorySelectedOption;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AgentInventoryProductAnchorServiceTest {

    @Test
    void createsATypedFallbackAnchorThatPreservesThePurchasedSize() {
        UUID userId = UUID.fromString("00000000-0000-0000-0000-000000000121");
        UUID inventoryItemId = UUID.fromString("00000000-0000-0000-0000-000000000122");
        UUID integrationId = UUID.fromString("00000000-0000-0000-0000-000000000123");
        UserCommerceContextService contexts = mock(UserCommerceContextService.class);
        UserInventoryProductRehydrationService rehydration = mock(UserInventoryProductRehydrationService.class);
        UserCanonicalProductReferencePersistenceService persistence =
                mock(UserCanonicalProductReferencePersistenceService.class);
        when(contexts.find(userId)).thenReturn(new UserCommerceContextResult("CZ"));
        UserInventoryCommerceReference commerce = new UserInventoryCommerceReference(
                "SHOPIFY",
                integrationId,
                "merchant-1",
                "shop.example",
                "canonical:shoes",
                "offer:shoes-42",
                "PROVIDER_CATALOG",
                "shopify-global",
                "gid://shopify/Product/1",
                "gid://shopify/ProductVariant/42",
                List.of(new UserInventorySelectedOption("variant", "Size", "42"))
        );
        when(rehydration.rehydrate(any())).thenReturn(new UserInventoryProductRehydrationResult(
                inventoryItemId, commerce, null, "Trail Shoes", UserInventoryCategory.APPAREL,
                null, null, null, null, null));
        AgentInventoryProductAnchorService service = new AgentInventoryProductAnchorService(
                contexts, rehydration, persistence);

        var result = service.anchor(userId, inventoryItemId);

        ArgumentCaptor<List<CanonicalProduct>> products = ArgumentCaptor.forClass(List.class);
        verify(persistence).replace(org.mockito.ArgumentMatchers.eq(userId), products.capture());
        CanonicalProduct product = products.getValue().getFirst();
        assertThat(result.canonicalProductKey()).isEqualTo("canonical:shoes");
        assertThat(product.title()).isEqualTo("Trail Shoes");
        assertThat(product.offers().getFirst().selectedOptions())
                .singleElement()
                .satisfies(option -> {
                    assertThat(option.name()).isEqualTo("Size");
                    assertThat(option.value()).isEqualTo("42");
                });
    }
}
