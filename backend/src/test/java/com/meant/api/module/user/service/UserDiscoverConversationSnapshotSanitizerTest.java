package com.meant.api.module.user.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class UserDiscoverConversationSnapshotSanitizerTest {

    private final UserDiscoverConversationSnapshotSanitizer sanitizer =
            new UserDiscoverConversationSnapshotSanitizer(new ObjectMapper());

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
