package com.meant.api.plugin.support;

import com.meant.api.plugin.spi.NegotiatedCapabilities;
import java.time.Instant;

public class UcpSession {

    private NegotiatedCapabilities activeCapabilities;
    private String cartId;
    private Instant cartExpiresAt;
    private String checkoutId;
    private Instant checkoutExpiresAt;
    private String continueUrl;

    private UcpSession(
            String cartId,
            Instant cartExpiresAt,
            String continueUrl,
            NegotiatedCapabilities activeCapabilities
    ) {
        this.cartId = cartId;
        this.cartExpiresAt = cartExpiresAt;
        this.continueUrl = continueUrl;
        this.activeCapabilities = activeCapabilities == null ? NegotiatedCapabilities.none() : activeCapabilities;
    }

    public static UcpSession start() {
        return new UcpSession(null, null, null, NegotiatedCapabilities.none());
    }

    public static UcpSession cart(String cartId, Instant cartExpiresAt, String continueUrl) {
        return new UcpSession(cartId, cartExpiresAt, continueUrl, NegotiatedCapabilities.none());
    }

    public NegotiatedCapabilities activeCapabilities() {
        return activeCapabilities;
    }

    public String cartId() {
        return cartId;
    }

    public Instant cartExpiresAt() {
        return cartExpiresAt;
    }

    public String checkoutId() {
        return checkoutId;
    }

    public Instant checkoutExpiresAt() {
        return checkoutExpiresAt;
    }

    public String continueUrl() {
        return continueUrl;
    }

    public void acceptNegotiatedCapabilities(NegotiatedCapabilities negotiatedCapabilities) {
        if (negotiatedCapabilities != null) {
            this.activeCapabilities = negotiatedCapabilities;
        }
    }

    public void updateCartState(String cartId, Instant cartExpiresAt, String continueUrl) {
        if (cartId != null && !cartId.isBlank()) {
            this.cartId = cartId;
        }
        this.cartExpiresAt = cartExpiresAt;
        this.continueUrl = continueUrl;
    }

    public void updateCheckoutState(String checkoutId, Instant checkoutExpiresAt, String continueUrl) {
        if (checkoutId != null && !checkoutId.isBlank()) {
            this.checkoutId = checkoutId;
        }
        this.checkoutExpiresAt = checkoutExpiresAt;
        this.continueUrl = continueUrl;
    }

    public void clearCartState() {
        this.cartId = null;
        this.cartExpiresAt = null;
        this.checkoutId = null;
        this.checkoutExpiresAt = null;
        this.continueUrl = null;
    }
}
