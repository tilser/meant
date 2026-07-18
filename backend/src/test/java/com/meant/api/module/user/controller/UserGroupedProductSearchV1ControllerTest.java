package com.meant.api.module.user.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.meant.api.module.user.controller.request.UserProductSearchRequest;
import com.meant.api.module.user.controller.response.UserGroupedProductSearchV1Response;
import com.meant.api.module.user.service.UserGroupedProductSearchService;
import com.meant.api.module.user.service.UserCanonicalProductDetailService;
import com.meant.api.module.user.service.UserQualifiedProductSearchResolver;
import com.meant.api.module.user.service.command.EnsureUserProfileCommand;
import com.meant.api.module.user.service.command.SearchUserProductsCommand;
import com.meant.api.module.user.service.dto.UserGroupedProductSearchResult;
import com.meant.api.module.user.service.dto.UserQualifiedProductSearchInput;
import com.meant.api.module.user.service.dto.UserProductSearchHistoryContext;
import com.meant.api.module.catalog.service.dto.CatalogDiscoveryFilters;
import com.meant.api.module.user.service.query.GetUserCanonicalProductDetailQuery;
import com.meant.api.module.user.exception.UserException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
                service, null, null, null, null, new FixedQualifiedSearchResolver());

        UserGroupedProductSearchV1Response response = controller.searchProducts(
                jwt,
                new UserProductSearchRequest(
                        "initial linen request",
                        FixedQualifiedSearchResolver.QUALIFICATION_ID,
                        null,
                        5,
                        10),
                httpRequest
        );

        assertThat(response.query()).isEqualTo("linen");
        assertThat(response.products()).isEmpty();
        assertThat(response.productResultSetId()).isEqualTo(CapturingGroupedProductSearchService.RESULT_SET_ID);
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
        assertThat(service.historyContext.conversationId())
                .isEqualTo(UUID.fromString("60000000-0000-0000-0000-000000000098"));
        assertThat(service.historyContext.qualificationId()).isEqualTo(FixedQualifiedSearchResolver.QUALIFICATION_ID);
    }

    @Test
    void mapsOnlyAuthenticatedUserAndServerIssuedKeysToTheDetailQuery() {
        CapturingProductDetailService detailService = new CapturingProductDetailService();
        UUID userId = UUID.fromString("60000000-0000-0000-0000-000000000001");
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(userId.toString())
                .claim("email", "shopper@example.com")
                .issuedAt(Instant.parse("2026-07-10T10:00:00Z"))
                .expiresAt(Instant.parse("2026-07-10T11:00:00Z"))
                .build();
        UserGroupedProductSearchV1Controller controller = new UserGroupedProductSearchV1Controller(
                null, detailService, null, null, null, null);

        assertThatThrownBy(() -> controller.getProductDetail(jwt, "grouped-product-v3_key", "offer-v2_key"))
                .isInstanceOf(UserException.class);

        assertThat(detailService.query.userId()).isEqualTo(userId);
        assertThat(detailService.query.canonicalProductKey()).isEqualTo("grouped-product-v3_key");
        assertThat(detailService.query.selectedOfferKey()).isEqualTo("offer-v2_key");
    }

    private static final class CapturingGroupedProductSearchService extends UserGroupedProductSearchService {
        private static final UUID RESULT_SET_ID =
                UUID.fromString("60000000-0000-0000-0000-000000000097");

        private EnsureUserProfileCommand profileCommand;
        private SearchUserProductsCommand searchCommand;
        private UserProductSearchHistoryContext historyContext;

        private CapturingGroupedProductSearchService() {
            super(null, null, null, null, null, null, null, null, null);
        }

        @Override
        public UserGroupedProductSearchResult search(
                EnsureUserProfileCommand profileCommand,
                SearchUserProductsCommand searchCommand,
                CatalogDiscoveryFilters discoveryFilters,
                UserProductSearchHistoryContext historyContext
        ) {
            this.profileCommand = profileCommand;
            this.searchCommand = searchCommand;
            this.historyContext = historyContext;
            return new UserGroupedProductSearchResult(
                    "linen", "linen", "profile", false, 5, 10, 15, true, false,
                    List.of(), Map.of(), Map.of(), Map.of(), List.of(), 0, false, List.of(), RESULT_SET_ID);
        }
    }

    private static final class FixedQualifiedSearchResolver extends UserQualifiedProductSearchResolver {
        private static final UUID QUALIFICATION_ID =
                UUID.fromString("60000000-0000-0000-0000-000000000099");

        private FixedQualifiedSearchResolver() {
            super(null, null, null);
        }

        @Override
        public UserQualifiedProductSearchInput resolve(UUID userId, UUID qualificationId) {
            assertThat(qualificationId).isEqualTo(QUALIFICATION_ID);
            return new UserQualifiedProductSearchInput(
                    QUALIFICATION_ID,
                    UUID.fromString("60000000-0000-0000-0000-000000000098"),
                    null,
                    "linen",
                    new CatalogDiscoveryFilters(
                            true, List.of(), null, List.of(), null, List.of(), List.of(), List.of(), null, List.of())
            );
        }
    }

    private static final class CapturingProductDetailService extends UserCanonicalProductDetailService {
        private GetUserCanonicalProductDetailQuery query;

        private CapturingProductDetailService() {
            super(null, null, null, null, null);
        }

        @Override
        public com.meant.api.module.user.service.dto.UserProductDetailResult get(
                EnsureUserProfileCommand profileCommand,
                GetUserCanonicalProductDetailQuery query
        ) {
            this.query = query;
            throw UserException.notFound("controlled test stop");
        }
    }
}
