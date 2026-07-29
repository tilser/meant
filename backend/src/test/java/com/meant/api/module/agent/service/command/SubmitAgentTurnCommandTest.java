package com.meant.api.module.agent.service.command;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SubmitAgentTurnCommandTest {

    @Test
    void sanitizesAndTruncatesOversizedUserAgentBeforeServiceValidation() {
        SubmitAgentTurnCommand command = new SubmitAgentTurnCommand(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Find running shoes",
                "client-turn-1",
                null,
                null,
                "203.0.113.42",
                "  Browser\r\n" + "x".repeat(600)
        );

        assertThat(command.userAgent())
                .hasSize(512)
                .startsWith("Browser ")
                .doesNotContain("\r", "\n");
        try (var validatorFactory = Validation.buildDefaultValidatorFactory()) {
            assertThat(validatorFactory.getValidator().validate(command)).isEmpty();
        }
    }
}
