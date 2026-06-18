package com.meant.api.module.user.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.meant.api.common.properties.OpenRouterProperties;
import com.meant.api.module.merchant.service.dto.MerchantSemanticProductResult;
import com.meant.api.module.user.constant.UserInventoryCategory;
import com.meant.api.module.user.constant.UserInventoryRecommendationRelationship;
import com.meant.api.module.user.constant.UserInventorySource;
import com.meant.api.module.user.entity.User;
import com.meant.api.module.user.entity.UserInventoryItem;
import com.meant.api.module.user.repository.UserInventoryItemRepository;
import com.meant.api.module.user.service.command.CreateUserInventoryItemCommand;
import com.meant.api.module.user.service.command.CreateUserInventoryPhotoItemCommand;
import com.meant.api.module.user.service.command.DeleteUserInventoryItemCommand;
import com.meant.api.module.user.service.command.ImportPurchasedInventoryItemsCommand;
import com.meant.api.module.user.service.command.UpdateUserInventoryItemCommand;
import com.meant.api.module.user.service.command.UpsertUserCommand;
import com.meant.api.module.user.service.dto.UserInventoryItemResult;
import com.meant.api.module.user.service.dto.UserInventoryPhotoRecognitionResult;
import com.meant.api.module.user.service.dto.UserInventoryRecommendationSignal;
import com.meant.api.module.user.service.dto.UserProductSearchProductSnapshot;
import com.meant.api.module.user.service.query.ExportUserInventoryQuery;
import com.meant.api.module.user.service.query.ListUserInventoryItemsQuery;
import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class UserInventoryServiceTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000015");
    private static final Instant NOW = Instant.parse("2026-06-18T10:00:00Z");

    private FakeUserInventoryItemRepository repository;
    private FakeUserService userService;
    private FakePhotoRecognitionService photoRecognitionService;
    private UserInventoryService service;

    @BeforeEach
    void setUp() {
        repository = new FakeUserInventoryItemRepository();
        userService = new FakeUserService();
        photoRecognitionService = new FakePhotoRecognitionService();
        service = new UserInventoryService(
                userService,
                repository.proxy(),
                photoRecognitionService,
                new ObjectMapper()
        );
    }

    @Test
    void createListsExportsAndDeletesManualInventoryItems() {
        UserInventoryItemResult item = service.create(upsertCommand(), new CreateUserInventoryItemCommand(
                USER_ID,
                UserInventorySource.MANUAL,
                null,
                null,
                "Heavyweight Organic Cotton Tee",
                "Field Loom",
                UserInventoryCategory.APPAREL,
                "White crewneck tee",
                "https://example.test/tee.jpg",
                null,
                null,
                2,
                "pcs",
                "Wardrobe",
                "Worn weekly",
                List.of("organic cotton", "white"),
                false,
                false,
                null,
                null
        ));

        assertThat(item.source()).isEqualTo(UserInventorySource.MANUAL);
        assertThat(item.category()).isEqualTo(UserInventoryCategory.APPAREL);
        assertThat(item.quantity()).isEqualTo(2);

        List<UserInventoryItemResult> listed = service.list(
                upsertCommand(),
                new ListUserInventoryItemsQuery(USER_ID, UserInventoryCategory.APPAREL, false)
        );
        assertThat(listed).singleElement()
                .extracting(UserInventoryItemResult::name)
                .isEqualTo("Heavyweight Organic Cotton Tee");

        assertThat(service.export(upsertCommand(), new ExportUserInventoryQuery(USER_ID)).items())
                .hasSize(1);

        service.delete(upsertCommand(), new DeleteUserInventoryItemCommand(USER_ID, item.id()));

        assertThat(service.list(upsertCommand(), new ListUserInventoryItemsQuery(USER_ID, null, false)))
                .isEmpty();
    }

    @Test
    void createFromPhotoMergesRecognitionWithFallbackFields() {
        photoRecognitionService.nextResult = Optional.of(new UserInventoryPhotoRecognitionResult(
                "Blue Linen Shirt",
                "Northbound",
                UserInventoryCategory.APPAREL,
                "Button-down shirt on a hanger",
                List.of("linen", "blue"),
                false
        ));

        UserInventoryItemResult item = service.createFromPhoto(
                upsertCommand(),
                new CreateUserInventoryPhotoItemCommand(
                        USER_ID,
                        "data:image/jpeg;base64,abc",
                        null,
                        null,
                        null,
                        null,
                        1,
                        null,
                        "Closet",
                        "Recognized from closet photo",
                        List.of("summer"),
                        null,
                        false,
                        null
                )
        );

        assertThat(item.source()).isEqualTo(UserInventorySource.PHOTO);
        assertThat(item.name()).isEqualTo("Blue Linen Shirt");
        assertThat(item.brand()).isEqualTo("Northbound");
        assertThat(item.category()).isEqualTo(UserInventoryCategory.APPAREL);
        assertThat(item.imageUrl()).isEqualTo("data:image/jpeg;base64,abc");
        assertThat(item.attributes()).containsExactly("summer", "linen", "blue");
    }

    @Test
    void listCanFilterRestockEnabledPantryItems() {
        service.create(upsertCommand(), manualItem("Olive Oil", UserInventoryCategory.PANTRY, true));
        service.create(upsertCommand(), manualItem("Merino Sweater", UserInventoryCategory.APPAREL, false));

        List<UserInventoryItemResult> restocks = service.list(
                upsertCommand(),
                new ListUserInventoryItemsQuery(USER_ID, null, true)
        );

        assertThat(restocks).singleElement()
                .extracting(UserInventoryItemResult::name)
                .isEqualTo("Olive Oil");
    }

    @Test
    void importPurchasedItemsIsIdempotentBySourceProductKey() {
        ImportPurchasedInventoryItemsCommand.PurchasedItem item =
                new ImportPurchasedInventoryItemsCommand.PurchasedItem(
                        "merchant.example:variant-1",
                        "hash-1",
                        "Cold-Pressed Extra Virgin Olive Oil",
                        "Casa Verde",
                        null,
                        null,
                        1,
                        NOW
                );

        service.importPurchasedItems(new ImportPurchasedInventoryItemsCommand(USER_ID, List.of(item)));
        service.importPurchasedItems(new ImportPurchasedInventoryItemsCommand(USER_ID, List.of(new ImportPurchasedInventoryItemsCommand.PurchasedItem(
                "merchant.example:variant-1",
                "hash-1",
                "Cold-Pressed Extra Virgin Olive Oil",
                "Casa Verde",
                null,
                null,
                2,
                NOW
        ))));

        List<UserInventoryItemResult> items = service.list(
                upsertCommand(),
                new ListUserInventoryItemsQuery(USER_ID, null, false)
        );

        assertThat(items).singleElement()
                .satisfies(result -> {
                    assertThat(result.source()).isEqualTo(UserInventorySource.MEANT_PURCHASE);
                    assertThat(result.sourceProductKey()).isEqualTo("merchant.example:variant-1");
                    assertThat(result.category()).isEqualTo(UserInventoryCategory.PANTRY);
                    assertThat(result.quantity()).isEqualTo(3);
                });
    }

    @Test
    void recommendationSignalsClassifyRestocksDuplicatesComplementsAndNone() {
        UserInventoryItemResult oil = service.create(upsertCommand(), manualItem(
                "Cold-Pressed Extra Virgin Olive Oil",
                UserInventoryCategory.PANTRY,
                true
        ));
        service.create(upsertCommand(), manualItem(
                "Heavyweight Organic Cotton Tee",
                UserInventoryCategory.APPAREL,
                false
        ));

        List<UserProductSearchProductSnapshot> products = List.of(
                snapshot("merchant.example:oil", "Cold-Pressed Extra Virgin Olive Oil"),
                snapshot("merchant.example:tee", "Heavyweight Organic Cotton Tee"),
                snapshot("merchant.example:sweater", "Merino Crew Sweater"),
                snapshot("merchant.example:hub", "USB-C Hub")
        );

        var signals = service.recommendationSignals(USER_ID, products);

        assertThat(signals.get("merchant.example:oil"))
                .extracting(UserInventoryRecommendationSignal::relationship, UserInventoryRecommendationSignal::inventoryItemId)
                .containsExactly(UserInventoryRecommendationRelationship.RESTOCK, oil.id());
        assertThat(signals.get("merchant.example:tee").relationship())
                .isEqualTo(UserInventoryRecommendationRelationship.DUPLICATE);
        assertThat(signals.get("merchant.example:sweater").relationship())
                .isEqualTo(UserInventoryRecommendationRelationship.COMPLEMENT);
        assertThat(signals.get("merchant.example:hub").relationship())
                .isEqualTo(UserInventoryRecommendationRelationship.NONE);
    }

    private CreateUserInventoryItemCommand manualItem(
            String name,
            UserInventoryCategory category,
            boolean restockEnabled
    ) {
        return new CreateUserInventoryItemCommand(
                USER_ID,
                UserInventorySource.MANUAL,
                null,
                null,
                name,
                null,
                category,
                null,
                null,
                null,
                null,
                1,
                null,
                null,
                null,
                List.of(),
                category == UserInventoryCategory.PANTRY,
                restockEnabled,
                restockEnabled ? 1 : null,
                null
        );
    }

    private UpsertUserCommand upsertCommand() {
        return new UpsertUserCommand(USER_ID, "inventory@example.com", "Inventory", "User");
    }

    private UserProductSearchProductSnapshot snapshot(String productKey, String title) {
        MerchantSemanticProductResult product = new MerchantSemanticProductResult(
                UUID.randomUUID(),
                "merchant.example",
                "Merchant",
                "https://merchant.example/mcp",
                1,
                0.9d,
                0.9d,
                productKey.substring(productKey.indexOf(':') + 1),
                title,
                title,
                "https://merchant.example/products/" + title.replace(" ", "-"),
                null,
                1000L,
                1000L,
                "USD",
                true,
                null,
                title,
                null,
                List.of(),
                List.of(),
                "10.00",
                "10.00",
                "USD",
                1,
                false,
                List.of(),
                "variant-1",
                "Default",
                List.of(),
                "10.00",
                "USD",
                null,
                null,
                true,
                1,
                0.9d,
                1
        );
        return new UserProductSearchProductSnapshot(productKey, "hash-" + title, product);
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

    static class FakePhotoRecognitionService extends UserInventoryPhotoRecognitionService {

        private Optional<UserInventoryPhotoRecognitionResult> nextResult = Optional.empty();

        FakePhotoRecognitionService() {
            super(null, openRouterProperties(), new ObjectMapper());
        }

        @Override
        public Optional<UserInventoryPhotoRecognitionResult> recognize(CreateUserInventoryPhotoItemCommand command) {
            return nextResult;
        }

        private static OpenRouterProperties openRouterProperties() {
            return new OpenRouterProperties(
                    "https://openrouter.test/api/v1",
                    "",
                    "Meant",
                    new OpenRouterProperties.Models("test", "test", "test", "test")
            );
        }
    }

    static class FakeUserInventoryItemRepository {

        private final List<UserInventoryItem> items = new ArrayList<>();

        UserInventoryItemRepository proxy() {
            return (UserInventoryItemRepository) Proxy.newProxyInstance(
                    UserInventoryItemRepository.class.getClassLoader(),
                    new Class<?>[]{UserInventoryItemRepository.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "save" -> save((UserInventoryItem) args[0]);
                        case "findByUserIdOrderByUpdatedAtDesc" -> byUser((UUID) args[0]);
                        case "findByUserIdAndCategoryOrderByUpdatedAtDesc" ->
                                byUser((UUID) args[0]).stream()
                                        .filter(item -> item.getCategory() == args[1])
                                        .toList();
                        case "findByUserIdAndRestockEnabledTrueOrderByUpdatedAtDesc" ->
                                byUser((UUID) args[0]).stream()
                                        .filter(UserInventoryItem::isRestockEnabled)
                                        .toList();
                        case "findByUserIdAndCategoryAndRestockEnabledTrueOrderByUpdatedAtDesc" ->
                                byUser((UUID) args[0]).stream()
                                        .filter(item -> item.getCategory() == args[1])
                                        .filter(UserInventoryItem::isRestockEnabled)
                                        .toList();
                        case "findByIdAndUserId" ->
                                items.stream()
                                        .filter(item -> item.getId().equals(args[0]) && item.getUserId().equals(args[1]))
                                        .findFirst();
                        case "findByUserIdAndSourceAndSourceProductKey" ->
                                items.stream()
                                        .filter(item -> item.getUserId().equals(args[0]))
                                        .filter(item -> item.getSource() == args[1])
                                        .filter(item -> item.getSourceProductKey() != null && item.getSourceProductKey().equals(args[2]))
                                        .findFirst();
                        case "deleteByIdAndUserId" -> deleteByIdAndUserId((UUID) args[0], (UUID) args[1]);
                        default -> throw new UnsupportedOperationException(method.getName());
                    }
            );
        }

        private UserInventoryItem save(UserInventoryItem item) {
            items.removeIf(existing -> existing.getId().equals(item.getId()));
            items.add(item);
            return item;
        }

        private List<UserInventoryItem> byUser(UUID userId) {
            return items.stream()
                    .filter(item -> item.getUserId().equals(userId))
                    .sorted(Comparator.comparing(UserInventoryItem::getUpdatedAt).reversed())
                    .toList();
        }

        private long deleteByIdAndUserId(UUID id, UUID userId) {
            boolean removed = items.removeIf(item -> item.getId().equals(id) && item.getUserId().equals(userId));
            return removed ? 1L : 0L;
        }
    }
}
