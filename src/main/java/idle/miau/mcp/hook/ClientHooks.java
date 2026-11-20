package idle.miau.mcp.hook;

import net.minecraft.client.resources.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;

public final class ClientHooks {
    private ClientHooks() {
    }

    public static IBakedModel handleCameraTransforms(
            IBakedModel model, ItemCameraTransforms.TransformType transformType) {
        return model;
    }
}
