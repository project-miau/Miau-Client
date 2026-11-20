package org.lwjgl.opengl;

public class PixelFormat {

    



    



    private int bpp;
     
    private int alpha;
     
    private int depth;
     
    private int stencil;
    



    private int samples;
    






    private int colorSamples;
     
    private int num_aux_buffers;
     
    private int accum_bpp;
     
    private int accum_alpha;
     
    private boolean stereo;
     
    private boolean floating_point;
    



    private boolean floating_point_packed;
    



    private boolean sRGB;

    



    public PixelFormat() {
        this(0, 8, 0);
    }

    public PixelFormat(int alpha, int depth, int stencil) {
        this(alpha, depth, stencil, 0);
    }

    public PixelFormat(int alpha, int depth, int stencil, int samples) {
        this(0, alpha, depth, stencil, samples);
    }

    public PixelFormat(int bpp, int alpha, int depth, int stencil, int samples) {
        this(bpp, alpha, depth, stencil, samples, 0, 0, 0, false);
    }

    public PixelFormat(int bpp, int alpha, int depth, int stencil, int samples, int num_aux_buffers, int accum_bpp, int accum_alpha, boolean stereo) {
        this(bpp, alpha, depth, stencil, samples, num_aux_buffers, accum_bpp, accum_alpha, stereo, false);
    }

    public PixelFormat(int bpp, int alpha, int depth, int stencil, int samples, int num_aux_buffers, int accum_bpp, int accum_alpha, boolean stereo, boolean floating_point) {
        this.bpp = bpp;
        this.alpha = alpha;
        this.depth = depth;
        this.stencil = stencil;

        this.samples = samples;

        this.num_aux_buffers = num_aux_buffers;

        this.accum_bpp = accum_bpp;
        this.accum_alpha = accum_alpha;

        this.stereo = stereo;

        this.floating_point = floating_point;
        this.floating_point_packed = false;
        this.sRGB = false;
    }

    private PixelFormat(final PixelFormat pf) {
        this.bpp = pf.bpp;
        this.alpha = pf.alpha;
        this.depth = pf.depth;
        this.stencil = pf.stencil;

        this.samples = pf.samples;
        this.colorSamples = pf.colorSamples;

        this.num_aux_buffers = pf.num_aux_buffers;

        this.accum_bpp = pf.accum_bpp;
        this.accum_alpha = pf.accum_alpha;

        this.stereo = pf.stereo;

        this.floating_point = pf.floating_point;
        this.floating_point_packed = pf.floating_point_packed;
        this.sRGB = pf.sRGB;
    }

    public int getBitsPerPixel() {
        return bpp;
    }

    






    public PixelFormat withBitsPerPixel(final int bpp) {
        if ( bpp < 0 )
            throw new IllegalArgumentException("Invalid number of bits per pixel specified: " + bpp);

        final PixelFormat pf = new PixelFormat(this);
        pf.bpp = bpp;
        return pf;
    }

    public int getAlphaBits() {
        return alpha;
    }

    






    public PixelFormat withAlphaBits(final int alpha) {
        if ( alpha < 0 )
            throw new IllegalArgumentException("Invalid number of alpha bits specified: " + alpha);

        final PixelFormat pf = new PixelFormat(this);
        pf.alpha = alpha;
        return pf;
    }

    public int getDepthBits() {
        return depth;
    }

    






    public PixelFormat withDepthBits(final int depth) {
        if ( depth < 0 )
            throw new IllegalArgumentException("Invalid number of depth bits specified: " + depth);

        final PixelFormat pf = new PixelFormat(this);
        pf.depth = depth;
        return pf;
    }

    public int getStencilBits() {
        return stencil;
    }

    






    public PixelFormat withStencilBits(final int stencil) {
        if ( stencil < 0 )
            throw new IllegalArgumentException("Invalid number of stencil bits specified: " + stencil);

        final PixelFormat pf = new PixelFormat(this);
        pf.stencil = stencil;
        return pf;
    }

    public int getSamples() {
        return samples;
    }

    






    public PixelFormat withSamples(final int samples) {
        if ( samples < 0 )
            throw new IllegalArgumentException("Invalid number of samples specified: " + samples);

        final PixelFormat pf = new PixelFormat(this);
        pf.samples = samples;
        return pf;
    }

    








    public PixelFormat withCoverageSamples(final int colorSamples) {
        return withCoverageSamples(colorSamples, samples);
    }

    








    public PixelFormat withCoverageSamples(final int colorSamples, final int coverageSamples) {
        if ( coverageSamples < 0 || colorSamples < 0 || (coverageSamples == 0 && 0 < colorSamples) || coverageSamples < colorSamples  )
            throw new IllegalArgumentException("Invalid number of coverage samples specified: " + coverageSamples + " - " + colorSamples);

        final PixelFormat pf = new PixelFormat(this);
        pf.samples = coverageSamples;
        pf.colorSamples = colorSamples;
        return pf;
    }

    public int getAuxBuffers() {
        return num_aux_buffers;
    }

    






    public PixelFormat withAuxBuffers(final int num_aux_buffers) {
        if ( num_aux_buffers < 0 )
            throw new IllegalArgumentException("Invalid number of auxiliary buffers specified: " + num_aux_buffers);

        final PixelFormat pf = new PixelFormat(this);
        pf.num_aux_buffers = num_aux_buffers;
        return pf;
    }

    public int getAccumulationBitsPerPixel() {
        return accum_bpp;
    }

    






    public PixelFormat withAccumulationBitsPerPixel(final int accum_bpp) {
        if ( accum_bpp < 0 )
            throw new IllegalArgumentException("Invalid number of bits per pixel in the accumulation buffer specified: " + accum_bpp);

        final PixelFormat pf = new PixelFormat(this);
        pf.accum_bpp = accum_bpp;
        return pf;
    }

    public int getAccumulationAlpha() {
        return accum_alpha;
    }

    






    public PixelFormat withAccumulationAlpha(final int accum_alpha) {
        if ( accum_alpha < 0 )
            throw new IllegalArgumentException("Invalid number of alpha bits in the accumulation buffer specified: " + accum_alpha);

        final PixelFormat pf = new PixelFormat(this);
        pf.accum_alpha = accum_alpha;
        return pf;
    }

    public boolean isStereo() {
        return stereo;
    }

    






    public PixelFormat withStereo(final boolean stereo) {
        final PixelFormat pf = new PixelFormat(this);
        pf.stereo = stereo;
        return pf;
    }

    public boolean isFloatingPoint() {
        return floating_point;
    }

    







    public PixelFormat withFloatingPoint(final boolean floating_point) {
        final PixelFormat pf = new PixelFormat(this);
        pf.floating_point = floating_point;
        if ( floating_point )
            pf.floating_point_packed = false;
        return pf;
    }

    







    public PixelFormat withFloatingPointPacked(final boolean floating_point_packed) {
        final PixelFormat pf = new PixelFormat(this);
        pf.floating_point_packed = floating_point_packed;
        if ( floating_point_packed )
            pf.floating_point = false;
        return pf;
    }

    public boolean isSRGB() {
        return sRGB;
    }

    






    public PixelFormat withSRGB(final boolean sRGB) {
        final PixelFormat pf = new PixelFormat(this);
        pf.sRGB = sRGB;
        return pf;
    }

}
