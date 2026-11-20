package idle.miau.lag.queue.node.impl;

import idle.miau.lag.api.LagRequest;
import idle.miau.lag.queue.node.api.AbstractLagNode;
import org.jetbrains.annotations.NotNull;

public final class AddRequestLagNode extends AbstractLagNode {

    private final @NotNull LagRequest request;

    public AddRequestLagNode(@NotNull LagRequest request) {
        this.request = request;
    }

    public @NotNull LagRequest getRequest() {
        return request;
    }

}
