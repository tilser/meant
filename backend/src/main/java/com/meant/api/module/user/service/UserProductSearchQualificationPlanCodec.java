package com.meant.api.module.user.service;

import com.meant.api.module.user.service.dto.UserProductSearchQualificationPlan;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class UserProductSearchQualificationPlanCodec {

    private final ObjectMapper objectMapper;

    public String encode(UserProductSearchQualificationPlan plan) {
        try {
            return objectMapper.writeValueAsString(plan);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Could not serialize product-search qualification plan", exception);
        }
    }

    public UserProductSearchQualificationPlan decode(String value) {
        try {
            return objectMapper.readValue(value, UserProductSearchQualificationPlan.class);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Could not deserialize product-search qualification plan", exception);
        }
    }
}
