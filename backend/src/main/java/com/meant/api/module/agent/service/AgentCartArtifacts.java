package com.meant.api.module.agent.service;

import com.meant.api.module.agent.constant.AgentArtifactType;
import com.meant.api.module.agent.service.dto.AgentArtifact;
import com.meant.api.module.agent.service.dto.AgentCartResult;
import java.util.ArrayList;
import java.util.List;

/** Builds the durable buyer-visible cart snapshot shared by agent tools and guest imports. */
public final class AgentCartArtifacts {

    private AgentCartArtifacts() {
    }

    public static List<AgentArtifact> from(AgentCartResult result, AgentJsonSupport jsonSupport) {
        List<AgentArtifact> artifacts = new ArrayList<>();
        int ordinal = 1;
        for (AgentCartResult.Cart cart : result.carts()) {
            artifacts.add(new AgentArtifact(
                    AgentArtifactType.CART,
                    ordinal++,
                    "cart:" + cart.cartId(),
                    cart.merchantOrigin() == null ? "Cart" : "Cart at " + cart.merchantOrigin(),
                    null,
                    null,
                    null,
                    cart.cartId(),
                    null,
                    null,
                    jsonSupport.writeArtifact(cart)
            ));
            for (AgentCartResult.Line line : cart.lines()) {
                artifacts.add(new AgentArtifact(
                        AgentArtifactType.CART_LINE,
                        ordinal++,
                        "cart-line:" + line.cartLineId(),
                        line.productTitle(),
                        line.canonicalProductKey(),
                        line.offerKey(),
                        null,
                        cart.cartId(),
                        line.cartLineId(),
                        null,
                        jsonSupport.writeArtifact(line)
                ));
            }
        }
        return List.copyOf(artifacts);
    }
}
