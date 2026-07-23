package com.meant.api.module.merchant.repository;

import com.meant.api.module.merchant.service.dto.MerchantSemanticSearchCandidate;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class MerchantRetrievalEmbeddingVectorRepository {

    private static final String UPSERT_SQL = """
            insert into merchant_retrieval_embedding (
                id,
                merchant_id,
                retrieval_content,
                retrieval_content_hash,
                retrieval_embedding,
                embedding_model,
                embedded_at,
                created_at,
                updated_at,
                active
            )
            values (
                :id,
                :merchantId,
                :retrievalContent,
                :retrievalContentHash,
                cast(:retrievalEmbedding as vector),
                :embeddingModel,
                :embeddedAt,
                :createdAt,
                :updatedAt,
                true
            )
            on conflict (merchant_id) do update set
                retrieval_content = excluded.retrieval_content,
                retrieval_content_hash = excluded.retrieval_content_hash,
                retrieval_embedding = excluded.retrieval_embedding,
                embedding_model = excluded.embedding_model,
                embedded_at = excluded.embedded_at,
                updated_at = excluded.updated_at,
                active = true
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public void upsertAll(List<MerchantRetrievalEmbeddingUpsert> embeddings) {
        if (embeddings.isEmpty()) {
            return;
        }

        SqlParameterSource[] batchParameters = embeddings.stream()
                .map(embedding -> new MapSqlParameterSource()
                        .addValue("id", UUID.randomUUID())
                        .addValue("merchantId", embedding.merchantId())
                        .addValue("retrievalContent", embedding.retrievalContent())
                        .addValue("retrievalContentHash", embedding.retrievalContentHash())
                        .addValue("retrievalEmbedding", vectorLiteral(embedding.retrievalEmbedding()))
                        .addValue("embeddingModel", embedding.embeddingModel())
                        .addValue("embeddedAt", timestamp(embedding.now()))
                        .addValue("createdAt", timestamp(embedding.now()))
                        .addValue("updatedAt", timestamp(embedding.now())))
                .toArray(SqlParameterSource[]::new);
        jdbcTemplate.batchUpdate(UPSERT_SQL, batchParameters);
    }

    public void deactivateAll(Collection<UUID> merchantIds, Instant now) {
        if (merchantIds.isEmpty()) {
            return;
        }

        jdbcTemplate.update("""
                update merchant_retrieval_embedding
                set active = false,
                    updated_at = :updatedAt
                where merchant_id in (:merchantIds)
                """, new MapSqlParameterSource()
                .addValue("merchantIds", merchantIds)
                .addValue("updatedAt", timestamp(now)));
    }

    public List<MerchantSemanticSearchCandidate> search(List<Double> queryEmbedding, String embeddingModel, int limit) {
        return jdbcTemplate.query("""
                select merchant.id,
                       merchant.domain,
                       merchant.name,
                       catalog_integration.endpoint as advertised_mcp_endpoint,
                       cast(null as text) as profile_mcp_endpoint,
                       embedding.retrieval_content,
                       1 - (embedding.retrieval_embedding <=> cast(:queryEmbedding as vector)) as score
                from merchant_retrieval_embedding embedding
                join merchant merchant on merchant.id = embedding.merchant_id
                join lateral (
                    select min(integration.endpoint) as endpoint
                    from merchant_integration integration
                    where integration.merchant_id = merchant.id
                      and integration.provider = 'GENERIC_UCP'
                      and integration.status = 'ACTIVE'
                      and exists (
                          select 1
                          from merchant_integration_role integration_role
                          where integration_role.merchant_integration_id = integration.id
                            and integration_role.role = 'STOREFRONT_CATALOG'
                      )
                    having count(*) = 1
                ) catalog_integration on true
                where embedding.active = true
                  and embedding.embedding_model = :embeddingModel
                  and merchant.active = true
                order by embedding.retrieval_embedding <=> cast(:queryEmbedding as vector)
                limit :limit
                """, Map.of(
                "queryEmbedding", vectorLiteral(queryEmbedding),
                "embeddingModel", embeddingModel,
                "limit", limit
        ), (resultSet, _) -> new MerchantSemanticSearchCandidate(
                resultSet.getObject("id", UUID.class),
                resultSet.getString("domain"),
                resultSet.getString("name"),
                resultSet.getString("advertised_mcp_endpoint"),
                resultSet.getString("profile_mcp_endpoint"),
                resultSet.getString("retrieval_content"),
                resultSet.getDouble("score")
        ));
    }

    private String vectorLiteral(List<Double> embedding) {
        return embedding.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(",", "[", "]"));
    }

    private Timestamp timestamp(Instant instant) {
        return Timestamp.from(instant);
    }

    public record MerchantRetrievalEmbeddingUpsert(
            UUID merchantId,
            String retrievalContent,
            String retrievalContentHash,
            List<Double> retrievalEmbedding,
            String embeddingModel,
            Instant now
    ) {
    }
}
