package com.meant.api.module.agent.service.command;

import static org.assertj.core.api.Assertions.assertThat;

import com.meant.api.module.agent.service.query.ResolveAgentPendingProductSearchQuery;
import com.meant.api.module.user.service.command.EnsureUserProfileCommand;
import com.meant.api.module.user.service.command.QualifyUserProductSearchCommand;
import com.meant.api.module.user.service.query.FindUserProductSearchQualificationByRequestQuery;
import jakarta.validation.Validation;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProductSearchQualificationInputValidationTest {

    @Test
    void acceptsTheSameMaximumMessageLengthAsAnAgentTurn() {
        UUID userId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        String maximumTurn = "x".repeat(8_000);
        var profile = new EnsureUserProfileCommand(userId, "buyer@example.com", null, null);

        try (var validatorFactory = Validation.buildDefaultValidatorFactory()) {
            var validator = validatorFactory.getValidator();

            assertThat(validator.validate(new QualifyAgentProductSearchCommand(
                    profile,
                    conversationId,
                    null,
                    null,
                    requestId,
                    maximumTurn
            ))).isEmpty();
            assertThat(validator.validate(new QualifyUserProductSearchCommand(
                    userId,
                    conversationId,
                    null,
                    maximumTurn,
                    null,
                    List.of(),
                    requestId
            ))).isEmpty();
            assertThat(validator.validate(new ResolveAgentPendingProductSearchQuery(
                    userId,
                    conversationId,
                    null,
                    requestId,
                    maximumTurn
            ))).isEmpty();
            assertThat(validator.validate(new FindUserProductSearchQualificationByRequestQuery(
                    userId,
                    conversationId,
                    null,
                    requestId,
                    maximumTurn
            ))).isEmpty();
        }
    }

    @Test
    void rejectsMessagesLongerThanTheAgentTurnBoundary() {
        UUID userId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        String oversizedTurn = "x".repeat(8_001);
        var profile = new EnsureUserProfileCommand(userId, "buyer@example.com", null, null);

        try (var validatorFactory = Validation.buildDefaultValidatorFactory()) {
            var validator = validatorFactory.getValidator();

            assertThat(validator.validate(new QualifyAgentProductSearchCommand(
                    profile,
                    conversationId,
                    null,
                    null,
                    requestId,
                    oversizedTurn
            ))).hasSize(1);
            assertThat(validator.validate(new QualifyUserProductSearchCommand(
                    userId,
                    conversationId,
                    null,
                    oversizedTurn,
                    null,
                    List.of(),
                    requestId
            ))).hasSize(1);
            assertThat(validator.validate(new ResolveAgentPendingProductSearchQuery(
                    userId,
                    conversationId,
                    null,
                    requestId,
                    oversizedTurn
            ))).hasSize(1);
            assertThat(validator.validate(new FindUserProductSearchQualificationByRequestQuery(
                    userId,
                    conversationId,
                    null,
                    requestId,
                    oversizedTurn
            ))).hasSize(1);
        }
    }
}
