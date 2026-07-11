package com.meant.api.module.user.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.meant.api.module.user.controller.request.UserProductSearchRequest;
import com.meant.api.module.user.controller.response.UserGroupedProductSearchV1Response;
import com.meant.api.module.user.service.UserGroupedProductSearchService;
import com.meant.api.module.user.service.command.EnsureUserProfileCommand;
import com.meant.api.module.user.service.command.SearchUserProductsCommand;
import com.meant.api.module.user.service.dto.UserGroupedProductSearchResult;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.oauth2.jwt.Jwt;

class UserGroupedProductSearchV1ControllerTest {

    @Test
    void mapsTheVersionedRequestToValidatedServiceCommandsAndResponse() {
        CapturingGroupedProductSearchService service = new CapturingGroupedProductSearchService();
        UUID userId = UUID.fromString("60000000-0000-0000-0000-000000000001");
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(userId.toString())
                .claim("email", "shopper@example.com")
                .claim("user_metadata", Map.of("full_name", "Ada Shopper"))
                .issuedAt(Instant.parse("2026-07-10T10:00:00Z"))
                .expiresAt(Instant.parse("2026-07-10T11:00:00Z"))
                .build();
        MockHttpServletRequest httpRequest = new MockHttpServletRequest();
        httpRequest.setRemoteAddr("192.0.2.10");
        httpRequest.addHeader("User-Agent", " grouped-client ");
        UserGroupedProductSearchV1Controller controller = new UserGroupedProductSearchV1Controller(
                service, null, null, null);

        UserGroupedProductSearchV1Response response = controller.searchProducts(
                jwt,
                new UserProductSearchRequest("linen", null, 5, 10),
                httpRequest
        );

        assertThat(response.query()).isEqualTo("linen");
        assertThat(response.products()).isEmpty();
        assertThat(service.profileCommand)
                .extracting(
                        EnsureUserProfileCommand::id,
                        EnsureUserProfileCommand::email,
                        EnsureUserProfileCommand::firstName,
                        EnsureUserProfileCommand::surname
                )
                .containsExactly(userId, "shopper@example.com", "Ada", "Shopper");
        assertThat(service.searchCommand.buyerIp()).isEqualTo("192.0.2.10");
        assertThat(service.searchCommand.userAgent()).isEqualTo("grouped-client");
        assertThat(service.searchCommand.offset()).isEqualTo(5);
        assertThat(service.searchCommand.limit()).isEqualTo(10);
    }

    private static final class CapturingGroupedProductSearchService extends UserGroupedProductSearchService {

        private EnsureUserProfileCommand profileCommand;
        private SearchUserProductsCommand searchCommand;

        private CapturingGroupedProductSearchService() {
            super(null, null, null);
        }

        @Override
        public UserGroupedProductSearchResult search(
                EnsureUserProfileCommand profileCommand,
                SearchUserProductsCommand searchCommand
        ) {
            this.profileCommand = profileCommand;
            this.searchCommand = searchCommand;
            return new UserGroupedProductSearchResult(
                    "linen", "linen", "profile", false, 5, 10, 15, true, false,
                    List.of(), 0, false, List.of());
        }
    }
}
