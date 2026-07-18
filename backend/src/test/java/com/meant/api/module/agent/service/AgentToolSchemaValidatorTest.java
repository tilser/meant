package com.meant.api.module.agent.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.meant.api.module.agent.exception.AgentException;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class AgentToolSchemaValidatorTest {

    private final AgentToolSchemaValidator validator = new AgentToolSchemaValidator(new ObjectMapper());

    @Test
    void enforcesRequiredFieldsAndRejectsUnknownProperties() {
        String schema = """
                {"type":"object","additionalProperties":false,
                 "required":["cartId","cartLineId","quantity"],
                 "properties":{
                   "cartId":{"type":"string","format":"uuid"},
                   "cartLineId":{"type":"string","format":"uuid"},
                   "quantity":{"type":"integer","minimum":1,"maximum":1000}
                 }}
                """;
        String base = "{\"cartId\":\"00000000-0000-0000-0000-000000000001\","
                + "\"cartLineId\":\"00000000-0000-0000-0000-000000000002\"";

        assertThatThrownBy(() -> validator.validate(schema, base + "}"))
                .isInstanceOf(AgentException.class)
                .hasMessageContaining("quantity is required");
        assertThatThrownBy(() -> validator.validate(schema, base + ",\"quantity\":1,\"user\":\"x\"}"))
                .isInstanceOf(AgentException.class)
                .hasMessageContaining("is not allowed");
        assertThatCode(() -> validator.validate(schema, base + ",\"quantity\":2}"))
                .doesNotThrowAnyException();
    }

    @Test
    void enforcesNestedAnyOfArrayAndEnumConstraints() {
        String schema = """
                {"type":"object","additionalProperties":false,"required":["state","selection"],
                 "properties":{
                   "state":{"type":"string","enum":["READY","CANCELLED"]},
                   "selection":{"type":"object","additionalProperties":false,
                     "anyOf":[{"required":["offerKey"]},{"required":["inventoryItemId"]}],
                     "properties":{
                       "offerKey":{"type":"string","minLength":1},
                       "inventoryItemId":{"type":"string","format":"uuid"}
                     }}
                 }}
                """;

        assertThatThrownBy(() -> validator.validate(
                schema,
                "{\"state\":\"ACTIVE\",\"selection\":{}}"
        )).isInstanceOf(AgentException.class);
        assertThatCode(() -> validator.validate(
                schema,
                "{\"state\":\"READY\",\"selection\":{\"offerKey\":\"offer-1\"}}"
        )).doesNotThrowAnyException();
    }
}
