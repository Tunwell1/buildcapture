package com.tunwell.buildcapture.client;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.systems.RenderSystem;
import com.tunwell.buildcapture.BuildCapture;
import com.tunwell.buildcapture.config.BuildCaptureConfig;
import net.minecraft.block.BlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockRendererDispatcher;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.texture.AtlasTexture;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.renderer.vertex.VertexBuffer;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.model.data.EmptyModelData;
import org.lwjgl.opengl.GL11;

import javax.annotation.Nullable;
import java.util.Random;

/**
 * Draws the placement ghost from a cached vertex buffer. Tesselating tens of
 * thousands of block models every frame is what made large previews lag, so the
 * models are baked once and then re-drawn with a translation. The cache is
 * rebuilt only when the structure, its rotation, the alpha setting or the
 * 16-block tint cell around the preview changes, so moving the ghost stays
 * smooth.
 */
@OnlyIn(Dist.CLIENT)
final class GhostRenderer {
    private static final VertexFormat FORMAT = DefaultVertexFormats.BLOCK;
    private static final int TRANSLUCENT_ALPHA = 150;
    private static final int OPAQUE_ALPHA = 255;
    /** Rough number of bytes one block contributes, used to size the build buffer. */
    private static final int BYTES_PER_BLOCK = 256;
    private static final int MAX_INITIAL_BYTES = 16 << 20;

    @Nullable
    private static VertexBuffer buffer;
    @Nullable
    private static GhostStructure bufferGhost;
    @Nullable
    private static BlockPos bufferOrigin;
    private static int bufferRevision = -1;
    private static int bufferAlpha = -1;

    private GhostRenderer() {
    }

    static void draw(MatrixStack matrixStack, GhostStructure ghost, BlockPos origin) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientWorld level = minecraft.level;
        if (level == null) {
            return;
        }
        int alpha = alpha();
        VertexBuffer mesh = mesh(ghost, origin, alpha, level);
        if (mesh == null) {
            return;
        }

        RenderSystem.enableTexture();
        minecraft.getTextureManager().bind(AtlasTexture.LOCATION_BLOCKS);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.color4f(1.0F, 1.0F, 1.0F, 1.0F);

        matrixStack.pushPose();
        matrixStack.translate(origin.getX(), origin.getY(), origin.getZ());
        mesh.bind();
        FORMAT.setupBufferState(0L);
        mesh.draw(matrixStack.last().pose(), GL11.GL_QUADS);
        VertexBuffer.unbind();
        FORMAT.clearBufferState();
        matrixStack.popPose();

        RenderSystem.disableTexture();
    }

    /** Frees the cached buffer; must be called on the render thread. */
    static void release() {
        if (buffer != null) {
            buffer.close();
        }
        buffer = null;
        bufferGhost = null;
        bufferOrigin = null;
        bufferRevision = -1;
        bufferAlpha = -1;
    }

    /**
     * Cached mesh for this preview state. A failed build is remembered too, so a
     * broken model cannot force a rebuild on every frame.
     */
    @Nullable
    private static VertexBuffer mesh(GhostStructure ghost, BlockPos origin, int alpha,
                                     ClientWorld level) {
        if (bufferGhost == ghost && bufferOrigin != null
                && sameTintCell(bufferOrigin, origin)
                && bufferRevision == ghost.revision() && bufferAlpha == alpha) {
            return buffer;
        }
        release();
        VertexBuffer built = null;
        try {
            built = build(ghost, origin, alpha, level);
        } catch (RuntimeException exception) {
            BuildCapture.LOGGER.warn("Could not build the placement preview mesh", exception);
        }
        buffer = built;
        bufferGhost = ghost;
        bufferOrigin = origin;
        bufferRevision = ghost.revision();
        bufferAlpha = alpha;
        return built;
    }

    @Nullable
    private static VertexBuffer build(GhostStructure ghost, BlockPos origin, int alpha,
                                      ClientWorld level) {
        int blocks = ghost.blockCount();
        if (blocks <= 0) {
            return null;
        }

        int estimatedBytes = Math.min(blocks * BYTES_PER_BLOCK, MAX_INITIAL_BYTES);
        BufferBuilder builder = new BufferBuilder(Math.max(1 << 14, estimatedBytes / 4));
        builder.begin(GL11.GL_QUADS, FORMAT);

        BlockRendererDispatcher dispatcher = Minecraft.getInstance().getBlockRenderer();
        MatrixStack matrixStack = new MatrixStack();
        Random random = new Random(0L);
        GhostVertexBuilder ghostBuilder = new GhostVertexBuilder(builder, alpha);
        for (int index = 0; index < blocks; index++) {
            if (!ghost.visible(index)) {
                continue;
            }
            BlockState state = ghost.state(index);
            if (state.isAir()) {
                continue;
            }
            int packed = ghost.position(index);
            int x = packed & 1023;
            int y = (packed >> 10) & 1023;
            int z = (packed >> 20) & 1023;
            matrixStack.pushPose();
            matrixStack.translate(x, y, z);
            // The world position is baked into the vertex colours (biome tint)
            // and into random model offsets, so the origin has to be part of the
            // cache key - see sameTintCell.
            dispatcher.renderModel(state, origin.offset(x, y, z), level, matrixStack,
                    ghostBuilder, false, random, EmptyModelData.INSTANCE);
            matrixStack.popPose();
        }

        builder.end();
        VertexBuffer mesh = new VertexBuffer(FORMAT);
        mesh.upload(builder);
        return mesh;
    }

    /**
     * Moving the preview one block would otherwise rebuild the whole mesh just
     * for the tint. Biome colours change on a much larger scale, so the mesh is
     * reused until the preview crosses a 16-block cell; the vertical axis does
     * not affect the tint at all.
     */
    private static boolean sameTintCell(BlockPos first, BlockPos second) {
        return (first.getX() >> 4) == (second.getX() >> 4)
                && (first.getZ() >> 4) == (second.getZ() >> 4);
    }

    private static int alpha() {
        try {
            return BuildCaptureConfig.CAPTURE_PREVIEW_TRANSLUCENT.get()
                    ? TRANSLUCENT_ALPHA : OPAQUE_ALPHA;
        } catch (IllegalStateException configNotLoaded) {
            return TRANSLUCENT_ALPHA;
        }
    }
}
