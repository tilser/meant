package com.meant.api.module.cart.service;

import java.time.Duration;
import java.util.concurrent.locks.LockSupport;
import org.springframework.stereotype.Component;

@Component
public class CartRetrySleeper {
    public void sleep(Duration delay) {
        if (delay != null && !delay.isZero()) {
            LockSupport.parkNanos(delay.toNanos());
        }
    }
}
