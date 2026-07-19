package com.meant.api.module.agent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.meant.api.module.agent.constant.AgentToolRisk;
import com.meant.api.module.agent.service.dto.AgentModelToolCall;
import com.meant.api.module.agent.service.dto.AgentProductClarification;
import com.meant.api.module.agent.service.dto.AgentToolDescriptor;
import com.meant.api.module.agent.service.dto.AgentToolExecutionContext;
import com.meant.api.module.agent.service.dto.AgentVisibleProductReference;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AgentProductClarificationServiceTest {

    private AgentToolRegistry toolRegistry;
    private AgentToolAuthorizationPolicy authorizationPolicy;
    private AgentMutationTargetPolicy mutationTargetPolicy;
    private AgentProductClarificationService service;

    @BeforeEach
    void setUp() {
        toolRegistry = mock(AgentToolRegistry.class);
        authorizationPolicy = mock(AgentToolAuthorizationPolicy.class);
        mutationTargetPolicy = mock(AgentMutationTargetPolicy.class);
        service = new AgentProductClarificationService(
                toolRegistry,
                authorizationPolicy,
                mutationTargetPolicy
        );
        when(toolRegistry.required(anyString())).thenAnswer(invocation -> {
            String name = invocation.getArgument(0);
            AgentTool tool = mock(AgentTool.class);
            when(tool.descriptor()).thenReturn(descriptor(name));
            return tool;
        });
    }

    @Test
    void unresolvedAuthorizedMutationProducesVisibleChoicesInTheirExactOrder() {
        AgentToolExecutionContext context = context("Add the third blue one to my cart");
        List<AgentVisibleProductReference> products = products();
        AgentToolDescriptor descriptor = descriptor("prepare_carts");
        when(authorizationPolicy.authorized(context, descriptor)).thenReturn(true);
        when(mutationTargetPolicy.requiresProductClarification(context, "prepare_carts"))
                .thenReturn(true);
        when(mutationTargetPolicy.productClarificationCandidates(context, "prepare_carts"))
                .thenReturn(products);

        Optional<AgentProductClarification> result = service.preflight(
                context,
                List.of(new AgentModelToolCall(
                        "call-1",
                        "prepare_carts",
                        "{\"offers\":[{\"offerKey\":\"offer-red\"}]}"
                ))
        );

        assertThat(result).contains(new AgentProductClarification(
                "prepare_carts",
                "Add the third blue one to my cart",
                products
        ));
    }

    @Test
    void wrongModelArgumentsDoNotAskUserWhenTheirTargetWasAlreadyExact() {
        AgentToolExecutionContext context = context("Add the third one to my cart");
        AgentToolDescriptor descriptor = descriptor("prepare_carts");
        when(authorizationPolicy.authorized(context, descriptor)).thenReturn(true);
        when(mutationTargetPolicy.requiresProductClarification(context, "prepare_carts"))
                .thenReturn(false);

        Optional<AgentProductClarification> result = service.preflight(
                context,
                List.of(new AgentModelToolCall(
                        "call-1",
                        "prepare_carts",
                        "{\"offers\":[{\"offerKey\":\"wrong-offer\"}]}"
                ))
        );

        assertThat(result).isEmpty();
        verify(authorizationPolicy, never()).authorizedInvocation(
                context,
                descriptor,
                "{\"offers\":[{\"offerKey\":\"wrong-offer\"}]}"
        );
    }

    @Test
    void aHallucinatedReadDoesNotTurnAnUnrelatedMessageIntoAProductQuestion() {
        AgentToolExecutionContext context = context("Hello there");

        assertThat(service.preflight(
                context,
                List.of(new AgentModelToolCall(
                        "call-1",
                        "get_product",
                        "{\"canonicalProductKey\":\"product-blue\"}"
                ))
        )).isEmpty();
        verify(authorizationPolicy, never()).authorized(
                context,
                descriptor("get_product")
        );
    }

    @Test
    void anUnresolvedReviewRequestProducesTheSameOrderedProductChoices() {
        AgentToolExecutionContext context = context("Show reviews for the third blue one");
        List<AgentVisibleProductReference> products = products();
        AgentToolDescriptor descriptor = descriptor("get_product_reviews");
        when(authorizationPolicy.authorized(context, descriptor)).thenReturn(true);
        when(mutationTargetPolicy.requiresProductClarification(context, "get_product_reviews"))
                .thenReturn(true);
        when(mutationTargetPolicy.productClarificationCandidates(context, "get_product_reviews"))
                .thenReturn(products);

        assertThat(service.preflight(
                context,
                List.of(new AgentModelToolCall(
                        "call-1",
                        "get_product_reviews",
                        "{\"canonicalProductKey\":\"product-red\"}"
                ))
        )).contains(new AgentProductClarification(
                "get_product_reviews",
                "Show reviews for the third blue one",
                products
        ));
    }

    @Test
    void unresolvedIntentDoesNotDependOnTheModelCallingATool() {
        AgentToolExecutionContext context = context("Add the blue cap to my cart");
        List<AgentVisibleProductReference> products = products();
        when(authorizationPolicy.authorized(
                context,
                descriptor("prepare_carts")
        )).thenReturn(true);
        when(mutationTargetPolicy.requiresProductClarification(context, "prepare_carts"))
                .thenReturn(true);
        when(mutationTargetPolicy.productClarificationCandidates(context, "prepare_carts"))
                .thenReturn(products);

        assertThat(service.unresolvedIntent(context))
                .contains(new AgentProductClarification(
                        "prepare_carts",
                        "Add the blue cap to my cart",
                        products
                ));
    }

    @Test
    void unresolvedIntentDoesNotClarifyAnUnqualifiedDelegatedMissionCartAddition() {
        AgentToolExecutionContext context = context("Prepare everything I need for the picnic");
        AgentToolDescriptor descriptor = descriptor("prepare_carts");
        when(authorizationPolicy.authorized(context, descriptor)).thenReturn(true);
        when(authorizationPolicy.isUnqualifiedDelegatedCartAddition(context, "prepare_carts"))
                .thenReturn(true);

        assertThat(service.unresolvedIntent(context)).isEmpty();
        verify(mutationTargetPolicy, never()).requiresProductClarification(
                context, "prepare_carts");
    }

    @Test
    void preflightDoesNotClarifyAnUnqualifiedDelegatedMissionCartAddition() {
        AgentToolExecutionContext context = context("Prepare everything I need for the picnic");
        AgentToolDescriptor descriptor = descriptor("prepare_carts");
        when(authorizationPolicy.authorized(context, descriptor)).thenReturn(true);
        when(authorizationPolicy.isUnqualifiedDelegatedCartAddition(context, "prepare_carts"))
                .thenReturn(true);

        assertThat(service.preflight(
                context,
                List.of(new AgentModelToolCall(
                        "call-1",
                        "prepare_carts",
                        "{\"offers\":[{\"offerKey\":\"offer-selected-by-mission\"}]}"
                ))
        )).isEmpty();
        verify(mutationTargetPolicy, never()).requiresProductClarification(
                context, "prepare_carts");
    }

    @Test
    void conflictingDelegatedWordingStillClarifiesTheVisibleProductTarget() {
        AgentToolExecutionContext context = context(
                "Prepare everything; add the third blue one to my cart."
        );
        List<AgentVisibleProductReference> products = List.of(
                new AgentVisibleProductReference(1, 1, "product-green", "offer-green", "Green cap"),
                new AgentVisibleProductReference(2, 2, "product-blue", "offer-blue", "Blue cap"),
                new AgentVisibleProductReference(3, 3, "product-red", "offer-red", "Red cap")
        );
        AgentToolDescriptor descriptor = descriptor("prepare_carts");
        when(authorizationPolicy.authorized(context, descriptor)).thenReturn(true);
        when(authorizationPolicy.isUnqualifiedDelegatedCartAddition(context, "prepare_carts"))
                .thenReturn(false);
        when(mutationTargetPolicy.requiresProductClarification(context, "prepare_carts"))
                .thenReturn(true);
        when(mutationTargetPolicy.productClarificationCandidates(context, "prepare_carts"))
                .thenReturn(products);

        assertThat(service.preflight(
                context,
                List.of(new AgentModelToolCall(
                        "call-1",
                        "prepare_carts",
                        "{\"offers\":[{\"offerKey\":\"offer-red\"}]}"
                ))
        )).contains(new AgentProductClarification(
                "prepare_carts",
                "Prepare everything; add the third blue one to my cart.",
                products
        ));
    }

    @Test
    void questionIsPlainTextAndFallsBackWhenATitleIsMissing() {
        AgentProductClarification clarification = new AgentProductClarification(
                "prepare_carts",
                "Add one",
                List.of(
                        new AgentVisibleProductReference(1, 8, "product-1", "offer-1", "Trail * Cap"),
                        new AgentVisibleProductReference(2, 9, "product-2", "offer-2", null)
                )
        );

        assertThat(service.question(clarification)).isEqualTo("""
                Which product should I add to your cart? Reply with a number or product name:
                1. Trail * Cap
                2. Product 2""");
    }

    private AgentToolExecutionContext context(String userText) {
        return new AgentToolExecutionContext(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                userText
        );
    }

    private List<AgentVisibleProductReference> products() {
        return List.of(
                new AgentVisibleProductReference(1, 5, "product-blue", "offer-blue", "Blue cap"),
                new AgentVisibleProductReference(2, 6, "product-red", "offer-red", "Red cap")
        );
    }

    private AgentToolDescriptor descriptor(String name) {
        return new AgentToolDescriptor(
                name,
                "Test tool",
                "{\"type\":\"object\"}",
                "1",
                AgentToolRisk.REVERSIBLE_MUTATION
        );
    }
}
