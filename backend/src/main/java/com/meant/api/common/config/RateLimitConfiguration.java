package com.meant.api.common.config;

import com.meant.api.common.properties.RateLimitProperties;
import com.meant.api.common.service.RateLimitService;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RateLimitConfiguration {

    @Bean
    Clock rateLimitClock() {
        return Clock.systemUTC();
    }

    @Bean
    RateLimitService expensiveEndpointRateLimitService(RateLimitProperties properties, Clock rateLimitClock) {
        return new RateLimitService(
                properties.expensiveEndpoints().limits(),
                properties.expensiveEndpoints().bucketCache(),
                rateLimitClock
        );
    }

    @Bean
    ExpensiveEndpointRateLimitFilter expensiveEndpointRateLimitFilter(
            RateLimitProperties properties,
            RateLimitService expensiveEndpointRateLimitService
    ) {
        return new ExpensiveEndpointRateLimitFilter(properties, expensiveEndpointRateLimitService);
    }
}
