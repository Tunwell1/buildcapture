package com.tunwell.buildcapture.client;

import com.mojang.blaze3d.vertex.IVertexBuilder;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Wraps the normal block vertex pipeline for the placement ghost: every vertex
 * gets a fixed translucency and full block/sky light, so the preview is easy to
 * tell apart from real blocks and does not darken in caves.
 */
@OnlyIn(Dist.CLIENT)
final class GhostVertexBuilder implements IVertexBuilder {
    private static final int ALPHA = 150;
    /** Packed block/sky light for a bright preview (0xF000F0). */
    private static final int FULL_BRIGHT = 0xF000F0;

    private final IVertexBuilder delegate;

    GhostVertexBuilder(IVertexBuilder delegate) {
        this.delegate = delegate;
    }

    @Override
    public IVertexBuilder vertex(double x, double y, double z) {
        delegate.vertex(x, y, z);
        return this;
    }

    @Override
    public IVertexBuilder color(int red, int green, int blue, int alpha) {
        delegate.color(red, green, blue, ALPHA);
        return this;
    }

    @Override
    public IVertexBuilder uv(float u, float v) {
        delegate.uv(u, v);
        return this;
    }

    @Override
    public IVertexBuilder overlayCoords(int u, int v) {
        delegate.overlayCoords(u, v);
        return this;
    }

    @Override
    public IVertexBuilder uv2(int u, int v) {
        delegate.uv2(FULL_BRIGHT, FULL_BRIGHT);
        return this;
    }

    @Override
    public IVertexBuilder normal(float x, float y, float z) {
        delegate.normal(x, y, z);
        return this;
    }

    @Override
    public void endVertex() {
        delegate.endVertex();
    }
}
