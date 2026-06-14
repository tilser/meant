package com.meant.api.module.merchant.repository;

import com.meant.api.module.merchant.entity.Merchant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface MerchantRepository extends JpaRepository<Merchant, UUID> {

    Optional<Merchant> findByDomain(String domain);

    List<Merchant> findByDomainIn(Collection<String> domains);

    @Query("""
            select merchant
            from Merchant merchant
            where merchant.active = true
              and (
                exists (
                    select category.id
                    from MerchantCategory category
                    where category.merchant = merchant
                )
                or exists (
                    select popularSearch.id
                    from MerchantPopularSearch popularSearch
                    where popularSearch.merchant = merchant
                )
              )
              and (
                not exists (
                    select embedding.id
                    from MerchantRetrievalEmbedding embedding
                    where embedding.merchant = merchant
                )
                or exists (
                    select embedding.id
                    from MerchantRetrievalEmbedding embedding
                    where embedding.merchant = merchant
                      and (
                        embedding.active = false
                        or embedding.embeddingModel <> :embeddingModel
                        or merchant.updatedAt > embedding.embeddedAt
                      )
                )
              )
            order by merchant.updatedAt asc
            """)
    List<Merchant> findForRetrievalEmbeddingRefresh(String embeddingModel, Pageable pageable);
}
