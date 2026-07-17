package com.meant.api.module.user.repository;

import com.meant.api.module.user.entity.UserDiscoverConversationTombstone;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserDiscoverConversationTombstoneRepository
        extends JpaRepository<UserDiscoverConversationTombstone, UUID> {
}
