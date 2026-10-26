package com.tunwell.buildcapture.client;

import com.tunwell.buildcapture.BuildCapture;
import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.vector.Matrix4f;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.opengl.GL11;

import javax.annotation.Nullable;

/**
 * Draws the capture selection as a translucent box. The box is server-side
 * state, so it is refreshed by {@link com.tunwell.buildcapture.network.CaptureBoxPacket}.
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = BuildCapture.MOD_ID, value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CaptureBoxRenderer {
    private static final double EXPANSION = 0.002D;
    private static final float FILL_ALPHA = 0.15F;
    private static final float RED = 0.25F;
    private static final float GREEN = 0.8F;
    private static final float BLUE = 1.0F;

    @Nullable
    private static ClientWorld lastLevel;

    private CaptureBoxRenderer() {
    }

    @SubscribeEvent
    public static void render(RenderWorldLastEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (lastLevel != null && minecraft.level != lastLevel) {
            ClientCaptureBox.clear();
            PlacementPreview.stop();
            GhostRenderer.release();
        }
        lastLevel = minecraft.level;

        if (minecraft.player == null || minecraft.level == null) {
            return;
        }

        CaptureInteraction.updateHover();
        BlockPos first = ClientCaptureBox.first();
        BlockPos second = ClientCaptureBox.second();
        boolean hasBox = first != null && second != null;
        boolean hasPreview = PlacementPreview.active()
                && PlacementPreview.origin() != null;
        if (!hasPreview || PlacementPreview.ghost() == null) {
            GhostRenderer.release();
        }
        if (!hasBox && !hasPreview) {
            return;
        }

        Vector3d camera = minecraft.gameRenderer.getMainCamera().getPosition();
        MatrixStack matrixStack = event.getMatrixStack();
        matrixStack.pushPose();
        matrixStack.translate(-camera.x, -camera.y, -camera.z);

        RenderSystem.disableTexture();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuilder();

        if (hasBox) {
            BlockPos min = new BlockPos(
                    Math.min(first.getX(), second.getX()),
                    Math.min(first.getY(), second.getY()),
                    Math.min(first.getZ(), second.getZ()));
            BlockPos max = new BlockPos(
                    Math.max(first.getX(), second.getX()),
                    Math.max(first.getY(), second.getY()),
                    Math.max(first.getZ(), second.getZ()));

            RenderSystem.enableDepthTest();
            buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
            drawFaces(matrixStack, buffer, min, max, RED, GREEN, BLUE, FILL_ALPHA);
            tessellator.end();

            // The highlighted face is always visible, like the outline, so it can be
            // grabbed even when part of the box is behind terrain.
            int hoverAxis = CaptureInteraction.hoveredAxis();
            if (hoverAxis >= 0) {
                RenderSystem.disableDepthTest();
                buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
                drawHighlight(matrixStack, buffer, min, max, hoverAxis,
                        CaptureInteraction.hoveredSide());
                tessellator.end();
            }

            // The outline ignores depth so the corners stay visible through terrain.
            RenderSystem.disableDepthTest();
            RenderSystem.lineWidth(2.0F);
            buffer.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR);
            drawBox(matrixStack, buffer, min, max, RED, GREEN, BLUE);
            tessellator.end();
        }

        if (hasPreview) {
            drawPreview(matrixStack, buffer, tessellator);
        }

        RenderSystem.lineWidth(1.0F);
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        RenderSystem.enableTexture();
        matrixStack.popPose();
    }

    /** Footprint of the structure about to be placed, green when it fits the world. */
    private static void drawPreview(MatrixStack matrixStack, BufferBuilder buffer,
                                    Tessellator tessellator) {
        BlockPos origin = PlacementPreview.origin();
        if (origin == null) {
            return;
        }
        int[] size = PlacementPreview.size();
        BlockPos min = origin;
        BlockPos max = origin.offset(size[0] - 1, size[1] - 1, size[2] - 1);

        boolean outside = PlacementPreview.outsideWorld();
        float red = outside ? 1.0F : 0.35F;
        float green = outside ? 0.35F : 0.95F;
        float blue = outside ? 0.35F : 0.55F;

        RenderSystem.enableDepthTest();
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        drawFaces(matrixStack, buffer, min, max, red, green, blue, 0.10F);
        tessellator.end();

        drawGhost(matrixStack);

        RenderSystem.disableDepthTest();
        RenderSystem.lineWidth(2.0F);
        buffer.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR);
        drawBox(matrixStack, buffer, min, max, red, green, blue);
        tessellator.end();
        RenderSystem.lineWidth(1.0F);
    }

    /** Translucent block models of the structure, at the preview position. */
    private static void drawGhost(MatrixStack matrixStack) {
        GhostStructure ghost = PlacementPreview.ghost();
        BlockPos origin = PlacementPreview.origin();
        if (ghost == null || origin == null || ghost.blockCount() == 0
                || PlacementPreview.outsideWorld()) {
            return;
        }
        GhostRenderer.draw(matrixStack, ghost, origin);
    }

    private static void drawFaces(MatrixStack matrixStack, BufferBuilder buffer,
                                  BlockPos min, BlockPos max,
                                  float red, float green, float blue, float alpha) {
        double x1 = min.getX() - EXPANSION;
        double y1 = min.getY() - EXPANSION;
        double z1 = min.getZ() - EXPANSION;
        double x2 = max.getX() + 1.0D + EXPANSION;
        double y2 = max.getY() + 1.0D + EXPANSION;
        double z2 = max.getZ() + 1.0D + EXPANSION;

        face(matrixStack, buffer, red, green, blue, alpha,
                x1, y1, z1, x2, y1, z1, x2, y1, z2, x1, y1, z2);
        face(matrixStack, buffer, red, green, blue, alpha,
                x1, y2, z1, x1, y2, z2, x2, y2, z2, x2, y2, z1);
        face(matrixStack, buffer, red, green, blue, alpha,
                x1, y1, z1, x1, y2, z1, x2, y2, z1, x2, y1, z1);
        face(matrixStack, buffer, red, green, blue, alpha,
                x1, y1, z2, x2, y1, z2, x2, y2, z2, x1, y2, z2);
        face(matrixStack, buffer, red, green, blue, alpha,
                x1, y1, z1, x1, y1, z2, x1, y2, z2, x1, y2, z1);
        face(matrixStack, buffer, red, green, blue, alpha,
                x2, y1, z1, x2, y2, z1, x2, y2, z2, x2, y1, z2);
    }

    /** Warm tint on the face under the crosshair, slightly proud of the box. */
    private static void drawHighlight(MatrixStack matrixStack, BufferBuilder buffer,
                                      BlockPos min, BlockPos max, int axis, int side) {
        double pad = 0.004D;
        double x1 = min.getX() - EXPANSION;
        double y1 = min.getY() - EXPANSION;
        double z1 = min.getZ() - EXPANSION;
        double x2 = max.getX() + 1.0D + EXPANSION;
        double y2 = max.getY() + 1.0D + EXPANSION;
        double z2 = max.getZ() + 1.0D + EXPANSION;

        float red = 1.0F;
        float green = 0.72F;
        float blue = 0.24F;
        float alpha = 0.45F;

        if (axis == 0) {
            double x = side > 0 ? x2 + pad : x1 - pad;
            face(matrixStack, buffer, red, green, blue, alpha,
                    x, y1, z1, x, y1, z2, x, y2, z2, x, y2, z1);
        } else if (axis == 1) {
            double y = side > 0 ? y2 + pad : y1 - pad;
            face(matrixStack, buffer, red, green, blue, alpha,
                    x1, y, z1, x1, y, z2, x2, y, z2, x2, y, z1);
        } else {
            double z = side > 0 ? z2 + pad : z1 - pad;
            face(matrixStack, buffer, red, green, blue, alpha,
                    x1, y1, z, x2, y1, z, x2, y2, z, x1, y2, z);
        }
    }

    private static void drawBox(MatrixStack matrixStack, BufferBuilder buffer,
                                BlockPos min, BlockPos max,
                                float red, float green, float blue) {
        double x1 = min.getX() - EXPANSION;
        double y1 = min.getY() - EXPANSION;
        double z1 = min.getZ() - EXPANSION;
        double x2 = max.getX() + 1.0D + EXPANSION;
        double y2 = max.getY() + 1.0D + EXPANSION;
        double z2 = max.getZ() + 1.0D + EXPANSION;

        line(matrixStack, buffer, x1, y1, z1, x2, y1, z1, red, green, blue);
        line(matrixStack, buffer, x1, y1, z2, x2, y1, z2, red, green, blue);
        line(matrixStack, buffer, x1, y2, z1, x2, y2, z1, red, green, blue);
        line(matrixStack, buffer, x1, y2, z2, x2, y2, z2, red, green, blue);
        line(matrixStack, buffer, x1, y1, z1, x1, y2, z1, red, green, blue);
        line(matrixStack, buffer, x1, y1, z2, x1, y2, z2, red, green, blue);
        line(matrixStack, buffer, x2, y1, z1, x2, y2, z1, red, green, blue);
        line(matrixStack, buffer, x2, y1, z2, x2, y2, z2, red, green, blue);
        line(matrixStack, buffer, x1, y1, z1, x1, y1, z2, red, green, blue);
        line(matrixStack, buffer, x2, y1, z1, x2, y1, z2, red, green, blue);
        line(matrixStack, buffer, x1, y2, z1, x1, y2, z2, red, green, blue);
        line(matrixStack, buffer, x2, y2, z1, x2, y2, z2, red, green, blue);
    }

    private static void face(MatrixStack matrixStack, BufferBuilder buffer,
                             float red, float green, float blue, float alpha,
                             double x1, double y1, double z1,
                             double x2, double y2, double z2,
                             double x3, double y3, double z3,
                             double x4, double y4, double z4) {
        vertex(matrixStack, buffer, x1, y1, z1, red, green, blue, alpha);
        vertex(matrixStack, buffer, x2, y2, z2, red, green, blue, alpha);
        vertex(matrixStack, buffer, x3, y3, z3, red, green, blue, alpha);
        vertex(matrixStack, buffer, x4, y4, z4, red, green, blue, alpha);
    }

    private static void vertex(MatrixStack matrixStack, BufferBuilder buffer,
                               double x, double y, double z,
                               float red, float green, float blue, float alpha) {
        Matrix4f matrix = matrixStack.last().pose();
        buffer.vertex(matrix, (float) x, (float) y, (float) z)
                .color(red, green, blue, alpha)
                .endVertex();
    }

    private static void line(MatrixStack matrixStack, BufferBuilder buffer,
                             double x1, double y1, double z1,
                             double x2, double y2, double z2,
                             float red, float green, float blue) {
        vertex(matrixStack, buffer, x1, y1, z1, red, green, blue, 1.0F);
        vertex(matrixStack, buffer, x2, y2, z2, red, green, blue, 1.0F);
    }
}
