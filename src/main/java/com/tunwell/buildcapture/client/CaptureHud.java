package com.tunwell.buildcapture.client;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.systems.RenderSystem;
import com.tunwell.buildcapture.BuildCapture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.vector.Matrix4f;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.List;

/**
 * Small panel shown while the face-edit key is held: the current selection and
 * the gestures that work on it. Placing the controls on screen instead of in
 * the chat means the answer to "how do I move it up" is always visible, and the
 * last edit is echoed so the wheel does not feel like guesswork.
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = BuildCapture.MOD_ID, value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CaptureHud {
    private static final int MARGIN = 4;
    private static final int PAD = 5;
    private static final int LINE = 10;
    private static final int BG = 0xC0101010;
    private static final int BORDER = 0x60FFFFFF;
    private static final int HEADER = 0xFFFFFF;
    private static final int DIM = 0xA0A0A0;
    private static final int ACTION = 0xFFD54A;

    private CaptureHud() {
    }

    @SubscribeEvent
    public static void onRenderOverlay(RenderGameOverlayEvent.Post event) {
        if (event.getType() != RenderGameOverlayEvent.ElementType.TEXT) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.screen != null || minecraft.options.hideGui
                || minecraft.options.renderDebug || PlacementPreview.active()) {
            return;
        }
        if (!CaptureKeys.FACE_EDIT.isDown()) {
            return;
        }
        draw(event.getMatrixStack(), minecraft, lines());
    }

    private static List<Line> lines() {
        List<Line> lines = new ArrayList<>();
        ClientCaptureBox.Bounds bounds = ClientCaptureBox.bounds();
        if (bounds == null) {
            lines.addAll(noBoxLines());
            lines.add(new Line(new TranslationTextComponent("buildcapture.hud.browser",
                    key(CaptureKeys.MENU)), DIM));
            return lines;
        }

        lines.add(new Line(new TranslationTextComponent("buildcapture.hud.title",
                bounds.size(0), bounds.size(1), bounds.size(2)), HEADER));
        lines.add(new Line(new TranslationTextComponent("buildcapture.hud.coords",
                bounds.min[0], bounds.max[0], bounds.min[1], bounds.max[1],
                bounds.min[2], bounds.max[2]), DIM));

        ITextComponent action = CaptureInteraction.lastAction();
        if (action != null) {
            lines.add(new Line(action, ACTION));
        }

        lines.add(new Line(new TranslationTextComponent("buildcapture.hud.scroll"), DIM));
        lines.add(new Line(new TranslationTextComponent("buildcapture.hud.drag"), DIM));
        lines.add(new Line(new TranslationTextComponent("buildcapture.hud.hotkeys",
                key(CaptureKeys.CORNER1), key(CaptureKeys.CORNER2),
                key(CaptureKeys.SAVE), key(CaptureKeys.MENU)), DIM));
        return lines;
    }

    /**
     * The box is only drawn once both corners exist, so a half-finished
     * selection would otherwise look like nothing happened.
     */
    private static List<Line> noBoxLines() {
        List<Line> lines = new ArrayList<>();
        BlockPos first = ClientCaptureBox.first();
        BlockPos second = ClientCaptureBox.second();
        if (first == null && second == null) {
            lines.add(new Line(new TranslationTextComponent("buildcapture.hud.none",
                    key(CaptureKeys.CORNER1), key(CaptureKeys.CORNER2)), HEADER));
            return lines;
        }
        boolean hasFirst = first != null;
        BlockPos pos = hasFirst ? first : second;
        lines.add(new Line(new TranslationTextComponent(
                hasFirst ? "buildcapture.hud.corner_one" : "buildcapture.hud.corner_two",
                pos.getX(), pos.getY(), pos.getZ(),
                key(hasFirst ? CaptureKeys.CORNER2 : CaptureKeys.CORNER1)), HEADER));
        return lines;
    }

    private static ITextComponent key(KeyBinding binding) {
        return binding.getTranslatedKeyMessage();
    }

    private static void draw(MatrixStack matrixStack, Minecraft minecraft, List<Line> lines) {
        int width = 0;
        for (Line line : lines) {
            width = Math.max(width, minecraft.font.width(line.text.getString()));
        }
        int height = lines.size() * LINE + PAD * 2 - 2;
        int left = MARGIN;
        int top = MARGIN;

        rect(matrixStack, left, top, left + width + PAD * 2, top + height, BG);
        rect(matrixStack, left, top, left + width + PAD * 2, top + 1, BORDER);

        int y = top + PAD;
        for (Line line : lines) {
            minecraft.font.drawShadow(matrixStack, line.text.getString(),
                    left + PAD, y, line.color);
            y += LINE;
        }
    }

    private static final class Line {
        private final ITextComponent text;
        private final int color;

        private Line(ITextComponent text, int color) {
            this.text = text;
            this.color = color;
        }
    }

    /** Solid rectangle helper, matching the screens. */
    private static void rect(MatrixStack matrixStack, int x1, int y1, int x2, int y2, int color) {
        float alpha = (color >> 24 & 255) / 255.0F;
        float red = (color >> 16 & 255) / 255.0F;
        float green = (color >> 8 & 255) / 255.0F;
        float blue = (color & 255) / 255.0F;

        Matrix4f matrix = matrixStack.last().pose();
        RenderSystem.disableTexture();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuilder();
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        buffer.vertex(matrix, x1, y2, 0.0F).color(red, green, blue, alpha).endVertex();
        buffer.vertex(matrix, x2, y2, 0.0F).color(red, green, blue, alpha).endVertex();
        buffer.vertex(matrix, x2, y1, 0.0F).color(red, green, blue, alpha).endVertex();
        buffer.vertex(matrix, x1, y1, 0.0F).color(red, green, blue, alpha).endVertex();
        tessellator.end();
        RenderSystem.enableTexture();
        RenderSystem.disableBlend();
    }
}
