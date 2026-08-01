package com.meant.api.module.agent.service.tool;

import static org.assertj.core.api.Assertions.assertThat;

import com.meant.api.module.agent.service.dto.AgentToolDescriptor;
import java.lang.reflect.Field;
import java.util.List;
import org.junit.jupiter.api.Test;

class AgentContextCatalogToolSchemaTest {

    private static final List<Class<? extends AgentTool>> TOOL_TYPES = List.of(
            GetUserPreferencesAgentTool.class,
            SearchInventoryAgentTool.class,
            GetInventoryItemAgentTool.class,
            ListRecentOrdersAgentTool.class,
            GetOrderAgentTool.class,
            ListSavedProductsAgentTool.class,
            SearchCatalogAgentTool.class,
            GetProductAgentTool.class,
            SelectProductVariantAgentTool.class,
            FindSimilarProductsAgentTool.class,
            CompareProductsAgentTool.class,
            GetProductReviewsAgentTool.class,
            FindDiscountCodesAgentTool.class,
            PickRecommendedProductAgentTool.class
    );

    @Test
    void everyReadToolHasAStrictSchemaWithoutCallerControlledIdentity() throws Exception {
        List<AgentToolDescriptor> descriptors = TOOL_TYPES.stream()
                .map(this::descriptor)
                .toList();

        assertThat(descriptors).hasSize(14);
        assertThat(descriptors).extracting(AgentToolDescriptor::name).doesNotHaveDuplicates();
        assertThat(descriptors).allSatisfy(descriptor -> {
            assertThat(descriptor.inputSchemaJson())
                    .contains("\"additionalProperties\":false")
                    .doesNotContain("userId", "ownerId");
            assertThat(descriptor.riskClass().name()).isEqualTo("READ");
        });
    }

    private AgentToolDescriptor descriptor(Class<? extends AgentTool> type) {
        try {
            Field field = type.getDeclaredField("DESCRIPTOR");
            field.setAccessible(true);
            return (AgentToolDescriptor) field.get(null);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Missing static descriptor on " + type.getSimpleName(), exception);
        }
    }
}
