package com.meant.api.module.agent.properties;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

class AgentMessageLimitPropertiesTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void acceptsAPositiveDailyLimit() {
        assertThat(validator.validate(new AgentMessageLimitProperties(100))).isEmpty();
    }

    @Test
    void rejectsAZeroDailyLimit() {
        assertThat(validator.validate(new AgentMessageLimitProperties(0)))
                .anySatisfy(violation -> assertThat(violation.getPropertyPath().toString())
                        .isEqualTo("maximumPerUserPerDay"));
    }
}
