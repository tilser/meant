package com.meant.api.module.merchant.repository;

import com.meant.api.module.merchant.entity.Merchant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MerchantRepository extends JpaRepository<Merchant, UUID> {

    Optional<Merchant> findByDomain(String domain);

    List<Merchant> findByDomainIn(Collection<String> domains);
}
