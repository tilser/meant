package com.meant.api.module.merchant.repository;

import com.meant.api.module.merchant.constant.MerchantIdentityNamespace;
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

    Optional<Merchant> findByDomainAndActiveTrue(String domain);

    @Query("""
            select identity.merchant
            from MerchantIdentity identity
            where identity.namespace = :namespace
              and identity.normalizedValue = :normalizedValue
              and identity.merchant.active = true
            """)
    Optional<Merchant> findActiveByIdentity(
            @Param("namespace") MerchantIdentityNamespace namespace,
            @Param("normalizedValue") String normalizedValue
    );

    Optional<Merchant> findByIdAndActiveTrue(UUID id);

    List<Merchant> findByDomainIn(Collection<String> domains);

    @EntityGraph(attributePaths = "merchantRaw")
    List<Merchant> findByActiveTrueOrderByNameAsc();

    @Query("select merchant.domain from Merchant merchant where merchant.active = true")
    List<String> findActiveDomains();

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
              and merchant.domain in :domains
            """)
    int markInactiveByDomainIn(@Param("domains") Collection<String> domains, @Param("updatedAt") Instant updatedAt);

    @Modifying
    @Query(value = """
            with desired_state as (
                select candidate.id,
                       exists (
                            select 1
                            from merchant_raw source
                            where source.active = true
                              and source.merchant_id = candidate.id
                       )
                       and (
                            exists (
                                select 1
                                from merchant_identity identity
                                where identity.merchant_id = candidate.id
                                  and identity.namespace = 'DOMAIN'
                                  and identity.role = 'STOREFRONT_DOMAIN'
                            )
                            or (
                                exists (
                                    select 1
                                    from merchant_raw source
                                    where source.active = true
                                      and source.merchant_id = candidate.id
                                      and source.source = 'HUGGING_FACE'
                                      and lower(trim(trailing '.' from btrim(source.domain))) <> 'myshopify.com'
                                      and lower(trim(trailing '.' from btrim(source.domain))) not like '%.myshopify.com'
                                )
                            )
                       )
                       and 1 = (
                            select count(*)
                            from merchant_integration integration
                            where integration.merchant_id = candidate.id
                              and integration.provider = 'GENERIC_UCP'
                              and integration.status = 'ACTIVE'
                              and integration.endpoint ~* '^https://'
                              and exists (
                                  select 1
                                  from merchant_integration_role integration_role
                                  where integration_role.merchant_integration_id = integration.id
                                    and integration_role.role = 'STOREFRONT_CATALOG'
                              )
                       ) as should_be_active
                from merchant candidate
            )
            update merchant
            set active = desired_state.should_be_active,
                updated_at = :updatedAt
            from desired_state
            where merchant.id = desired_state.id
              and merchant.active is distinct from desired_state.should_be_active
            """, nativeQuery = true)
    int synchronizeActiveWithSources(@Param("updatedAt") Instant updatedAt);

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
