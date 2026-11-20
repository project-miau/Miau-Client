package idle.miau.mcp.accessor;

import net.minecraft.entity.Entity;
import net.minecraft.util.ResourceLocation;

public interface IAccessorEntityRenderer {
    void callSetupCameraTransform(float partialTicks, int pass);

    void callLoadShader(ResourceLocation resourceLocationIn);

    ResourceLocation[] getShaderResourceLocations();

    boolean getUseShader();

    void setUseShader(boolean useShader);

    int getShaderIndex();

    void setShaderIndex(int index);

    void setThirdPersonDistance(float distance);

    Entity getPointedEntity();

    void setPointedEntity(Entity entity);
}
