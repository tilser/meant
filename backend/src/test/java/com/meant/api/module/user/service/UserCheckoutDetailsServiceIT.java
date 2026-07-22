package com.meant.api.module.user.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.meant.api.PostgresIntegrationTestSupport;
import com.meant.api.module.user.repository.UserCheckoutDetailsRepository;
import com.meant.api.module.user.service.command.EnsureUserProfileCommand;
import com.meant.api.module.user.service.command.SaveUserCheckoutDetailsCommand;
import com.meant.api.module.user.service.dto.UserCheckoutDetailsResult;
import com.meant.api.module.user.service.query.GetUserCheckoutDetailsQuery;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class UserCheckoutDetailsServiceIT extends PostgresIntegrationTestSupport {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000091");

    @Autowired
    private UserCheckoutDetailsService userCheckoutDetailsService;

    @Autowired
    private UserCheckoutDetailsRepository userCheckoutDetailsRepository;

    @Autowired
    private UserService userService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        userCheckoutDetailsRepository.deleteAll();
        userService.ensureProfile(new EnsureUserProfileCommand(
                USER_ID,
                "identity@example.com",
                "Identity",
                "User"
        ));
    }

    @Test
    void saveInsertsAndAtomicallyReplacesTheUsersCheckoutDetails() {
        UserCheckoutDetailsResult first = userCheckoutDetailsService.save(command(
                "ada@example.com",
                "+44 20 7946 0958",
                " Flat 3 ",
                " London ",
                " gb "
        ));
        Instant createdAt = jdbcTemplate.queryForObject(
                "SELECT created_at FROM user_checkout_details WHERE user_id = ?",
                Instant.class,
                USER_ID
        );

        assertThat(first.email()).isEqualTo("ada@example.com");
        assertThat(first.phoneNumber()).isEqualTo("+44 20 7946 0958");
        assertThat(first.extendedAddress()).isEqualTo("Flat 3");
        assertThat(first.addressRegion()).isEqualTo("London");
        assertThat(first.addressCountry()).isEqualTo("GB");

        UserCheckoutDetailsResult replacement = userCheckoutDetailsService.save(command(
                "grace@example.com",
                " ",
                null,
                " ",
                "us"
        ));

        assertThat(replacement.email()).isEqualTo("grace@example.com");
        assertThat(replacement.phoneNumber()).isNull();
        assertThat(replacement.extendedAddress()).isNull();
        assertThat(replacement.addressRegion()).isNull();
        assertThat(replacement.addressCountry()).isEqualTo("US");
        assertThat(userCheckoutDetailsService.get(new GetUserCheckoutDetailsQuery(USER_ID)))
                .contains(replacement);
        assertThat(userCheckoutDetailsRepository.count()).isOne();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT created_at FROM user_checkout_details WHERE user_id = ?",
                Instant.class,
                USER_ID
        )).isEqualTo(createdAt);

        UserCheckoutDetailsResult unchanged = userCheckoutDetailsService.save(command(
                "grace@example.com",
                null,
                null,
                null,
                "US"
        ));

        assertThat(unchanged.updatedAt()).isEqualTo(replacement.updatedAt());
    }

    private SaveUserCheckoutDetailsCommand command(
            String email,
            String phoneNumber,
            String extendedAddress,
            String addressRegion,
            String addressCountry
    ) {
        return new SaveUserCheckoutDetailsCommand(
                USER_ID,
                email,
                " Ada ",
                " Lovelace ",
                phoneNumber,
                " 12 St James Square ",
                extendedAddress,
                " London ",
                addressRegion,
                " SW1Y 4LB ",
                addressCountry
        );
    }
}
