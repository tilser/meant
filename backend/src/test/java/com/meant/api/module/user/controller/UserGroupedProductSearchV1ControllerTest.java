package com.meant.api.module.user.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.meant.api.module.user.controller.request.UserCanonicalProductRehydrationRequest;
import com.meant.api.module.user.controller.request.UserSimilarProductSearchRequest;
import com.meant.api.module.user.controller.response.UserCanonicalProductRehydrationV1Response;
import com.meant.api.module.user.controller.request.UserProductSearchRequest;
import com.meant.api.module.user.controller.response.UserGroupedProductSearchV1Response;
import com.meant.api.module.user.controller.response.UserSimilarProductSearchV1Response;
import com.meant.api.module.user.service.UserSimilarProductSearchService;
import com.meant.api.module.user.service.UserGroupedProductSearchService;
import com.meant.api.module.user.service.UserCanonicalProductDetailService;
import com.meant.api.module.user.service.UserQualifiedProductSearchResolver;
import com.meant.api.module.user.service.command.EnsureUserProfileCommand;
import com.meant.api.module.user.service.command.SearchSimilarUserProductsCommand;
import com.meant.api.module.user.service.command.SearchUserProductsCommand;
import com.meant.api.module.user.service.dto.UserCanonicalProductsRehydrationResult;
import com.meant.api.module.user.service.dto.UserGroupedProductSearchResult;
import com.meant.api.module.user.service.dto.UserQualifiedProductSearchInput;
import com.meant.api.module.catalog.service.dto.CatalogDiscoveryFilters;
import com.meant.api.module.user.service.query.GetUserCanonicalProductDetailQuery;
import com.meant.api.module.user.service.query.RehydrateUserCanonicalProductsQuery;
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
                service, null, null, null, null, null, new FixedQualifiedSearchResolver());

        UserGroupedProductSearchV1Response response = controller.searchProducts(
                jwt,
                new UserProductSearchRequest(
                        "initial linen request",
                        FixedQualifiedSearchResolver.QUALIFICATION_ID,
                        FixedQualifiedSearchResolver.MERCHANT_ID,
                        5,
                        10),
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
        assertThat(service.searchCommand.merchantId()).isEqualTo(FixedQualifiedSearchResolver.MERCHANT_ID);
        assertThat(service.searchCommand.offset()).isEqualTo(5);
        assertThat(service.searchCommand.limit()).isEqualTo(10);
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
                null, null, detailService, null, null, null, null);

        assertThatThrownBy(() -> controller.getProductDetail(jwt, "grouped-product-v3_key", "offer-v2_key"))
                .isInstanceOf(UserException.class);

        assertThat(detailService.query.userId()).isEqualTo(userId);
        assertThat(detailService.query.canonicalProductKey()).isEqualTo("grouped-product-v3_key");
        assertThat(detailService.query.selectedOfferKey()).isEqualTo("offer-v2_key");
    }

    @Test
    void mapsAuthenticatedUserAndCanonicalKeysToBatchRehydration() {
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
                null, null, detailService, null, null, null, null);

        UserCanonicalProductRehydrationV1Response response = controller.rehydrateProducts(
                jwt,
                new UserCanonicalProductRehydrationRequest(List.of(
                        " grouped-product-v3_first ",
                        "grouped-product-v3_missing"
                ))
        );

        assertThat(detailService.rehydrationQuery.userId()).isEqualTo(userId);
        assertThat(detailService.rehydrationQuery.canonicalProductKeys())
                .containsExactly("grouped-product-v3_first", "grouped-product-v3_missing");
        assertThat(response.products()).isEmpty();
        assertThat(response.unavailableCanonicalProductKeys())
                .containsExactly("grouped-product-v3_missing");
    }

    @Test
    void mapsTheCanonicalKeyAndOriginatingQualificationToSimilaritySearch() {
        CapturingSimilarProductSearchService service = new CapturingSimilarProductSearchService();
        UUID userId = UUID.fromString("60000000-0000-0000-0000-000000000001");
        UUID qualificationId = UUID.fromString("60000000-0000-0000-0000-000000000099");
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(userId.toString())
                .claim("email", "shopper@example.com")
                .issuedAt(Instant.parse("2026-07-10T10:00:00Z"))
                .expiresAt(Instant.parse("2026-07-10T11:00:00Z"))
                .build();
        MockHttpServletRequest httpRequest = new MockHttpServletRequest();
        httpRequest.setRemoteAddr("192.0.2.20");
        httpRequest.addHeader("User-Agent", " similarity-client ");
        UserGroupedProductSearchV1Controller controller = new UserGroupedProductSearchV1Controller(
                null, service, null, null, null, null, null);

        UserSimilarProductSearchV1Response response = controller.searchSimilarProducts(
                jwt,
                "grouped-product-v3_anchor",
                new UserSimilarProductSearchRequest("trail running shoes", qualificationId),
                httpRequest
        );

        assertThat(response.query()).isEqualTo("trail running shoes");
        assertThat(service.profileCommand.id()).isEqualTo(userId);
        assertThat(service.command).satisfies(command -> {
            assertThat(command.userId()).isEqualTo(userId);
            assertThat(command.canonicalProductKey()).isEqualTo("grouped-product-v3_anchor");
            assertThat(command.query()).isEqualTo("trail running shoes");
            assertThat(command.qualificationId()).isEqualTo(qualificationId);
            assertThat(command.buyerIp()).isEqualTo("192.0.2.20");
            assertThat(command.userAgent()).isEqualTo("similarity-client");
        });
    }

    private static final class CapturingGroupedProductSearchService extends UserGroupedProductSearchService {
        private EnsureUserProfileCommand profileCommand;
        private SearchUserProductsCommand searchCommand;

        private CapturingGroupedProductSearchService() {
            super(null, null, null, null, null, null, null, null, null);
        }

        @Override
        public UserGroupedProductSearchResult search(
                EnsureUserProfileCommand profileCommand,
                SearchUserProductsCommand searchCommand,
                CatalogDiscoveryFilters discoveryFilters
        ) {
            this.profileCommand = profileCommand;
            this.searchCommand = searchCommand;
            return new UserGroupedProductSearchResult(
                    "linen", "linen", "profile", false, 5, 10, 15, true, false,
                    List.of(), Map.of(), Map.of(), Map.of(), List.of(), 0, false, List.of());
        }
    }

    private static final class FixedQualifiedSearchResolver extends UserQualifiedProductSearchResolver {
        private static final UUID QUALIFICATION_ID =
                UUID.fromString("60000000-0000-0000-0000-000000000099");
        private static final UUID MERCHANT_ID =
                UUID.fromString("60000000-0000-0000-0000-000000000096");

        private FixedQualifiedSearchResolver() {
            super(null, null, null);
        }

        @Override
        public UserQualifiedProductSearchInput resolve(UUID userId, UUID qualificationId) {
            assertThat(qualificationId).isEqualTo(QUALIFICATION_ID);
            return new UserQualifiedProductSearchInput(
                    QUALIFICATION_ID,
                    UUID.fromString("60000000-0000-0000-0000-000000000098"),
                    MERCHANT_ID,
                    "linen",
                    new CatalogDiscoveryFilters(
                            true, List.of(), null, List.of(), null, List.of(), List.of(), List.of(), null, List.of())
            );
        }
    }

    private static final class CapturingProductDetailService extends UserCanonicalProductDetailService {
        private GetUserCanonicalProductDetailQuery query;
        private RehydrateUserCanonicalProductsQuery rehydrationQuery;

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

        @Override
        public UserCanonicalProductsRehydrationResult rehydrate(
                EnsureUserProfileCommand profileCommand,
                RehydrateUserCanonicalProductsQuery query
        ) {
            this.rehydrationQuery = query;
            return new UserCanonicalProductsRehydrationResult(
                    List.of(), List.of("grouped-product-v3_missing"));
        }
    }

    private static final class CapturingSimilarProductSearchService extends UserSimilarProductSearchService {
        private EnsureUserProfileCommand profileCommand;
        private SearchSimilarUserProductsCommand command;

        private CapturingSimilarProductSearchService() {
            super(null, null, null, null, null);
        }

        @Override
        public UserGroupedProductSearchResult search(
                EnsureUserProfileCommand profileCommand,
                SearchSimilarUserProductsCommand command
        ) {
            this.profileCommand = profileCommand;
            this.command = command;
            return new UserGroupedProductSearchResult(
                    command.query(), command.query(), "profile", false,
                    0, 20, null, false, false,
                    List.of(), 0, false, List.of());
        }
    }
}
