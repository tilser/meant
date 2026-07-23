package com.meant.api.module.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.meant.api.module.merchant.service.dto.MerchantSemanticProductResult;
import com.meant.api.module.user.constant.UserInventoryCategory;
import com.meant.api.module.user.constant.UserInventoryRecommendationRelationship;
import com.meant.api.module.user.constant.UserInventorySource;
import com.meant.api.module.user.entity.User;
import com.meant.api.module.user.entity.UserInventoryItem;
import com.meant.api.module.user.exception.UserException;
import com.meant.api.module.user.properties.UserCollectionProperties;
import com.meant.api.module.user.repository.UserInventoryItemRepository;
import com.meant.api.module.user.service.command.CreateUserInventoryItemCommand;
import com.meant.api.module.user.service.command.DeleteUserInventoryItemCommand;
import com.meant.api.module.user.service.command.ImportPurchasedInventoryItemsCommand;
import com.meant.api.module.user.service.command.UpdateUserInventoryItemCommand;
import com.meant.api.module.user.service.command.EnsureUserProfileCommand;
import com.meant.api.module.user.service.dto.UserInventoryItemResult;
import com.meant.api.module.user.service.dto.UserInventoryCommerceReference;
import com.meant.api.module.user.service.dto.UserInventoryRecommendationSignal;
import com.meant.api.module.user.service.dto.UserInventorySelectedOption;
import com.meant.api.module.user.service.dto.UserInventoryProfileSummary;
import com.meant.api.module.user.service.dto.UserProductSearchProductSnapshot;
import com.meant.api.module.user.service.query.ExportUserInventoryQuery;
import com.meant.api.module.user.service.query.ListUserInventoryItemsQuery;
import java.lang.reflect.Proxy;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import tools.jackson.databind.ObjectMapper;

class UserInventoryServiceTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000015");
    private static final UUID ATTEMPT_1 = UUID.fromString("00000000-0000-0000-0000-000000000101");
    private static final UUID ATTEMPT_2 = UUID.fromString("00000000-0000-0000-0000-000000000102");
    private static final Instant NOW = Instant.parse("2026-06-18T10:00:00Z");

    private FakeUserInventoryItemRepository repository;
    private FakeUserService userService;
    private UserInventoryService service;

    @BeforeEach
    void setUp() {
        repository = new FakeUserInventoryItemRepository();
        userService = new FakeUserService();
        service = new UserInventoryService(
                userService,
                repository.proxy(),
                collectionProperties(50),
                new ObjectMapper()
        );
    }

    @Test
    void createListsExportsAndDeletesUploadedInventoryItems() {
        UserInventoryItemResult item = service.create(profileCommand(), new CreateUserInventoryItemCommand(
                USER_ID,
                USER_ID + "/cotton-tee.webp",
                "Heavyweight Organic Cotton Tee",
                "Field Loom",
                UserInventoryCategory.APPAREL,
                "White crewneck tee",
                "https://example.test/tee",
                2,
                "pcs",
                "Wardrobe",
                "Worn weekly",
                List.of("organic cotton", "white"),
                false,
                false,
                null,
                LocalDate.parse("2025-04-12"),
                "M",
                "White",
                "Organic cotton"
        ));

        assertThat(item.source()).isEqualTo(UserInventorySource.PHOTO);
        assertThat(item.category()).isEqualTo(UserInventoryCategory.APPAREL);
        assertThat(item.quantity()).isEqualTo(2);
        assertThat(item.photoPath()).isEqualTo(USER_ID + "/cotton-tee.webp");
        assertThat(item.purchasedOn()).isEqualTo(LocalDate.parse("2025-04-12"));
        assertThat(item.size()).isEqualTo("M");
        assertThat(item.color()).isEqualTo("White");
        assertThat(item.material()).isEqualTo("Organic cotton");
        assertThat(item.imageUrl()).isNull();
        assertThat(item.photoUrl()).isNull();
        assertThat(item.commerceReference()).isNull();
        assertThat(item.sourceCheckoutAttemptId()).isNull();

        List<UserInventoryItemResult> listed = service.list(
                profileCommand(),
                listQuery(UserInventoryCategory.APPAREL, false)
        );
        assertThat(listed).singleElement()
                .extracting(UserInventoryItemResult::name)
                .isEqualTo("Heavyweight Organic Cotton Tee");

        assertThat(service.export(profileCommand(), new ExportUserInventoryQuery(USER_ID)).items())
                .hasSize(1);

        service.delete(profileCommand(), new DeleteUserInventoryItemCommand(USER_ID, item.id()));

        assertThat(service.list(profileCommand(), listQuery(null, false)))
                .isEmpty();
    }

    @Test
    void createRejectsUnownedAndUnsupportedPhotoPaths() {
        CreateUserInventoryItemCommand unowned = uploadedItem(
                "Blue Linen Shirt", UserInventoryCategory.APPAREL, false, UUID.randomUUID() + "/shirt.jpg");
        CreateUserInventoryItemCommand unsupported = uploadedItem(
                "Blue Linen Shirt", UserInventoryCategory.APPAREL, false, USER_ID + "/shirt.heic");

        assertThatThrownBy(() -> service.create(profileCommand(), unowned))
                .isInstanceOf(UserException.class)
                .hasMessageContaining("Inventory photo path");
        assertThatThrownBy(() -> service.create(profileCommand(), unsupported))
                .isInstanceOf(UserException.class)
                .hasMessageContaining("Inventory photo path");
    }

    @Test
    void updateCanClearOptionalTextFields() {
        UserInventoryItemResult created = service.create(profileCommand(), new CreateUserInventoryItemCommand(
                USER_ID,
                USER_ID + "/brewer.jpg",
                "Countertop Coffee Brewer",
                "Brew Works",
                UserInventoryCategory.HOME,
                "Daily coffee setup",
                "https://example.test/brewer",
                1,
                "piece",
                "Kitchen",
                "Filter size 02",
                List.of("glass carafe"),
                false,
                false,
                null,
                LocalDate.parse("2024-11-03"),
                "1.2 L",
                "Black",
                "Glass"
        ));

        UserInventoryItemResult updated = service.update(profileCommand(), new UpdateUserInventoryItemCommand(
                USER_ID,
                created.id(),
                USER_ID + "/brewer-replacement.png",
                " ",
                " ",
                null,
                " ",
                " ",
                null,
                " ",
                " ",
                " ",
                null,
                null,
                null,
                null,
                "",
                " ",
                " ",
                " "
        ));

        assertThat(updated.name()).isEqualTo("Countertop Coffee Brewer");
        assertThat(updated.category()).isEqualTo(UserInventoryCategory.HOME);
        assertThat(updated.quantity()).isEqualTo(1);
        assertThat(updated.brand()).isNull();
        assertThat(updated.description()).isNull();
        assertThat(updated.imageUrl()).isNull();
        assertThat(updated.productUrl()).isNull();
        assertThat(updated.photoUrl()).isNull();
        assertThat(updated.photoPath()).isEqualTo(USER_ID + "/brewer-replacement.png");
        assertThat(updated.unit()).isNull();
        assertThat(updated.location()).isNull();
        assertThat(updated.notes()).isNull();
        assertThat(updated.purchasedOn()).isNull();
        assertThat(updated.size()).isNull();
        assertThat(updated.color()).isNull();
        assertThat(updated.material()).isNull();
    }

    @Test
    void photoReplacementDropsLegacyPayloadAndPreservesDistinctCatalogImage() {
        String legacyPayload = "data:image/jpeg;base64,abc";
        UserInventoryItem legacyPhoto = repository.save(UserInventoryItem.create(
                USER_ID,
                UserInventoryItem.Snapshot.builder()
                        .source(UserInventorySource.PHOTO)
                        .name("Legacy photo item")
                        .category(UserInventoryCategory.OTHER)
                        .imageUrl(legacyPayload)
                        .photoUrl(legacyPayload)
                        .quantity(1)
                        .attributes("[]")
                        .build(),
                NOW));
        UserInventoryItem checkoutItem = repository.save(UserInventoryItem.create(
                USER_ID,
                UserInventoryItem.Snapshot.builder()
                        .source(UserInventorySource.MEANT_PURCHASE)
                        .name("Purchased item")
                        .category(UserInventoryCategory.OTHER)
                        .imageUrl("https://merchant.example/item.jpg")
                        .photoUrl(legacyPayload)
                        .quantity(1)
                        .attributes("[]")
                        .build(),
                NOW));

        UserInventoryItemResult updatedLegacy = service.update(
                profileCommand(), replacePhoto(legacyPhoto.getId(), USER_ID + "/legacy-replacement.jpg"));
        UserInventoryItemResult updatedCheckout = service.update(
                profileCommand(), replacePhoto(checkoutItem.getId(), USER_ID + "/checkout-photo.webp"));

        assertThat(updatedLegacy.photoUrl()).isNull();
        assertThat(updatedLegacy.imageUrl()).isNull();
        assertThat(updatedCheckout.photoUrl()).isNull();
        assertThat(updatedCheckout.imageUrl()).isEqualTo("https://merchant.example/item.jpg");
    }

    @Test
    void listCanFilterRestockEnabledPantryItems() {
        service.create(profileCommand(), uploadedItem("Olive Oil", UserInventoryCategory.PANTRY, true));
        service.create(profileCommand(), uploadedItem("Merino Sweater", UserInventoryCategory.APPAREL, false));

        List<UserInventoryItemResult> restocks = service.list(
                profileCommand(),
                listQuery(null, true)
        );

        assertThat(restocks).singleElement()
                .extracting(UserInventoryItemResult::name)
                .isEqualTo("Olive Oil");
    }

    @Test
    void importPurchasedItemsDoesNotDuplicateSameLatestCheckoutAttempt() {
        service.importPurchasedItems(purchaseCommand(
                ATTEMPT_1,
                NOW,
                purchasedItem("variant-1", "Cold-Pressed Extra Virgin Olive Oil", "Casa Verde", 1, null)
        ));
        service.importPurchasedItems(purchaseCommand(
                ATTEMPT_1,
                NOW.plusSeconds(60),
                purchasedItem("variant-1", "Cold-Pressed Extra Virgin Olive Oil", "Casa Verde", 2, null)
        ));

        List<UserInventoryItemResult> items = service.list(
                profileCommand(),
                listQuery(null, false)
        );

        assertThat(items).singleElement()
                .satisfies(result -> {
                    assertThat(result.source()).isEqualTo(UserInventorySource.MEANT_PURCHASE);
                    assertThat(result.sourceProductKey()).isEqualTo("merchant.example:variant-1");
                    assertThat(result.category()).isEqualTo(UserInventoryCategory.PANTRY);
                    assertThat(result.quantity()).isEqualTo(1);
                    assertThat(result.purchasedAt()).isEqualTo(NOW);
                    assertThat(result.sourceCheckoutAttemptId()).isEqualTo(ATTEMPT_1);
                });
    }

    @Test
    void importPurchasedItemsAccumulatesForDistinctCheckoutAttempt() {
        service.importPurchasedItems(purchaseCommand(
                ATTEMPT_1,
                NOW,
                purchasedItem("variant-1", "Cold-Pressed Extra Virgin Olive Oil", "Casa Verde", 1, null),
                purchasedItem("variant-2", "Merino Crew Sweater", "Northbound", 1, null)
        ));
        service.importPurchasedItems(purchaseCommand(
                ATTEMPT_2,
                NOW.plusSeconds(60),
                purchasedItem("variant-1", "Cold-Pressed Extra Virgin Olive Oil", "Casa Verde", 2, null)
        ));

        List<UserInventoryItemResult> items = service.list(
                profileCommand(),
                listQuery(null, false)
        );

        assertThat(items).anySatisfy(result -> {
            assertThat(result.sourceProductKey()).isEqualTo("merchant.example:variant-1");
            assertThat(result.quantity()).isEqualTo(3);
            assertThat(result.purchasedAt()).isEqualTo(NOW.plusSeconds(60));
            assertThat(result.sourceCheckoutAttemptId()).isEqualTo(ATTEMPT_2);
        });
        assertThat(items).anySatisfy(result -> {
            assertThat(result.sourceProductKey()).isEqualTo("merchant.example:variant-2");
            assertThat(result.quantity()).isEqualTo(1);
        });
    }

    @Test
    void importPurchasedItemsBatchLoadsExistingKeysAndSavesChangedItemsTogether() {
        service.importPurchasedItems(purchaseCommand(
                ATTEMPT_1,
                NOW,
                purchasedItem("variant-1", "Cold-Pressed Extra Virgin Olive Oil", "Casa Verde", 1, null)
        ));
        repository.resetCounters();

        service.importPurchasedItems(purchaseCommand(
                ATTEMPT_2,
                NOW.plusSeconds(60),
                purchasedItem("variant-1", "Cold-Pressed Extra Virgin Olive Oil", "Casa Verde", 2, null),
                purchasedItem("variant-2", "Merino Crew Sweater", "Northbound", 1, null)
        ));

        assertThat(repository.batchSourceProductLookupCount).isEqualTo(1);
        assertThat(repository.singleSourceProductLookupCount).isZero();
        assertThat(repository.saveAllCount).isEqualTo(1);
        assertThat(repository.savedInLastSaveAll).isEqualTo(2);
        assertThat(service.list(profileCommand(), listQuery(null, false)))
                .anySatisfy(result -> {
                    assertThat(result.sourceProductKey()).isEqualTo("merchant.example:variant-1");
                    assertThat(result.quantity()).isEqualTo(3);
                    assertThat(result.purchasedAt()).isEqualTo(NOW.plusSeconds(60));
                })
                .anySatisfy(result -> {
                    assertThat(result.sourceProductKey()).isEqualTo("merchant.example:variant-2");
                    assertThat(result.quantity()).isEqualTo(1);
                });
    }

    @Test
    void importPurchasedItemsRetainsTypedCommerceIdentityAndSelectedOptions() {
        UUID integrationId = UUID.randomUUID();
        UserInventoryCommerceReference reference = new UserInventoryCommerceReference(
                "shopify",
                integrationId,
                "merchant-1",
                "Shop.Example.",
                "canonical-shoe",
                "offer-shoe-42",
                "PROVIDER_CATALOG",
                "shopify-global",
                "product-1",
                "variant-size-42",
                List.of(new UserInventorySelectedOption("variant-option", "Size", "42"))
        );

        service.importPurchasedItems(purchaseCommand(
                ATTEMPT_1,
                NOW,
                purchasedItem("variant-size-42", "Trail Shoe", "Northbound", 1, reference)
        ));

        UserInventoryItemResult imported = service.list(profileCommand(), listQuery(null, false)).getFirst();
        UserInventoryItemResult updated = service.update(profileCommand(), new UpdateUserInventoryItemCommand(
                USER_ID,
                imported.id(),
                null,
                "Trail Shoe (worn)",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        ));

        assertThat(updated.sourceCheckoutAttemptId()).isEqualTo(ATTEMPT_1);
        assertThat(updated.commerceReference()).isNotNull();
        assertThat(updated.commerceReference().provider()).isEqualTo("SHOPIFY");
        assertThat(updated.commerceReference().merchantIntegrationId()).isEqualTo(integrationId);
        assertThat(updated.commerceReference().externalMerchantDomain()).isEqualTo("shop.example");
        assertThat(updated.commerceReference().canonicalProductKey()).isEqualTo("canonical-shoe");
        assertThat(updated.commerceReference().externalVariantId()).isEqualTo("variant-size-42");
        assertThat(updated.commerceReference().selectedOptions())
                .containsExactly(new UserInventorySelectedOption("variant-option", "Size", "42"));
    }

    @Test
    void inventoryProfileHashUsesRepositorySignature() {
        assertThat(service.inventoryProfileHash(USER_ID)).isEqualTo("inventory:none");
        assertThat(repository.profileSummaryLookupCount).isEqualTo(1);

        service.create(profileCommand(), uploadedItem("Olive Oil", UserInventoryCategory.PANTRY, true));

        assertThat(service.inventoryProfileHash(USER_ID))
                .startsWith("inventory:1:")
                .isNotEqualTo("inventory:none");
        assertThat(repository.profileSummaryLookupCount).isEqualTo(2);
    }

    @Test
    void listAppliesPageAndLimitAtRepositoryBoundary() {
        service.create(profileCommand(), uploadedItem("Olive Oil", UserInventoryCategory.PANTRY, true));
        service.create(profileCommand(), uploadedItem("Merino Sweater", UserInventoryCategory.APPAREL, false));
        service.create(profileCommand(), uploadedItem("Countertop Brewer", UserInventoryCategory.HOME, false));

        List<UserInventoryItemResult> firstPage = service.list(
                profileCommand(),
                new ListUserInventoryItemsQuery(USER_ID, null, false, 0, 2)
        );
        List<UserInventoryItemResult> secondPage = service.list(
                profileCommand(),
                new ListUserInventoryItemsQuery(USER_ID, null, false, 1, 2)
        );

        assertThat(firstPage).hasSize(2);
        assertThat(secondPage).hasSize(1);
    }

    @Test
    void createRejectsNewItemsPastUserQuota() {
        UserInventoryService quotaService = new UserInventoryService(
                userService,
                repository.proxy(),
                collectionProperties(1),
                new ObjectMapper()
        );

        quotaService.create(profileCommand(), uploadedItem("Olive Oil", UserInventoryCategory.PANTRY, true));

        assertThatThrownBy(() -> quotaService.create(
                        profileCommand(),
                        uploadedItem("Merino Sweater", UserInventoryCategory.APPAREL, false)))
                .isInstanceOf(UserException.class)
                .hasMessageContaining("Inventory item quota exceeded");
    }

    @Test
    void recommendationSignalsClassifyRestocksDuplicatesComplementsAndNone() {
        UserInventoryItemResult oil = service.create(profileCommand(), uploadedItem(
                "Cold-Pressed Extra Virgin Olive Oil",
                UserInventoryCategory.PANTRY,
                true
        ));
        service.create(profileCommand(), uploadedItem(
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

    private ImportPurchasedInventoryItemsCommand purchaseCommand(
            UUID checkoutAttemptId,
            Instant purchasedAt,
            ImportPurchasedInventoryItemsCommand.PurchasedItem... items
    ) {
        return new ImportPurchasedInventoryItemsCommand(
                USER_ID, checkoutAttemptId, purchasedAt, List.of(items));
    }

    private ImportPurchasedInventoryItemsCommand.PurchasedItem purchasedItem(
            String variantId,
            String name,
            String brand,
            int quantity,
            UserInventoryCommerceReference commerceReference
    ) {
        return new ImportPurchasedInventoryItemsCommand.PurchasedItem(
                "merchant.example:" + variantId,
                "hash-" + variantId,
                name,
                brand,
                "https://merchant.example/images/" + variantId + ".jpg",
                "https://merchant.example/products/" + variantId,
                quantity,
                commerceReference
        );
    }

    private CreateUserInventoryItemCommand uploadedItem(
            String name,
            UserInventoryCategory category,
            boolean restockEnabled
    ) {
        return uploadedItem(
                name,
                category,
                restockEnabled,
                USER_ID + "/" + name.replaceAll("[^A-Za-z0-9]+", "-") + ".jpg");
    }

    private CreateUserInventoryItemCommand uploadedItem(
            String name,
            UserInventoryCategory category,
            boolean restockEnabled,
            String photoPath
    ) {
        return new CreateUserInventoryItemCommand(
                USER_ID,
                photoPath,
                name,
                null,
                category,
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
                null,
                null,
                null,
                null
        );
    }

    private UpdateUserInventoryItemCommand replacePhoto(UUID itemId, String photoPath) {
        return new UpdateUserInventoryItemCommand(
                USER_ID,
                itemId,
                photoPath,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    private EnsureUserProfileCommand profileCommand() {
        return new EnsureUserProfileCommand(USER_ID, "inventory@example.com", "Inventory", "User");
    }

    private ListUserInventoryItemsQuery listQuery(UserInventoryCategory category, boolean restockOnly) {
        return new ListUserInventoryItemsQuery(USER_ID, category, restockOnly, 0, 50);
    }

    private UserCollectionProperties collectionProperties(int inventoryQuota) {
        return new UserCollectionProperties(
                new UserCollectionProperties.SavedProducts(50, 100, 500, 50),
                new UserCollectionProperties.Inventory(50, 100, inventoryQuota)
        );
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
                null,
                null,
                null,
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
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
        public User ensureProfile(EnsureUserProfileCommand command) {
            return null;
        }
    }

    static class FakeUserInventoryItemRepository {

        private final List<UserInventoryItem> items = new ArrayList<>();
        private int singleSourceProductLookupCount;
        private int batchSourceProductLookupCount;
        private int saveAllCount;
        private int savedInLastSaveAll;
        private int profileSummaryLookupCount;

        UserInventoryItemRepository proxy() {
            return (UserInventoryItemRepository) Proxy.newProxyInstance(
                    UserInventoryItemRepository.class.getClassLoader(),
                    new Class<?>[]{UserInventoryItemRepository.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "save" -> save((UserInventoryItem) args[0]);
                        case "saveAll" -> saveAll((Iterable<UserInventoryItem>) args[0]);
                        case "findByUserIdOrderByUpdatedAtDesc" -> args.length == 1
                                ? byUser((UUID) args[0])
                                : page(byUser((UUID) args[0]), (Pageable) args[1]);
                        case "findByUserIdAndCategoryOrderByUpdatedAtDesc" ->
                                pageIfRequested(byUser((UUID) args[0]).stream()
                                                .filter(item -> item.getCategory() == args[1])
                                                .toList(),
                                        args);
                        case "findByUserIdAndRestockEnabledTrueOrderByUpdatedAtDesc" ->
                                pageIfRequested(byUser((UUID) args[0]).stream()
                                                .filter(UserInventoryItem::isRestockEnabled)
                                                .toList(),
                                        args);
                        case "findByUserIdAndCategoryAndRestockEnabledTrueOrderByUpdatedAtDesc" ->
                                pageIfRequested(byUser((UUID) args[0]).stream()
                                                .filter(item -> item.getCategory() == args[1])
                                                .filter(UserInventoryItem::isRestockEnabled)
                                                .toList(),
                                        args);
                        case "countByUserId" -> (long) byUser((UUID) args[0]).size();
                        case "summarizeProfileByUserId" -> summarizeProfileByUserId((UUID) args[0]);
                        case "findByIdAndUserId" ->
                                items.stream()
                                        .filter(item -> item.getId().equals(args[0]) && item.getUserId().equals(args[1]))
                                        .findFirst();
                        case "findByUserIdAndSourceAndSourceProductKey" ->
                                findByUserIdAndSourceAndSourceProductKey(
                                        (UUID) args[0],
                                        (UserInventorySource) args[1],
                                        (String) args[2]);
                        case "findByUserIdAndSourceAndSourceProductKeyIn" ->
                                findByUserIdAndSourceAndSourceProductKeyIn(
                                        (UUID) args[0],
                                        (UserInventorySource) args[1],
                                        (Collection<String>) args[2]);
                        case "deleteByIdAndUserId" -> deleteByIdAndUserId((UUID) args[0], (UUID) args[1]);
                        default -> throw new UnsupportedOperationException(method.getName());
                    }
            );
        }

        private void resetCounters() {
            singleSourceProductLookupCount = 0;
            batchSourceProductLookupCount = 0;
            saveAllCount = 0;
            savedInLastSaveAll = 0;
        }

        private UserInventoryProfileSummary summarizeProfileByUserId(UUID userId) {
            profileSummaryLookupCount++;
            List<UserInventoryItem> userItems = byUser(userId);
            return new UserInventoryProfileSummary(
                    userItems.size(),
                    userItems.stream()
                            .map(UserInventoryItem::getUpdatedAt)
                            .max(Comparator.naturalOrder())
                            .orElse(null)
            );
        }

        private UserInventoryItem save(UserInventoryItem item) {
            items.removeIf(existing -> existing.getId().equals(item.getId()));
            items.add(item);
            return item;
        }

        private List<UserInventoryItem> saveAll(Iterable<UserInventoryItem> nextItems) {
            List<UserInventoryItem> saved = new ArrayList<>();
            nextItems.forEach(item -> saved.add(save(item)));
            saveAllCount++;
            savedInLastSaveAll = saved.size();
            return saved;
        }

        private Optional<UserInventoryItem> findByUserIdAndSourceAndSourceProductKey(
                UUID userId,
                UserInventorySource source,
                String sourceProductKey
        ) {
            singleSourceProductLookupCount++;
            return items.stream()
                    .filter(item -> item.getUserId().equals(userId))
                    .filter(item -> item.getSource() == source)
                    .filter(item -> item.getSourceProductKey() != null
                            && item.getSourceProductKey().equals(sourceProductKey))
                    .findFirst();
        }

        private List<UserInventoryItem> findByUserIdAndSourceAndSourceProductKeyIn(
                UUID userId,
                UserInventorySource source,
                Collection<String> sourceProductKeys
        ) {
            batchSourceProductLookupCount++;
            Set<String> requestedKeys = new LinkedHashSet<>(sourceProductKeys);
            return items.stream()
                    .filter(item -> item.getUserId().equals(userId))
                    .filter(item -> item.getSource() == source)
                    .filter(item -> requestedKeys.contains(item.getSourceProductKey()))
                    .toList();
        }

        private List<UserInventoryItem> byUser(UUID userId) {
            return items.stream()
                    .filter(item -> item.getUserId().equals(userId))
                    .sorted(Comparator.comparing(UserInventoryItem::getUpdatedAt).reversed())
                    .toList();
        }

        private List<UserInventoryItem> pageIfRequested(List<UserInventoryItem> source, Object[] args) {
            return args.length > 2 && args[2] instanceof Pageable pageable ? page(source, pageable) : source;
        }

        private List<UserInventoryItem> page(List<UserInventoryItem> source, Pageable pageable) {
            int start = (int) pageable.getOffset();
            if (start >= source.size()) {
                return List.of();
            }
            int end = Math.min(start + pageable.getPageSize(), source.size());
            return source.subList(start, end);
        }

        private long deleteByIdAndUserId(UUID id, UUID userId) {
            boolean removed = items.removeIf(item -> item.getId().equals(id) && item.getUserId().equals(userId));
            return removed ? 1L : 0L;
        }
    }
}
