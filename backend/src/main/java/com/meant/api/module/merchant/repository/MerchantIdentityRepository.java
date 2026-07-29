package com.meant.api.module.merchant.repository;

import com.meant.api.module.merchant.constant.MerchantIdentityNamespace;
import com.meant.api.module.merchant.constant.MerchantIdentityRole;
import com.meant.api.module.merchant.entity.MerchantIdentity;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MerchantIdentityRepository extends JpaRepository<MerchantIdentity, UUID> {

    @EntityGraph(attributePaths = "merchant")
    List<MerchantIdentity> findByNamespaceInAndNormalizedValueIn(
            Collection<MerchantIdentityNamespace> namespaces,
            Collection<String> normalizedValues
    );

    List<MerchantIdentity> findByMerchantIdOrderByVerifiedAtAsc(UUID merchantId);

    List<MerchantIdentity> findByMerchantIdAndMerchantActiveTrueAndNamespaceAndRoleOrderByVerifiedAtAsc(
            UUID merchantId,
            MerchantIdentityNamespace namespace,
            MerchantIdentityRole role
    );
}
