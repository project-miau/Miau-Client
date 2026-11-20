package idle.miau.mcp.accessor;

import net.minecraft.client.shader.ShaderGroup;

public interface ISaturationRenderer {
    ShaderGroup Miau$getSaturationShader();

    void Miau$setSaturationShader(ShaderGroup shader);
}
