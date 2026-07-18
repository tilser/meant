package com.meant.api.common.config;

import com.meant.api.common.service.SpringAiAgentModelGateway;
import com.meant.api.common.service.UnavailableAgentModelGateway;
import com.meant.api.module.agent.properties.AgentProperties;
import com.meant.api.module.agent.service.port.AgentModelGateway;
import io.micrometer.observation.ObservationRegistry;
import java.util.Map;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.model.tool.DefaultToolCallingManager;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AgentModelConfiguration {

    @Bean("agentChatModel")
    @ConditionalOnProperty(prefix = "commerce.agent", name = "enabled", havingValue = "true")
    ChatModel agentChatModel(AgentProperties properties, ObservationRegistry observationRegistry) {
        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .baseUrl(properties.baseUrl())
                .apiKey(properties.apiKey())
                .model(properties.model())
                .temperature(properties.temperature())
                .maxTokens(properties.maximumOutputTokens())
                .timeout(properties.modelTimeout())
                .maxRetries(0)
                .streamUsage(true)
                .parallelToolCalls(true)
                .customHeaders(Map.of(
                        "HTTP-Referer", properties.siteUrl(),
                        "X-Title", properties.appTitle(),
                        "X-OpenRouter-Title", properties.appTitle()
                ))
                .build();
        return OpenAiChatModel.builder()
                .options(options)
                .toolCallingManager(DefaultToolCallingManager.builder()
                        .observationRegistry(observationRegistry)
                        .build())
                .observationRegistry(observationRegistry)
                .build();
    }

    @Bean
    @ConditionalOnBean(name = "agentChatModel")
    AgentModelGateway springAiAgentModelGateway(
            @Qualifier("agentChatModel") ChatModel chatModel,
            AgentProperties properties
    ) {
        return new SpringAiAgentModelGateway(chatModel, properties);
    }

    @Bean
    @ConditionalOnMissingBean(AgentModelGateway.class)
    AgentModelGateway unavailableAgentModelGateway() {
        return new UnavailableAgentModelGateway();
    }
}
