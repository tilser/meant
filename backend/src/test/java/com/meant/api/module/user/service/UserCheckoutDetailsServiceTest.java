package com.meant.api.module.user.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.meant.api.module.user.entity.UserCheckoutDetails;
import com.meant.api.module.user.repository.UserCheckoutDetailsRepository;
import com.meant.api.module.user.service.command.SaveUserCheckoutDetailsCommand;
import com.meant.api.module.user.service.dto.UserCheckoutDetailsResult;
import com.meant.api.module.user.service.query.GetUserCheckoutDetailsQuery;
import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UserCheckoutDetailsServiceTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private final FakeUserCheckoutDetailsRepository repository = new FakeUserCheckoutDetailsRepository();
    private final UserCheckoutDetailsService service = new UserCheckoutDetailsService(repository.proxy());

    @Test
    void savesNormalizedDetailsAndReadsThemForTheUser() {
        UserCheckoutDetailsResult saved = service.save(new SaveUserCheckoutDetailsCommand(
                USER_ID,
                "ada@example.com",
                "  Ada  ",
                "  Lovelace  ",
                "  +44 20 7946 0958  ",
                "  12 St James's Square  ",
                "  Flat 4  ",
                "  London  ",
                "  Greater London  ",
                "  SW1Y 4LB  ",
                "  gb  "
        ));

        assertThat(saved.userId()).isEqualTo(USER_ID);
        assertThat(saved.email()).isEqualTo("ada@example.com");
        assertThat(saved.firstName()).isEqualTo("Ada");
        assertThat(saved.lastName()).isEqualTo("Lovelace");
        assertThat(saved.phoneNumber()).isEqualTo("+44 20 7946 0958");
        assertThat(saved.streetAddress()).isEqualTo("12 St James's Square");
        assertThat(saved.extendedAddress()).isEqualTo("Flat 4");
        assertThat(saved.addressLocality()).isEqualTo("London");
        assertThat(saved.addressRegion()).isEqualTo("Greater London");
        assertThat(saved.postalCode()).isEqualTo("SW1Y 4LB");
        assertThat(saved.addressCountry()).isEqualTo("GB");
        assertThat(saved.updatedAt()).isNotNull();

        assertThat(service.get(new GetUserCheckoutDetailsQuery(USER_ID)))
                .contains(saved);
    }

    @Test
    void clearsOptionalValuesWhenReplacementContainsNullOrBlankText() {
        service.save(new SaveUserCheckoutDetailsCommand(
                USER_ID,
                "ada@example.com",
                "Ada",
                "Lovelace",
                "+44 20 7946 0958",
                "12 St James's Square",
                "Flat 4",
                "London",
                "Greater London",
                "SW1Y 4LB",
                "GB"
        ));

        UserCheckoutDetailsResult replaced = service.save(new SaveUserCheckoutDetailsCommand(
                USER_ID,
                "ada@example.com",
                "Ada",
                "Lovelace",
                "   ",
                "12 St James's Square",
                null,
                "London",
                "\t",
                "SW1Y 4LB",
                "GB"
        ));

        assertThat(replaced.phoneNumber()).isNull();
        assertThat(replaced.extendedAddress()).isNull();
        assertThat(replaced.addressRegion()).isNull();
        assertThat(service.get(new GetUserCheckoutDetailsQuery(USER_ID)))
                .get()
                .extracting(
                        UserCheckoutDetailsResult::phoneNumber,
                        UserCheckoutDetailsResult::extendedAddress,
                        UserCheckoutDetailsResult::addressRegion
                )
                .containsExactly(null, null, null);
    }

    private static final class FakeUserCheckoutDetailsRepository {

        private final Map<UUID, UserCheckoutDetails> records = new LinkedHashMap<>();

        private UserCheckoutDetailsRepository proxy() {
            return (UserCheckoutDetailsRepository) Proxy.newProxyInstance(
                    UserCheckoutDetailsRepository.class.getClassLoader(),
                    new Class<?>[]{UserCheckoutDetailsRepository.class},
                    (proxy, method, args) -> {
                        String methodName = method.getName();
                        if ("upsert".equals(methodName)) {
                            upsert(args);
                            return 1;
                        }
                        if ("findById".equals(methodName)) {
                            return Optional.ofNullable(records.get(args[0]));
                        }
                        if ("toString".equals(methodName)) {
                            return "FakeUserCheckoutDetailsRepository";
                        }
                        throw new UnsupportedOperationException(methodName);
                    }
            );
        }

        private void upsert(Object[] args) {
            UUID userId = (UUID) args[0];
            Instant now = (Instant) args[11];
            UserCheckoutDetails existing = records.get(userId);
            records.put(userId, UserCheckoutDetails.builder()
                    .userId(userId)
                    .email((String) args[1])
                    .firstName((String) args[2])
                    .lastName((String) args[3])
                    .phoneNumber((String) args[4])
                    .streetAddress((String) args[5])
                    .extendedAddress((String) args[6])
                    .addressLocality((String) args[7])
                    .addressRegion((String) args[8])
                    .postalCode((String) args[9])
                    .addressCountry((String) args[10])
                    .createdAt(existing == null ? now : existing.getCreatedAt())
                    .updatedAt(now)
                    .build());
        }
    }
}
