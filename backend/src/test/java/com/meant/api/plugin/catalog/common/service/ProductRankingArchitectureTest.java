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
        Path rankingServices = Path.of("src/main/java/com/meant/api/plugin/catalog/common/service");
        List<Path> sources;
        try (var files = Files.list(rankingServices)) {
            sources = new java.util.ArrayList<>(files
                    .filter(path -> path.getFileName().toString().contains("Ranking")
                            || path.getFileName().toString().contains("Diversity"))
                    .toList());
        }
        sources.add(Path.of("src/main/java/com/meant/api/plugin/catalog/common/dto/ProductRankingContext.java"));
        sources.add(Path.of("src/main/java/com/meant/api/module/user/service/UserProductRankingContextFactory.java"));

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
