package com.meant.api.plugin.catalog.common.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProductRankingArchitectureTest {

    @Test
    void providerNeutralRankingAndUserContextContainNoShopifyTypesOrProviderBranches() throws IOException {
        List<Path> sources = List.of(
                Path.of("src/main/java/com/meant/api/plugin/catalog/common/service/ProductRankingService.java"),
                Path.of("src/main/java/com/meant/api/plugin/catalog/common/service/ProductRankingFeatureExtractor.java"),
                Path.of("src/main/java/com/meant/api/plugin/catalog/common/service/OfferRankingService.java"),
                Path.of("src/main/java/com/meant/api/plugin/catalog/common/service/ProductRankingModel.java"),
                Path.of("src/main/java/com/meant/api/plugin/catalog/common/dto/ProductRankingContext.java"),
                Path.of("src/main/java/com/meant/api/module/user/service/UserProductRankingContextFactory.java")
        );

        assertThat(sources).allSatisfy(source -> {
            assertThat(source).exists();
            String code = Files.readString(source);
            assertThat(code)
                    .doesNotContain("plugin.catalog.shopify")
                    .doesNotContain("Shopify")
                    .doesNotContain("case \"SHOPIFY\"")
                    .doesNotContain("equals(\"SHOPIFY\")");
        });
        assertThat(ProductRankingService.class.getDeclaredFields())
                .allSatisfy(field -> assertThat(field.getType().getPackageName())
                        .doesNotContain("shopify"));
    }
}
