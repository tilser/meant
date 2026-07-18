package com.meant.api.module.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.meant.api.module.catalog.service.CatalogProductRehydrationService;
import com.meant.api.module.catalog.service.dto.CatalogProductReference;
import com.meant.api.module.catalog.service.dto.CatalogProductRehydrationResult;
import com.meant.api.module.catalog.service.dto.CatalogRehydrationContext;
import com.meant.api.module.catalog.service.dto.CatalogRehydrationFailureKind;
import com.meant.api.module.catalog.service.dto.CatalogRehydrationStatus;
import com.meant.api.module.catalog.service.dto.ExternalIdentifierType;
import com.meant.api.module.user.constant.UserInventoryCategory;
import com.meant.api.module.user.constant.UserInventorySource;
import com.meant.api.module.user.entity.UserInventoryItem;
import com.meant.api.module.user.repository.UserInventoryItemRepository;
import com.meant.api.module.user.service.query.RehydrateUserInventoryProductQuery;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class UserInventoryProductRehydrationServiceTest {
    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000015");
    private static final UUID ITEM_ID = UUID.fromString("00000000-0000-0000-0000-000000000115");

    @Mock private UserInventoryItemRepository repository;
    @Mock private CatalogProductRehydrationService catalogProductRehydrationService;
    private UserInventoryProductRehydrationService service;

    @BeforeEach
    void setUp() {
        service = new UserInventoryProductRehydrationService(
                repository, catalogProductRehydrationService, new ObjectMapper());
    }

    @Test
    void rehydratesExactProviderNeutralProductAndVariantReference() {
        UUID integrationId = UUID.randomUUID();
        UserInventoryItem item = baseItem()
                .provider("SHOPIFY")
                .merchantIntegrationId(integrationId)
                .externalMerchantId("merchant-1")
                .externalMerchantDomain("shop.example")
                .canonicalProductKey("canonical-shoe")
                .offerKey("offer-shoe-42")
                .sourceType("PROVIDER_CATALOG")
                .sourceIdentity("shopify-global")
                .externalProductId("product-1")
                .externalVariantId("variant-size-42")
                .selectedOptionsJson("[{\"group\":\"variant-option\",\"name\":\"Size\",\"value\":\"42\"}]")
                .build();
        when(repository.findByIdAndUserId(ITEM_ID, USER_ID)).thenReturn(Optional.of(item));
        when(catalogProductRehydrationService.rehydrate(
                any(CatalogProductReference.class), any(CatalogRehydrationContext.class))).thenAnswer(invocation ->
                CatalogProductRehydrationResult.failed(
                        invocation.getArgument(0),
                        CatalogRehydrationStatus.DEGRADED,
                        CatalogRehydrationFailureKind.UPSTREAM_UNAVAILABLE
                ));

        var result = service.rehydrate(new RehydrateUserInventoryProductQuery(USER_ID, ITEM_ID, "us"));

        assertThat(result.fallbackName()).isEqualTo("Trail Shoe");
        assertThat(result.fallbackCategory()).isEqualTo(UserInventoryCategory.APPAREL);
        assertThat(result.commerceReference().merchantIntegrationId()).isEqualTo(integrationId);
        assertThat(result.rehydration().reference()).satisfies(reference -> {
            assertThat(reference.interactionKey()).isEqualTo("offer-shoe-42");
            assertThat(reference.discoverySource().provider().value()).isEqualTo("SHOPIFY");
            assertThat(reference.localRouting().merchantIntegrationId()).isEqualTo(integrationId);
            assertThat(reference.externalProductReference().type()).isEqualTo(ExternalIdentifierType.PRODUCT);
            assertThat(reference.externalProductReference().value()).isEqualTo("product-1");
            assertThat(reference.externalVariantReference().value()).isEqualTo("variant-size-42");
            assertThat(reference.selectedOptions()).singleElement().satisfies(option -> {
                assertThat(option.name()).isEqualTo("Size");
                assertThat(option.value()).isEqualTo("42");
            });
        });
    }

    @Test
    void legacyInventoryUsesNameAndCategoryFallbackWithoutRemoteCall() {
        UserInventoryItem item = baseItem().build();
        when(repository.findByIdAndUserId(ITEM_ID, USER_ID)).thenReturn(Optional.of(item));

        var result = service.rehydrate(new RehydrateUserInventoryProductQuery(USER_ID, ITEM_ID));

        assertThat(result.commerceReference()).isNull();
        assertThat(result.rehydration()).isNull();
        assertThat(result.currentFactsAvailable()).isFalse();
        assertThat(result.fallbackName()).isEqualTo("Trail Shoe");
        assertThat(result.fallbackCategory()).isEqualTo(UserInventoryCategory.APPAREL);
        verify(catalogProductRehydrationService, never()).rehydrate(
                any(CatalogProductReference.class), any(CatalogRehydrationContext.class));
    }

    private UserInventoryItem.UserInventoryItemBuilder baseItem() {
        Instant now = Instant.parse("2026-07-18T10:00:00Z");
        return UserInventoryItem.builder()
                .id(ITEM_ID)
                .userId(USER_ID)
                .source(UserInventorySource.MEANT_PURCHASE)
                .sourceProductKey("shop.example:variant-size-42")
                .name("Trail Shoe")
                .category(UserInventoryCategory.APPAREL)
                .quantity(1)
                .attributes("[]")
                .consumable(false)
                .restockEnabled(false)
                .createdAt(now)
                .updatedAt(now);
    }
}
