package com.meant.api.module.agent.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AgentMutationTargetTextSupportTest {

    @Test
    void resolvesOrdinalPhrasesWithoutPersistenceContext() {
        assertThat(AgentMutationTargetTextSupport.ordinals("Compare the second and fourth."))
                .containsExactly(2, 4);
        assertThat(AgentMutationTargetTextSupport.ordinals("Show me the first three."))
                .containsExactly(1, 2, 3);
        assertThat(AgentMutationTargetTextSupport.ordinals("2"))
                .containsExactly(2);
    }

    @Test
    void keepsClarificationNumbersDistinctFromOrdinalsInDescriptions() {
        assertThat(AgentMutationTargetTextSupport.clarificationOrdinals("option 2 and 3"))
                .containsExactly(2, 3);
        assertThat(AgentMutationTargetTextSupport.clarificationOrdinals("the third jacket"))
                .containsExactly(3);
    }

    @Test
    void normalizesDescriptiveProductWords() {
        assertThat(AgentMutationTargetTextSupport.descriptiveTokens(
                "Please show the black dresses and accessories."
        )).containsExactlyInAnyOrder("black", "dress", "accessory");
    }

    @Test
    void recognizesTheExactProductDetailComposerAnchorWithoutAbsorbingTheQuestion() {
        String turn = "About LETHAL SPEED RS MENS FOOTBALL: is there also black variant?";

        assertThat(AgentMutationTargetTextSupport.productDetailAnchorMatches(
                turn,
                "LETHAL SPEED RS MENS FOOTBALL"
        )).isTrue();
        assertThat(AgentMutationTargetTextSupport.productDetailAnchorMatches(
                turn,
                "Mens Puma FUTURE 7 ULTIMATE FG/AG"
        )).isFalse();
    }

    @Test
    void recognizesOnlyContextualOrExplicitReaddLanguage() {
        assertThat(AgentMutationTargetTextSupport.isReaddReference("Re-add it.")).isTrue();
        assertThat(AgentMutationTargetTextSupport.isReaddReference("Put that back, please.")).isTrue();
        assertThat(AgentMutationTargetTextSupport.isReaddReference(
                "Go back to the second jacket and compare it."
        )).isFalse();
    }
}
