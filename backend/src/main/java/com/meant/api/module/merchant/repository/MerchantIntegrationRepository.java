package com.meant.api.module.merchant.repository;

import com.meant.api.module.merchant.constant.MerchantIntegrationProvider;
import com.meant.api.module.merchant.constant.MerchantIntegrationRole;
import com.meant.api.module.merchant.constant.MerchantIntegrationStatus;
import com.meant.api.module.merchant.entity.MerchantIntegration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MerchantIntegrationRepository extends JpaRepository<MerchantIntegration, UUID> {

    @EntityGraph(attributePaths = {"roles", "merchant"})
    List<MerchantIntegration> findByMerchantIdOrderByCreatedAtAsc(UUID merchantId);

    @EntityGraph(attributePaths = {"roles", "merchant"})
    List<MerchantIntegration> findByMerchantIdAndProviderOrderByCreatedAtAsc(
            UUID merchantId,
            MerchantIntegrationProvider provider
    );

    @EntityGraph(attributePaths = {"roles", "merchant"})
    @Query("""
            select distinct integration
            from MerchantIntegration integration
            join integration.roles role
            where integration.merchant.id = :merchantId
              and integration.provider = :provider
              and integration.status = :status
              and role = :role
            order by integration.createdAt asc
            """)
    List<MerchantIntegration> findByMerchantIdAndProviderAndStatusAndRole(
            @Param("merchantId") UUID merchantId,
            @Param("provider") MerchantIntegrationProvider provider,
            @Param("status") MerchantIntegrationStatus status,
            @Param("role") MerchantIntegrationRole role
    );

    @EntityGraph(attributePaths = {"roles", "merchant"})
    List<MerchantIntegration> findByMerchantIdInOrderByCreatedAtAsc(Set<UUID> merchantIds);

    @EntityGraph(attributePaths = {"roles", "merchant"})
    List<MerchantIntegration> findByIdInOrderByCreatedAtAsc(Set<UUID> integrationIds);

    @EntityGraph(attributePaths = {"roles", "merchant"})
    Optional<MerchantIntegration> findByProviderAndExternalMerchantId(
            MerchantIntegrationProvider provider,
            String externalMerchantId
    );

    // The lower expression intentionally matches the case-insensitive unique index for non-JPA writers.
    @EntityGraph(attributePaths = {"roles", "merchant"})
    @Query("""
            select integration
            from MerchantIntegration integration
            where lower(integration.verifiedDomain) = :verifiedDomain
            order by integration.createdAt asc
            """)
    List<MerchantIntegration> findByNormalizedVerifiedDomainOrderByCreatedAtAsc(
            @Param("verifiedDomain") String verifiedDomain
    );
}
