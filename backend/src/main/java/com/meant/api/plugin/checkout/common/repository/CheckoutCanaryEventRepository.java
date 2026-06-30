package com.meant.api.plugin.checkout.common.repository;

import com.meant.api.plugin.checkout.common.entity.CheckoutCanaryEvent;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CheckoutCanaryEventRepository extends JpaRepository<CheckoutCanaryEvent, UUID> {
}
