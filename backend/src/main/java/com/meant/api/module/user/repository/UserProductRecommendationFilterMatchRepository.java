package com.meant.api.module.user.repository;

import com.meant.api.module.user.entity.UserProductRecommendationFilterMatch;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserProductRecommendationFilterMatchRepository
        extends JpaRepository<UserProductRecommendationFilterMatch, UUID> {

    List<UserProductRecommendationFilterMatch> findByExplanationIdInOrderByRankAsc(Collection<UUID> explanationIds);
}
