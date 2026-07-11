package com.meant.api.module.order.service;

import static com.meant.api.common.util.CollectionUtils.safeNonNullList;

import com.meant.api.module.merchant.service.dto.MerchantOrderSourceResult;
import com.meant.api.module.order.constant.OrderState;
import com.meant.api.module.order.entity.MerchantOrder;
import com.meant.api.module.order.entity.MerchantOrderLine;
import com.meant.api.module.order.exception.OrderException;
import com.meant.api.module.order.repository.MerchantOrderRepository;
import com.meant.api.plugin.order.common.dto.UcpOrderResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class OrderPersistenceService {

    private final MerchantOrderRepository orderRepository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public Slice<MerchantOrder> listOrders(UUID userId, Pageable pageable) {
        return orderRepository.findByUserIdOrderByPlacedAtDescCreatedAtDesc(userId, pageable);
    }

    @Transactional(readOnly = true)
    public MerchantOrder findOrder(UUID orderId, UUID userId) {
        MerchantOrder order = orderRepository.findByIdAndUserId(orderId, userId)
                .orElseThrow(() -> OrderException.notFound("Order not found: " + orderId));
        Hibernate.initialize(order.getLines());
        return order;
    }

    @Transactional
    public MerchantOrder saveSnapshot(
            UUID userId,
            MerchantOrderSourceResult merchant,
            String endpoint,
            UcpOrderResponse.Order remoteOrder,
            String rawOrderResponse,
            String webhookId,
            String webhookTopic
    ) {
        Instant now = Instant.now();
        String remoteOrderId = required(firstText(remoteOrder.id(), remoteOrder.name(), remoteOrder.orderNumber()),
                "Remote order id is required");
        String remoteOrderIdHash = hash(merchant.merchantId() + ":" + remoteOrderId);
        MerchantOrder order = orderRepository.findByMerchantIdAndRemoteOrderIdHash(
                        merchant.merchantId(),
                        remoteOrderIdHash
                )
                .orElseGet(() -> MerchantOrder.builder()
                        .userId(userId)
                        .merchantId(merchant.merchantId())
                        .merchantDomain(merchant.domain())
                        .merchantName(merchant.name())
                        .remoteOrderId(remoteOrderId)
                        .remoteOrderIdHash(remoteOrderIdHash)
                        .state(OrderState.UNKNOWN)
                        .createdAt(now)
                        .build());
        UcpOrderResponse.Money total = remoteOrder.cost() == null ? null : remoteOrder.cost().totalAmount();
        UcpOrderResponse.Money subtotal = remoteOrder.cost() == null ? null : remoteOrder.cost().subtotalAmount();
        String currency = currency(total, subtotal, remoteOrder.currency());
        OrderState state = OrderState.fromRemote(
                remoteOrder.status(),
                remoteOrder.financialStatus(),
                remoteOrder.fulfillmentStatus(),
                remoteOrder.canceledAt(),
                remoteOrder.closedAt()
        );
        order.replaceSnapshot(
                userId,
                merchant.merchantId(),
                merchant.domain(),
                merchant.name(),
                required(endpoint, "Order endpoint is required"),
                remoteOrderId,
                remoteOrderIdHash,
                blankToNull(remoteOrder.name()),
                blankToNull(remoteOrder.orderNumber()),
                state,
                blankToNull(remoteOrder.financialStatus()),
                blankToNull(remoteOrder.fulfillmentStatus()),
                customerEmail(remoteOrder),
                blankToNull(remoteOrder.orderStatusUrl()),
                required(rawOrderResponse, "Raw order response is required"),
                amount(firstValue(total == null ? null : total.amount(), remoteOrder.totalPrice())),
                amount(firstValue(subtotal == null ? null : subtotal.amount(), remoteOrder.subtotalPrice())),
                currency,
                totalQuantity(remoteOrder.lineItems()),
                firstInstant(remoteOrder.processedAt(), remoteOrder.createdAt()),
                remoteOrder.updatedAt(),
                remoteOrder.processedAt(),
                remoteOrder.canceledAt(),
                remoteOrder.closedAt(),
                blankToNull(webhookId),
                blankToNull(webhookTopic),
                now
        );
        order.replaceLines(toOrderLines(remoteOrder.lineItems(), currency, now));
        return orderRepository.save(order);
    }

    private List<MerchantOrderLine> toOrderLines(
            List<UcpOrderResponse.Line> remoteLines,
            String orderCurrency,
            Instant now
    ) {
        List<UcpOrderResponse.Line> lines = safeNonNullList(remoteLines);
        return java.util.stream.IntStream.range(0, lines.size())
                .mapToObj(index -> toOrderLine(lines.get(index), orderCurrency, now, index))
                .toList();
    }

    private MerchantOrderLine toOrderLine(
            UcpOrderResponse.Line line,
            String orderCurrency,
            Instant now,
            int index
    ) {
        UcpOrderResponse.Product product = line.product();
        UcpOrderResponse.Variant variant = line.variant();
        String productId = firstText(line.productId(), product == null ? null : product.id());
        String variantId = firstText(line.variantId(), variant == null ? null : variant.id());
        String productTitle = required(firstText(
                line.title(),
                line.name(),
                product == null ? null : product.title(),
                variant == null ? null : variant.title(),
                "Order item"
        ), "Order line product title is required");
        String currency = firstText(line.currency(), orderCurrency);
        return MerchantOrderLine.builder()
                .remoteOrderLineId(required(firstText(line.id(), variantId, productId, String.valueOf(index)),
                        "Remote order line id is required"))
                .position(index)
                .productId(blankToNull(productId))
                .productTitle(productTitle)
                .productVariantId(blankToNull(variantId))
                .variantTitle(blankToNull(firstText(line.variantTitle(), variant == null ? null : variant.title())))
                .sku(blankToNull(firstText(line.sku(), variant == null ? null : variant.sku())))
                .vendor(blankToNull(firstText(line.vendor(), product == null ? null : product.vendor())))
                .imageUrl(blankToNull(firstText(line.imageUrl(), product == null ? null : product.imageUrl())))
                .productUrl(blankToNull(firstText(line.productUrl(), product == null ? null : product.productUrl())))
                .quantity(line.quantity() == null ? 0 : line.quantity())
                .unitAmount(amount(line.price()))
                .totalAmount(amount(line.totalPrice()))
                .currency(blankToNull(currency))
                .rawLineResponse(toJson(line))
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    private String customerEmail(UcpOrderResponse.Order remoteOrder) {
        return blankToNull(firstText(
                remoteOrder.email(),
                remoteOrder.customer() == null ? null : remoteOrder.customer().email()
        ));
    }

    private Integer totalQuantity(List<UcpOrderResponse.Line> lines) {
        return safeNonNullList(lines).stream()
                .map(UcpOrderResponse.Line::quantity)
                .filter(quantity -> quantity != null)
                .mapToInt(Integer::intValue)
                .sum();
    }

    private String amount(Object value) {
        return value == null ? null : value.toString();
    }

    private Object firstValue(Object first, Object second) {
        return first == null ? second : first;
    }

    private String currency(UcpOrderResponse.Money first, UcpOrderResponse.Money second, String fallback) {
        return firstText(
                first == null ? null : first.currency(),
                second == null ? null : second.currency(),
                fallback
        );
    }

    private Instant firstInstant(Instant first, Instant second) {
        return first == null ? second : first;
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String required(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new OrderException(message);
        }
        return value;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new OrderException("Could not serialize order snapshot", exception);
        }
    }

    private String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new OrderException("SHA-256 hash algorithm is unavailable", exception);
        }
    }
}
