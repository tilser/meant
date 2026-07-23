package com.meant.api.module.merchant.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.meant.api.module.merchant.repository.MerchantRetrievalEmbeddingVectorRepository.MerchantRetrievalEmbeddingUpsert;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

class MerchantRetrievalEmbeddingVectorRepositoryTest {

    @Test
    void upsertsGeneratedEmbeddingsInOneJdbcBatch() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        MerchantRetrievalEmbeddingVectorRepository repository =
                new MerchantRetrievalEmbeddingVectorRepository(jdbcTemplate);
        Instant now = Instant.parse("2026-07-23T12:00:00Z");
        UUID firstMerchantId = UUID.randomUUID();
        UUID secondMerchantId = UUID.randomUUID();

        repository.upsertAll(List.of(
                new MerchantRetrievalEmbeddingUpsert(
                        firstMerchantId,
                        "Categories: Shoes",
                        "first-hash",
                        List.of(1.0d, 0.0d),
                        "voyage-3-large",
                        now
                ),
                new MerchantRetrievalEmbeddingUpsert(
                        secondMerchantId,
                        "Categories: Home",
                        "second-hash",
                        List.of(0.0d, 1.0d),
                        "voyage-3-large",
                        now
                )
        ));

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<SqlParameterSource[]> parametersCaptor =
                ArgumentCaptor.forClass(SqlParameterSource[].class);
        verify(jdbcTemplate).batchUpdate(sqlCaptor.capture(), parametersCaptor.capture());
        assertThat(sqlCaptor.getValue()).contains("on conflict (merchant_id) do update");
        assertThat(parametersCaptor.getValue()).hasSize(2);
        assertThat(parametersCaptor.getValue()[0].getValue("merchantId")).isEqualTo(firstMerchantId);
        assertThat(parametersCaptor.getValue()[0].getValue("retrievalEmbedding")).isEqualTo("[1.0,0.0]");
        assertThat(parametersCaptor.getValue()[1].getValue("merchantId")).isEqualTo(secondMerchantId);
        assertThat(parametersCaptor.getValue()[1].getValue("retrievalEmbedding")).isEqualTo("[0.0,1.0]");
        assertThat(parametersCaptor.getValue())
                .extracting(parameters -> parameters.getValue("embeddedAt"))
                .containsOnly(Timestamp.from(now));
    }

    @Test
    void deactivatesMissingContentMerchantsWithOneStatement() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        MerchantRetrievalEmbeddingVectorRepository repository =
                new MerchantRetrievalEmbeddingVectorRepository(jdbcTemplate);
        Instant now = Instant.parse("2026-07-23T12:00:00Z");
        List<UUID> merchantIds = List.of(UUID.randomUUID(), UUID.randomUUID());

        repository.deactivateAll(merchantIds, now);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<SqlParameterSource> parametersCaptor =
                ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(jdbcTemplate).update(sqlCaptor.capture(), parametersCaptor.capture());
        assertThat(sqlCaptor.getValue()).contains("where merchant_id in (:merchantIds)");
        assertThat(parametersCaptor.getValue().getValue("merchantIds")).isEqualTo(merchantIds);
        assertThat(parametersCaptor.getValue().getValue("updatedAt")).isEqualTo(Timestamp.from(now));
    }

    @Test
    void emptyWritesDoNotReachJdbc() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        MerchantRetrievalEmbeddingVectorRepository repository =
                new MerchantRetrievalEmbeddingVectorRepository(jdbcTemplate);

        repository.upsertAll(List.of());
        repository.deactivateAll(List.of(), Instant.parse("2026-07-23T12:00:00Z"));

        verify(jdbcTemplate, never()).batchUpdate(anyString(), org.mockito.ArgumentMatchers.any(SqlParameterSource[].class));
        verify(jdbcTemplate, never()).update(anyString(), org.mockito.ArgumentMatchers.any(SqlParameterSource.class));
    }
}
