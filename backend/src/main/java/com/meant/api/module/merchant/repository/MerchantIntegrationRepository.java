package com.meant.api.module.merchant.repository;

import com.meant.api.module.merchant.constant.MerchantIntegrationProvider;
import com.meant.api.module.merchant.entity.MerchantIntegration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MerchantIntegrationRepository extends JpaRepository<MerchantIntegration, UUID> {

    @EntityGraph(attributePaths = "roles")
    List<MerchantIntegration> findByMerchantIdOrderByCreatedAtAsc(UUID merchantId);

    @EntityGraph(attributePaths = "roles")
    Optional<MerchantIntegration> findByProviderAndExternalMerchantId(
            MerchantIntegrationProvider provider,
            String externalMerchantId
    );

    @EntityGraph(attributePaths = "roles")
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
