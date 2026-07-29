package com.meant.api.common.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

import com.meant.api.common.exception.OpenRouterException;
import com.meant.api.common.properties.OpenRouterProperties;
import com.meant.api.common.service.dto.OpenRouterChatMessage;
import com.meant.api.common.service.dto.OpenRouterChatRequest;
import com.meant.api.common.service.dto.OpenRouterJsonSchemaDefinition;
import com.meant.api.common.service.dto.OpenRouterPlugin;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(OutputCaptureExtension.class)
class OpenRouterChatClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void chatRequestSerializesWebSearchPlugins() throws Exception {
        OpenRouterChatRequest request = new OpenRouterChatRequest(
                "test-model",
                List.of(new OpenRouterChatMessage("user", "Find codes")),
                0.0,
                null,
                List.of(new OpenRouterPlugin("web", 8))
        );

        String json = objectMapper.writeValueAsString(request);

        assertThat(json)
                .contains("\"plugins\":[{\"id\":\"web\",\"max_results\":8}]")
                .contains("\"model\":\"test-model\"")
                .doesNotContain("response_format");
    }

    @Test
    void chatRequestWithoutPluginsKeepsExistingSerializationShape() throws Exception {
        OpenRouterChatRequest request = new OpenRouterChatRequest(
                "test-model",
                List.of(new OpenRouterChatMessage("user", "Hello")),
                0.0,
                null
        );

        String json = objectMapper.writeValueAsString(request);

        assertThat(json)
                .contains("\"model\":\"test-model\"")
                .contains("\"stream\":false")
                .doesNotContain("\"plugins\"");
    }

    @Test
    void completeJsonLogsRedactedHttpFailureDiagnostics(CapturedOutput output) {
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        OpenRouterChatClient client = new OpenRouterChatClient(
                restClientBuilder,
                openRouterProperties("test-secret-key")
        );
        server.expect(requestTo("https://openrouter.test/api/v1/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer test-secret-key"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {
                                  "error": {
                                    "message": "Rate limit exceeded",
                                    "api_key": "test-secret-key",
                                    "authorization": "Bearer opaque-token"
                                  }
                                }
                                """));

        assertThatThrownBy(() -> client.completeJson(
                "test-model",
                "private system prompt",
                "private user prompt",
                "test_schema",
                responseSchema()
        ))
                .isInstanceOf(OpenRouterException.class)
                .hasMessageContaining("OpenRouter chat completion failed")
                .hasMessageContaining("status=429")
                .hasMessageContaining("model=test-model")
                .hasMessageContaining("Rate limit exceeded")
                .hasMessageNotContaining("test-secret-key")
                .hasMessageNotContaining("opaque-token")
                .hasMessageNotContaining("private user prompt")
                .hasMessageNotContaining("private system prompt");

        assertThat(output)
                .contains("OpenRouter chat completion failed model=test-model status=429")
                .contains("Rate limit exceeded")
                .doesNotContain("test-secret-key")
                .doesNotContain("opaque-token")
                .doesNotContain("private user prompt")
                .doesNotContain("private system prompt");
        server.verify();
    }

    @Test
    void completeJsonRejectsLengthLimitedContentBeforeTheCallerParsesIt(CapturedOutput output) {
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        OpenRouterChatClient client = new OpenRouterChatClient(
                restClientBuilder,
                openRouterProperties("test-key")
        );
        server.expect(requestTo("https://openrouter.test/api/v1/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"max_tokens\":4096")))
                .andRespond(withStatus(HttpStatus.OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {
                                  "model": "resolved-model",
                                  "choices": [{
                                    "finish_reason": "length",
                                    "native_finish_reason": "MAX_TOKENS",
                                    "message": {"role": "assistant", "content": "{\\"value\\":"}
                                  }],
                                  "usage": {
                                    "prompt_tokens": 200,
                                    "completion_tokens": 4096,
                                    "total_tokens": 4296
                                  }
                                }
                                """));

        assertThatThrownBy(() -> client.completeJson(
                "requested-model",
                "system",
                "user",
                "test_schema",
                responseSchema(),
                4096
        ))
                .isInstanceOf(OpenRouterException.class)
                .hasMessageContaining("was truncated")
                .hasMessageContaining("requestedModel=requested-model")
                .hasMessageContaining("resolvedModel=resolved-model")
                .hasMessageContaining("finishReason=length")
                .hasMessageContaining("completionTokens=4096")
                .hasMessageContaining("maximumOutputTokens=4096");
        assertThat(output)
                .contains(
                        "OpenRouter chat completion truncated",
                        "requestedModel=requested-model",
                        "resolvedModel=resolved-model",
                        "completionTokens=4096",
                        "maximumOutputTokens=4096"
                )
                .doesNotContain("{\"value\":");
        server.verify();
    }

    private OpenRouterProperties openRouterProperties(String apiKey) {
        return new OpenRouterProperties(
                "https://openrouter.test/api/v1",
                apiKey,
                "Meant",
                new OpenRouterProperties.Models(
                        "test-model",
                        "test-model",
                        "test-model",
                        "test-model"
                )
        );
    }

    private OpenRouterJsonSchemaDefinition responseSchema() {
        return OpenRouterJsonSchemaDefinition.object(
                List.of("value"),
                Map.of("value", OpenRouterJsonSchemaDefinition.string())
        );
    }
}
