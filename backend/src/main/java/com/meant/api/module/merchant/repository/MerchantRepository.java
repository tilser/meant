package com.meant.api.module.merchant.repository;

import com.meant.api.module.merchant.entity.Merchant;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MerchantRepository extends JpaRepository<Merchant, UUID> {

    Optional<Merchant> findByDomain(String domain);

    Optional<Merchant> findByIdAndActiveTrue(UUID id);

    List<Merchant> findByDomainIn(Collection<String> domains);

    @EntityGraph(attributePaths = "merchantRaw")
    List<Merchant> findByActiveTrueOrderByNameAsc();

    @Modifying
    @Query("""
            update Merchant merchant
            set merchant.active = false,
                merchant.updatedAt = :updatedAt
            where merchant.active = true
            """)
    int markAllActiveInactive(@Param("updatedAt") Instant updatedAt);

    @Modifying
    @Query("""
            update Merchant merchant
            set merchant.active = false,
                merchant.updatedAt = :updatedAt
            where merchant.active = true
              and merchant.domain not in :domains
            """)
    int markInactiveByDomainNotIn(@Param("domains") Collection<String> domains, @Param("updatedAt") Instant updatedAt);

    @Query(value = """
            select merchant.*
            from merchant merchant
            left join merchant_retrieval_embedding embedding on embedding.merchant_id = merchant.id
            where merchant.active = true
              and (
                exists (
                    select 1
                    from merchant_category category
                    where category.merchant_id = merchant.id
                )
                or exists (
                    select 1
                    from merchant_popular_search popular_search
                    where popular_search.merchant_id = merchant.id
                )
              )
              and (
                embedding.id is null
                or embedding.active = false
                or embedding.embedding_model <> :embeddingModel
                or merchant.updated_at > embedding.embedded_at
              )
            order by merchant.updated_at asc
            limit :limit
            """, nativeQuery = true)
    List<Merchant> findForRetrievalEmbeddingRefresh(String embeddingModel, int limit);
}
