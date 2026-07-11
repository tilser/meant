package com.meant.api.module.checkout.repository;

import com.meant.api.module.checkout.entity.CheckoutCanaryEvent;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CheckoutCanaryEventRepository extends JpaRepository<CheckoutCanaryEvent, UUID> {
}
