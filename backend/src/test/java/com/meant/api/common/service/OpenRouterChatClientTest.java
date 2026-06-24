package com.meant.api.common.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

import com.meant.api.common.exception.OpenRouterException;
import com.meant.api.common.properties.OpenRouterProperties;
import com.meant.api.common.service.dto.OpenRouterJsonSchemaDefinition;
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

@ExtendWith(OutputCaptureExtension.class)
class OpenRouterChatClientTest {

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
