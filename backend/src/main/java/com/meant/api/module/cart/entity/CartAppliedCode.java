package com.meant.api.module.cart.entity;

import com.meant.api.module.cart.constant.CartAppliedCodeType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Table(name = "cart_applied_code")
public class CartAppliedCode {

    @Id
    @Builder.Default
    private UUID id = UUID.randomUUID();

    @ManyToOne
    @JoinColumn(nullable = false)
    private Cart cart;

    @Enumerated(EnumType.STRING)
    private CartAppliedCodeType type;

    private String code;

    private String label;

    private Boolean applicable;

    private String amount;

    private String currency;

    private Integer displayOrder;

    public void assignCart(Cart cart) {
        this.cart = cart;
    }
}
