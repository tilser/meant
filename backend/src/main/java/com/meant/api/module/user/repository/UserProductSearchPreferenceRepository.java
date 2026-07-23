package com.meant.api.module.user.repository;

import com.meant.api.module.user.constant.UserProductSearchAttributeName;
import com.meant.api.module.user.entity.UserProductSearchPreference;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserProductSearchPreferenceRepository extends JpaRepository<UserProductSearchPreference, UUID> {

    @Query(value = """
            SELECT 1
            FROM pg_advisory_xact_lock(hashtextextended(CAST(:userId AS text), 0))
            """, nativeQuery = true)
    int lockUserPreferenceWrites(@Param("userId") UUID userId);

    List<UserProductSearchPreference> findByUserIdOrderByScopeAscAttributeNameAsc(UUID userId);

    List<UserProductSearchPreference> findByUserIdAndScopeInAndAttributeNameIn(
            UUID userId,
            Collection<String> scopes,
            Collection<UserProductSearchAttributeName> attributeNames
    );

    void deleteByUserIdAndScopeAndAttributeName(
            UUID userId,
            String scope,
            UserProductSearchAttributeName attributeName
    );
}
