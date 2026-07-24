package com.meant.api.module.user.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.meant.api.module.user.entity.UserSettingsLocation;
import com.meant.api.module.user.entity.UserSettingsLocationId;
import com.meant.api.module.user.repository.UserSettingsLocationRepository;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class UserCommerceContextServiceTest {

    @Test
    void normalizesUkToGbWithOneSettingsRead() {
        AtomicInteger reads = new AtomicInteger();
        UserCommerceContextService service = new UserCommerceContextService(repository(
                List.of(location("UK")), reads));

        assertThat(service.find(UUID.randomUUID()).countryCode()).isEqualTo("GB");
        assertThat(reads).hasValue(1);
    }

    @Test
    void invalidOrMissingLocationDoesNotFabricateCountry() {
        AtomicInteger reads = new AtomicInteger();
        UserCommerceContextService invalid = new UserCommerceContextService(repository(
                List.of(location("XX")), reads));

        assertThat(invalid.find(UUID.randomUUID()).countryCode()).isNull();
        assertThat(reads).hasValue(1);

        AtomicInteger missingReads = new AtomicInteger();
        UserCommerceContextService missing = new UserCommerceContextService(repository(List.of(), missingReads));
        assertThat(missing.find(UUID.randomUUID()).countryCode()).isNull();
        assertThat(missingReads).hasValue(1);
    }

    private UserSettingsLocation location(String code) {
        return UserSettingsLocation.builder()
                .id(new UserSettingsLocationId(UUID.randomUUID(), code, "city"))
                .country(code)
                .locationCode(code)
                .locationCity("city")
                .displayOrder(0)
                .build();
    }

    private UserSettingsLocationRepository repository(
            List<UserSettingsLocation> locations, AtomicInteger reads) {
        return (UserSettingsLocationRepository) Proxy.newProxyInstance(
                UserSettingsLocationRepository.class.getClassLoader(),
                new Class<?>[]{UserSettingsLocationRepository.class},
                (proxy, method, args) -> {
                    if ("findByIdUserIdOrderByDisplayOrderAsc".equals(method.getName())) {
                        reads.incrementAndGet();
                        return locations;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }
}
