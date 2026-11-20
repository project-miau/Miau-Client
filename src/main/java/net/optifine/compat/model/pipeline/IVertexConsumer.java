package net.optifine.compat.model.pipeline;

import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.util.EnumFacing;

public interface IVertexConsumer {
    VertexFormat getVertexFormat();

    void setQuadTint(int tintIndex);

    void setQuadOrientation(EnumFacing orientation);

    void setQuadColored();

    void put(int element, float... data);
}
