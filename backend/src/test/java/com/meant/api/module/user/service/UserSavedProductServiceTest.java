package com.meant.api.module.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.meant.api.module.user.entity.User;
import com.meant.api.module.user.entity.UserSavedProduct;
import com.meant.api.module.user.exception.UserException;
import com.meant.api.module.user.properties.UserCollectionProperties;
import com.meant.api.module.user.repository.UserSavedProductRepository;
import com.meant.api.module.user.service.command.SaveUserProductCommand;
import com.meant.api.module.user.service.command.UpsertUserCommand;
import com.meant.api.module.user.service.dto.UserSavedProductResult;
import com.meant.api.module.user.service.query.ListSavedProductsQuery;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import tools.jackson.databind.ObjectMapper;

class UserSavedProductServiceTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000016");

    private FakeUserSavedProductRepository repository;
    private UserSavedProductService service;

    @BeforeEach
    void setUp() {
        repository = new FakeUserSavedProductRepository();
        service = new UserSavedProductService(
                new FakeUserService(),
                repository.proxy(),
                new NoopUserTasteProfileService(),
                collectionProperties(50),
                new ObjectMapper()
        );
    }

    @Test
    void listAppliesPageAndLimitAtRepositoryBoundary() {
        service.save(upsertCommand(), product("merchant.example:one", "One"));
        service.save(upsertCommand(), product("merchant.example:two", "Two"));
        service.save(upsertCommand(), product("merchant.example:three", "Three"));

        List<UserSavedProductResult> firstPage = service.list(
                upsertCommand(),
                new ListSavedProductsQuery(USER_ID, 0, 2)
        );
        List<UserSavedProductResult> secondPage = service.list(
                upsertCommand(),
                new ListSavedProductsQuery(USER_ID, 1, 2)
        );

        assertThat(firstPage).hasSize(2);
        assertThat(secondPage).hasSize(1);
    }

    @Test
    void saveRejectsNewProductsPastUserQuotaButAllowsRefreshes() {
        UserSavedProductService quotaService = new UserSavedProductService(
                new FakeUserService(),
                repository.proxy(),
                new NoopUserTasteProfileService(),
                collectionProperties(1),
                new ObjectMapper()
        );

        quotaService.save(upsertCommand(), product("merchant.example:one", "One"));
        UserSavedProductResult refreshed = quotaService.save(
                upsertCommand(),
                product("merchant.example:one", "Updated One")
        );

        assertThat(refreshed.name()).isEqualTo("Updated One");
        assertThatThrownBy(() -> quotaService.save(upsertCommand(), product("merchant.example:two", "Two")))
                .isInstanceOf(UserException.class)
                .hasMessageContaining("Saved product quota exceeded");
    }

    private SaveUserProductCommand product(String productKey, String name) {
        return new SaveUserProductCommand(
                USER_ID,
                productKey,
                "hash-" + name,
                name,
                "Field Loom",
                "Clothing",
                "#f3f0e8",
                "https://example.test/" + name + ".jpg",
                "https://example.test/" + name,
                false,
                90,
                42.0d,
                1,
                List.of("organic"),
                List.of(),
                "A strong saved match.",
                List.of("Durable"),
                List.of("Limited colors"),
                new SaveUserProductCommand.Review(4.8d, 200, "Well reviewed."),
                List.of(new SaveUserProductCommand.Offer(
                        "Field Loom",
                        42.0d,
                        "Tomorrow",
                        "merchant-1",
                        "merchant.example",
                        "variant-1",
                        "Default",
                        true
                )),
                null,
                List.of()
        );
    }

    private UpsertUserCommand upsertCommand() {
        return new UpsertUserCommand(USER_ID, "saved@example.com", "Saved", "User");
    }

    private UserCollectionProperties collectionProperties(int savedProductQuota) {
        int safeLimit = Math.min(50, savedProductQuota);
        return new UserCollectionProperties(
                new UserCollectionProperties.SavedProducts(safeLimit, 100, savedProductQuota, safeLimit, safeLimit),
                new UserCollectionProperties.Inventory(50, 100, 500)
        );
    }

    static class FakeUserService extends UserService {

        FakeUserService() {
            super(null);
        }

        @Override
        public User upsert(UpsertUserCommand command) {
            return null;
        }
    }

    static class NoopUserTasteProfileService extends UserTasteProfileService {

        NoopUserTasteProfileService() {
            super(null, null, null, null);
        }

        @Override
        public void recordSavedProduct(UUID userId, SaveUserProductCommand command, java.time.Instant now) {
        }
    }

    static class FakeUserSavedProductRepository {

        private final List<UserSavedProduct> products = new ArrayList<>();

        UserSavedProductRepository proxy() {
            return (UserSavedProductRepository) Proxy.newProxyInstance(
                    UserSavedProductRepository.class.getClassLoader(),
                    new Class<?>[]{UserSavedProductRepository.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "save" -> save((UserSavedProduct) args[0]);
                        case "findByUserIdOrderByCreatedAtDesc" -> page(byUser((UUID) args[0]), (Pageable) args[1]);
                        case "findByUserIdAndProductKey" -> products.stream()
                                .filter(product -> product.getUserId().equals(args[0]))
                                .filter(product -> product.getProductKey().equals(args[1]))
                                .findFirst();
                        case "countByUserId" -> (long) byUser((UUID) args[0]).size();
                        case "deleteByUserIdAndProductKey" -> deleteByUserIdAndProductKey(
                                (UUID) args[0],
                                (String) args[1]);
                        default -> throw new UnsupportedOperationException(method.getName());
                    }
            );
        }

        private UserSavedProduct save(UserSavedProduct product) {
            products.removeIf(existing -> existing.getId().equals(product.getId()));
            products.add(product);
            return product;
        }

        private List<UserSavedProduct> byUser(UUID userId) {
            return products.stream()
                    .filter(product -> product.getUserId().equals(userId))
                    .sorted(Comparator.comparing(UserSavedProduct::getCreatedAt).reversed())
                    .toList();
        }

        private List<UserSavedProduct> page(List<UserSavedProduct> source, Pageable pageable) {
            int start = (int) pageable.getOffset();
            if (start >= source.size()) {
                return List.of();
            }
            int end = Math.min(start + pageable.getPageSize(), source.size());
            return source.subList(start, end);
        }

        private long deleteByUserIdAndProductKey(UUID userId, String productKey) {
            boolean removed = products.removeIf(product -> product.getUserId().equals(userId)
                    && product.getProductKey().equals(productKey));
            return removed ? 1L : 0L;
        }
    }
}
