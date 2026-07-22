package com.meant.api.module.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.meant.api.module.user.exception.UserException;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class UserDiscoverConversationSnapshotSanitizerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final UserDiscoverConversationSnapshotSanitizer sanitizer =
            new UserDiscoverConversationSnapshotSanitizer(objectMapper);

    @Test
    void stripsRichSimilarityFactsAndKeepsOnlyBoundedReferences() throws Exception {
        String snapshotJson = """
                {
                  "id": "thread-1",
                  "title": "SENTINEL PRODUCT TITLE",
                  "merchantId": "00000000-0000-4000-8000-000000000088",
                  "autoTitleSource": "similar-product-search",
                  "unrelated": {"text": "keep this value"},
                  "messages": [
                    {
                      "id": "request-1",
                      "role": "you",
                      "text": "Show me products similar to SENTINEL PRODUCT TITLE.",
                      "productContext": {
                        "id": "anchor-key",
                        "name": "SENTINEL PRODUCT TITLE",
                        "imageUrl": "https://example.test/sentinel.jpg",
                        "priceFrom": 49.99,
                        "offers": [{"merchant": "SENTINEL MERCHANT"}],
                        "canonicalProduct": {"key": "anchor-key", "title": "SENTINEL CANONICAL"}
                      },
                      "similarMessageRole": "request",
                      "similarSearchStatus": "requested",
                      "similarAnchorCanonicalProductKey": " anchor-key "
                    },
                    {
                      "id": "response-1",
                      "role": "ai",
                      "query": " jeans under 50 USD ",
                      "similarMessageRole": "response",
                      "similarSearchStatus": "success",
                      "similarAnchorCanonicalProductKey": "anchor-key",
                      "product": {"name": "SENTINEL ROOT PRODUCT"},
                      "blocks": [
                        {"type": "text", "text": "SENTINEL result summary"},
                        {
                          "type": "similar",
                          "product": {
                            "id": "wrong-fallback",
                            "name": "SENTINEL ANCHOR",
                            "canonicalProduct": {"key": "anchor-key", "title": "SENTINEL CANONICAL"}
                          },
                          "products": [{"id": "unused-rich-result", "name": "SENTINEL RESULT"}],
                          "anchorCanonicalProductKey": " anchor-key ",
                          "resultCanonicalProductKeys": [
                            " result-one ",
                            "result-one",
                            "anchor-key",
                            "result-two",
                            "%s"
                          ],
                          "query": " jeans under 50 USD ",
                          "media": [{"url": "https://example.test/sentinel-result.jpg"}],
                          "offers": [{"price": 42}],
                          "canonicalProduct": {"title": "SENTINEL BLOCK CANONICAL"}
                        },
                        {"type": "products", "products": [{"name": "SENTINEL MALICIOUS EXTRA"}]}
                      ]
                    }
                  ]
                }
                """.formatted("x".repeat(201));

        var sanitized = sanitizer.sanitize("SENTINEL PRODUCT TITLE", snapshotJson);

        assertThat(sanitized.title()).isEqualTo("Similar products");
        assertThat(sanitized.threadJson())
                .contains("anchor-key", "result-one", "result-two", "jeans under 50 USD")
                .doesNotContain(
                        "SENTINEL",
                        "productContext",
                        "imageUrl",
                        "priceFrom",
                        "offers",
                        "canonicalProduct",
                        "unused-rich-result",
                        "x".repeat(201));

        JsonNode root = objectMapper.readTree(sanitized.threadJson());
        assertThat(root.path("title").asText()).isEqualTo("Similar products");
        assertThat(root.path("merchantId").asText())
                .isEqualTo("00000000-0000-4000-8000-000000000088");
        assertThat(root.has("unrelated")).isFalse();
        assertThat(root.path("messages").get(0).path("text").asText())
                .isEqualTo("Show me similar products.");
        JsonNode response = root.path("messages").get(1);
        assertThat(response.path("blocks")).hasSize(2);
        assertThat(response.path("blocks").get(0).path("text").asText())
                .isEqualTo("I found similar products for your search.");
        JsonNode reference = response.path("blocks").get(1);
        assertThat(reference.path("type").asText()).isEqualTo("similar-reference");
        assertThat(reference.path("anchorCanonicalProductKey").asText()).isEqualTo("anchor-key");
        assertThat(reference.path("resultCanonicalProductKeys"))
                .extracting(JsonNode::asText)
                .containsExactly("result-one", "result-two");
        assertThat(reference.path("query").asText()).isEqualTo("jeans under 50 USD");
        assertThat(reference.path("status").asText()).isEqualTo("idle");
    }

    @Test
    void dropsMalformedSimilarReferenceWithoutChangingSeparateUnrelatedMessageContent() throws Exception {
        String snapshotJson = """
                {
                  "id": "thread-1",
                  "title": "ordinary thread",
                  "named": true,
                  "messages": [
                    {
                      "id": "similar-message",
                      "role": "ai",
                      "text": "remove this malformed Similar text",
                      "blocks": [{
                        "type": "similar-reference",
                        "anchorCanonicalProductKey": "anchor-key",
                        "resultCanonicalProductKeys": ["result-key"],
                        "query": "%s"
                      }]
                    },
                    {
                      "id": "ordinary-message",
                      "role": "ai",
                      "text": "keep this exact message",
                      "blocks": [{"type": "products", "products": [{"name": "unrelated product"}]}]
                    }
                  ]
                }
                """.formatted("q".repeat(501));

        var sanitized = sanitizer.sanitize("ordinary thread", snapshotJson);

        JsonNode root = objectMapper.readTree(sanitized.threadJson());
        JsonNode malformedSimilarMessage = root.path("messages").get(0);
        JsonNode unrelatedMessage = root.path("messages").get(1);
        assertThat(sanitized.title()).isEqualTo("ordinary thread");
        assertThat(malformedSimilarMessage.path("similarSearchStatus").asText()).isEqualTo("error");
        assertThat(malformedSimilarMessage.path("blocks")).hasSize(1);
        assertThat(unrelatedMessage.path("text").asText()).isEqualTo("keep this exact message");
        assertThat(unrelatedMessage.path("blocks")).hasSize(1);
        assertThat(unrelatedMessage.path("blocks").get(0).path("type").asText()).isEqualTo("products");
        assertThat(unrelatedMessage.path("blocks").get(0).path("products").get(0).path("name").asText())
                .isEqualTo("unrelated product");
    }

    @Test
    void reconstructsUnmarkedReferenceOnlyMessageBeforePersistence() throws Exception {
        String snapshotJson = """
                {
                  "id": "thread-1",
                  "title": "SENTINEL REFERENCE TITLE",
                  "messages": [{
                    "id": "reference-response",
                    "role": "ai",
                    "text": "SENTINEL REFERENCE TEXT",
                    "productContext": {"name": "SENTINEL CONTEXT", "offers": [{"price": 40}]},
                    "media": [{"url": "https://example.test/sentinel.jpg"}],
                    "blocks": [
                      {
                        "type": "similar-reference",
                        "anchorCanonicalProductKey": "anchor-key",
                        "resultCanonicalProductKeys": ["result-key"],
                        "query": "jeans under 50 USD"
                      },
                      {"type": "products", "products": [{"name": "SENTINEL EXTRA PRODUCT"}]}
                    ]
                  }]
                }
                """;

        var sanitized = sanitizer.sanitize("SENTINEL REFERENCE TITLE", snapshotJson);

        assertThat(sanitized.title()).isEqualTo("Similar products");
        assertThat(sanitized.threadJson())
                .contains("similar-reference", "anchor-key", "result-key", "jeans under 50 USD")
                .doesNotContain(
                        "SENTINEL",
                        "productContext",
                        "offers",
                        "media",
                        "\"products\"");
        JsonNode response = objectMapper.readTree(sanitized.threadJson()).path("messages").get(0);
        assertThat(response.path("similarMessageRole").asText()).isEqualTo("response");
        assertThat(response.path("similarSearchStatus").asText()).isEqualTo("success");
        assertThat(response.path("blocks")).hasSize(2);
    }

    @Test
    void preservesAnExistingUnnamedThreadTitleWhenSimilarityIsAppended() throws Exception {
        String snapshotJson = """
                {
                  "id": "thread-1",
                  "title": "jeans under 50",
                  "messages": [
                    {
                      "id": "original-search",
                      "role": "you",
                      "text": "jeans under 50"
                    },
                    {
                      "id": "similar-response",
                      "role": "ai",
                      "similarMessageRole": "response",
                      "similarSearchStatus": "success",
                      "blocks": [{
                        "type": "similar-reference",
                        "anchorCanonicalProductKey": "anchor-key",
                        "resultCanonicalProductKeys": ["result-key"],
                        "query": "jeans under 50"
                      }]
                    }
                  ]
                }
                """;

        var sanitized = sanitizer.sanitize("jeans under 50", snapshotJson);

        assertThat(sanitized.title()).isEqualTo("jeans under 50");
        JsonNode root = objectMapper.readTree(sanitized.threadJson());
        assertThat(root.path("title").asText()).isEqualTo("jeans under 50");
        assertThat(root.path("messages").get(1).path("blocks").get(1).path("type").asText())
                .isEqualTo("similar-reference");
    }

    @Test
    void sanitizesUnmarkedLegacyRichSimilarMessageAndGeneratedTitle() throws Exception {
        String snapshotJson = """
                {
                  "id": "thread-1",
                  "title": "SENTINEL LEGACY PRODUCT TITLE",
                  "autoTitleSource": "similar-product-search",
                  "messages": [
                    {
                      "id": "legacy-response",
                      "role": "ai",
                      "text": "SENTINEL LEGACY PRODUCT TITLE has similar options.",
                      "productContext": {"name": "SENTINEL CONTEXT", "priceFrom": 45},
                      "blocks": [{
                        "type": "similar",
                        "product": {
                          "id": "anchor-key",
                          "name": "SENTINEL ANCHOR",
                          "canonicalProduct": {"key": "anchor-key", "offers": [{"price": 45}]}
                        },
                        "products": [{
                          "id": "result-key",
                          "name": "SENTINEL RESULT",
                          "imageUrl": "https://example.test/sentinel.jpg"
                        }],
                        "query": "jeans under 50 USD"
                      }]
                    },
                    {
                      "id": "ordinary-message",
                      "role": "you",
                      "text": "keep unrelated content"
                    }
                  ]
                }
                """;

        var sanitized = sanitizer.sanitize("SENTINEL LEGACY PRODUCT TITLE", snapshotJson);

        assertThat(sanitized.title()).isEqualTo("Similar products");
        assertThat(sanitized.threadJson())
                .contains("similar-reference", "anchor-key", "result-key", "jeans under 50 USD")
                .contains("keep unrelated content")
                .doesNotContain(
                        "SENTINEL",
                        "productContext",
                        "priceFrom",
                        "imageUrl",
                        "canonicalProduct",
                        "offers");
        JsonNode root = objectMapper.readTree(sanitized.threadJson());
        assertThat(root.path("title").asText()).isEqualTo("Similar products");
        assertThat(root.path("messages").get(0).path("similarMessageRole").asText())
                .isEqualTo("response");
        assertThat(root.path("messages").get(0).path("similarSearchStatus").asText())
                .isEqualTo("success");
        assertThat(root.path("messages").get(1).path("text").asText())
                .isEqualTo("keep unrelated content");
    }

    @Test
    void limitsPersistedSimilarResultReferencesToTwentyUniqueKeys() throws Exception {
        String resultKeys = IntStream.range(0, 25)
                .mapToObj(index -> "\"result-%s\"".formatted(index))
                .collect(Collectors.joining(","));
        String snapshotJson = """
                {
                  "title": "similar",
                  "messages": [{
                    "id": "response",
                    "role": "ai",
                    "similarMessageRole": "response",
                    "similarSearchStatus": "success",
                    "blocks": [{
                      "type": "similar-reference",
                      "anchorCanonicalProductKey": "anchor-key",
                      "resultCanonicalProductKeys": [%s,"result-0","anchor-key"],
                      "query": "jeans under 50 USD"
                    }]
                  }]
                }
                """.formatted(resultKeys);

        var sanitized = sanitizer.sanitize("similar", snapshotJson);

        JsonNode result = objectMapper.readTree(sanitized.threadJson())
                .path("messages").get(0)
                .path("blocks").get(1)
                .path("resultCanonicalProductKeys");
        assertThat(result).hasSize(20);
        assertThat(result).extracting(JsonNode::asText)
                .containsExactlyElementsOf(IntStream.range(0, 20)
                        .mapToObj(index -> "result-" + index)
                        .toList());
    }

    @Test
    void rejectsMalformedSnapshotJson() {
        assertThatThrownBy(() -> sanitizer.sanitize("thread", "{not-json"))
                .isInstanceOf(UserException.class)
                .hasMessageContaining("valid JSON");
    }

    @Test
    void keepsOnlyTheServerResultReferenceFromAProductBlock() {
        String sanitized = sanitizer.sanitize("""
                {
                  "messages":[{
                    "id":"answer-1",
                    "role":"ai",
                    "blocks":[
                      {"type":"text","text":"I found current matches."},
                      {
                        "type":"products",
                        "productResultSetId":"10000000-0000-0000-0000-000000000001",
                        "query":"shoes",
                        "products":[{
                          "name":"private title",
                          "price":12900,
                          "image":"https://cdn.shopify.com/private.jpg"
                        }],
                        "unavailableCount":7
                      }
                    ]
                  }]
                }
                """);

        assertThat(sanitized)
                .contains(
                        "I found current matches.",
                        "\"type\":\"products\"",
                        "\"products\":[]",
                        "10000000-0000-0000-0000-000000000001",
                        "\"query\":\"shoes\"")
                .doesNotContain(
                        "private title",
                        "12900",
                        "cdn.shopify.com",
                        "unavailableCount",
                        "active session");
    }

    @Test
    void preservesProductSnapshotsWithoutACanonicalServerResultReference() {
        String legacy = sanitizer.sanitize("""
                {"messages":[{"role":"ai","blocks":[
                  {"type":"text","text":"I can still keep this answer."},
                  {"type":"products","query":"shoes","products":[{"name":"legacy"}]}
                ]}]}
                """);
        String shortenedUuid = sanitizer.sanitize("""
                {"messages":[{"role":"ai","blocks":[
                  {"type":"products","productResultSetId":"1-1-1-1-1","query":"shoes"}
                ]}]}
                """);

        assertThat(legacy)
                .contains(
                        "I can still keep this answer.",
                        "\"type\":\"products\"",
                        "legacy")
                .doesNotContain("This attachment is unavailable in conversation history");
        assertThat(shortenedUuid)
                .contains("\"type\":\"products\"", "1-1-1-1-1")
                .doesNotContain("This attachment is unavailable in conversation history");
    }

    @Test
    void keepsMessageEnvelopeProductContextAndOriginalRichBlocks() {
        String sanitized = sanitizer.sanitize("""
                {
                  "focusProductId":"canonical-product-1",
                  "messages":[{
                    "id":"answer-1",
                    "role":"ai",
                    "text":"Complete assistant answer",
                    "query":"running shoes",
                    "suggestedReplies":["Show another",42,"Compare"],
                    "sessionOnly":true,
                    "productContext":{"title":"private product","price":12900},
                    "blocks":[
                      {"type":"text","text":"The explanation remains.","product":{"title":"hidden"}},
                      {"type":"reviews","product":{"title":"private product","image":"https://cdn.shopify.com/p.jpg"}},
                      {"type":"system","text":"Current status"}
                    ]
                  }]
                }
                """);

        assertThat(sanitized)
                .contains(
                        "\"focusProductId\":\"canonical-product-1\"",
                        "Complete assistant answer",
                        "\"query\":\"running shoes\"",
                        "Show another",
                        "Compare",
                        "The explanation remains.",
                        "Current status",
                        "\"productContext\"",
                        "\"type\":\"reviews\"",
                        "private product",
                        "12900",
                        "cdn.shopify.com",
                        "hidden")
                .doesNotContain(
                        "sessionOnly",
                        "42");
    }

    @Test
    void keepsComparisonAndCartBlocksForTheOriginalHistoryUi() {
        String sanitized = sanitizer.sanitize("""
                {"messages":[{"id":"answer-1","role":"ai","blocks":[
                  {"type":"minicompare","products":[{"id":"product-1","name":"Jacket"}],
                   "rows":[{"label":"Match","values":["87%"],"winnerIndex":0}],"pickIndex":0},
                  {"type":"cart","lines":[{"id":"product-1","merchant":"Store","qty":2}],
                   "products":[{"id":"product-1","name":"Jacket"}]}
                ]}]}
                """);

        assertThat(sanitized)
                .contains(
                        "\"type\":\"minicompare\"",
                        "\"rows\"",
                        "\"winnerIndex\":0",
                        "\"pickIndex\":0",
                        "\"type\":\"cart\"",
                        "\"lines\"",
                        "\"merchant\":\"Store\"",
                        "\"qty\":2");
    }

    @Test
    void sanitizationIsIdempotent() {
        String first = sanitizer.sanitize("""
                {"focusProductId":"product-1","messages":[
                  {"id":"user-1","role":"you","text":"Compare these"},
                  {"id":"answer-1","role":"ai","blocks":[
                    {"type":"text","text":"Here is the comparison."},
                    {"type":"minicompare","products":[{"title":"private"}]}
                  ]}
                ]}
                """);

        assertThat(sanitizer.sanitize(first)).isEqualTo(first);
    }
}
