package idle.miau.patcher.culling;

import idle.miau.module.impl.other.Patcher;
import net.minecraft.client.particle.EntityFX;
import net.minecraft.client.renderer.culling.ICamera;

public class ParticleCulling {
    public static ICamera camera;

    public static boolean shouldRender(EntityFX entityFX) {
        if (!Patcher.isParticleCullingActive()) {
            return true;
        }
        if (camera == null || entityFX == null) {
            return true;
        }
        return camera.isBoundingBoxInFrustum(entityFX.getEntityBoundingBox());
    }
}
