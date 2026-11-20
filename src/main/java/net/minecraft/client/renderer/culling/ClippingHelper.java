package net.minecraft.client.renderer.culling;

public class ClippingHelper
{
    public float[][] frustum = new float[6][4];
    public float[] projectionMatrix = new float[16];
    public float[] modelviewMatrix = new float[16];
    public float[] clippingMatrix = new float[16];
    public boolean disabled = false;

    private float dot(float[] p_dot_1_, float p_dot_2_, float p_dot_3_, float p_dot_4_)
    {
        return p_dot_1_[0] * p_dot_2_ + p_dot_1_[1] * p_dot_3_ + p_dot_1_[2] * p_dot_4_ + p_dot_1_[3];
    }

    public boolean isBoxInFrustum(double minX, double minY, double minZ, double maxX, double maxY, double maxZ)
    {
        if (this.disabled)
        {
            return true;
        }

        float x0 = (float)minX;
        float y0 = (float)minY;
        float z0 = (float)minZ;
        float x1 = (float)maxX;
        float y1 = (float)maxY;
        float z1 = (float)maxZ;

        for (int i = 0; i < 6; ++i)
        {
            float[] plane = this.frustum[i];
            float a = plane[0];
            float b = plane[1];
            float c = plane[2];
            if (a * (a < 0.0F ? x0 : x1)
                    + b * (b < 0.0F ? y0 : y1)
                    + c * (c < 0.0F ? z0 : z1)
                    + plane[3] <= 0.0F)
            {
                return false;
            }
        }

        return true;
    }

    public boolean isBoxInFrustumFully(double minX, double minY, double minZ, double maxX, double maxY, double maxZ)
    {
        if (this.disabled)
        {
            return true;
        }

        float x0 = (float)minX;
        float y0 = (float)minY;
        float z0 = (float)minZ;
        float x1 = (float)maxX;
        float y1 = (float)maxY;
        float z1 = (float)maxZ;

        for (int i = 0; i < 6; ++i)
        {
            float[] plane = this.frustum[i];
            float a = plane[0];
            float b = plane[1];
            float c = plane[2];
            float x = i < 4 ? (a < 0.0F ? x1 : x0) : (a < 0.0F ? x0 : x1);
            float y = i < 4 ? (b < 0.0F ? y1 : y0) : (b < 0.0F ? y0 : y1);
            float z = i < 4 ? (c < 0.0F ? z1 : z0) : (c < 0.0F ? z0 : z1);
            if (a * x + b * y + c * z + plane[3] <= 0.0F)
            {
                return false;
            }
        }

        return true;
    }

}
