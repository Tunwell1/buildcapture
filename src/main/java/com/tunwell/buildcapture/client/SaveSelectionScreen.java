package com.tunwell.buildcapture.client;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.systems.RenderSystem;
import com.tunwell.buildcapture.network.CaptureCommandPacket;
import com.tunwell.buildcapture.network.ModNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.widget.button.Button;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.math.vector.Matrix4f;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Asks for the name of the capture before saving, instead of making the player
 * type the whole {@code /capture save} command. The name is pre-filled with a
 * timestamp, so confirming with Enter is enough for the common case.
 */
@OnlyIn(Dist.CLIENT)
public final class SaveSelectionScreen extends Screen {
    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("yyyy_MM_dd_HHmmss");
    private static final int PANEL_WIDTH = 260;
    private static final int PANEL_HEIGHT = 108;
    private static final int BG = 0xF00E0E0E;
    private static final int BORDER = 0xFF3C3C3C;
    private static final int TEXT = 0xFFFFFF;
    private static final int TEXT_DIM = 0xA0A0A0;

    private final String initialName;

    private TextFieldWidget nameField;
    private Button saveButton;

    private int left;
    private int top;

    private SaveSelectionScreen(String initialName) {
        super(new TranslationTextComponent("buildcapture.capture.gui.save_title"));
        this.initialName = initialName;
    }

    public static void open() {
        Minecraft.getInstance().setScreen(new SaveSelectionScreen(defaultName()));
    }

    private static String defaultName() {
        return "capture_" + STAMP.format(LocalDateTime.now());
    }

    @Override
    protected void init() {
        left = (this.width - PANEL_WIDTH) / 2;
        top = (this.height - PANEL_HEIGHT) / 2;

        nameField = new TextFieldWidget(this.font, left + 8, top + 42, PANEL_WIDTH - 16, 18,
                new TranslationTextComponent("buildcapture.capture.gui.save_name"));
        nameField.setMaxLength(120);
        nameField.setValue(initialName);
        nameField.setResponder(value -> updateSaveButton());
        addButton(nameField);
        setInitialFocus(nameField);
        nameField.setCursorPosition(initialName.length());

        int buttonWidth = (PANEL_WIDTH - 16 - 6) / 2;
        saveButton = addButton(new Button(left + 8, top + 84, buttonWidth, 18,
                new TranslationTextComponent("buildcapture.capture.gui.save"),
                button -> save()));
        addButton(new Button(left + 8 + buttonWidth + 6, top + 84, buttonWidth, 18,
                new TranslationTextComponent("buildcapture.capture.gui.cancel"),
                button -> onClose()));
        updateSaveButton();
    }

    private void updateSaveButton() {
        if (saveButton != null && nameField != null) {
            saveButton.active = !nameField.getValue().trim().isEmpty();
        }
    }

    private void save() {
        if (nameField == null) {
            return;
        }
        String name = nameField.getValue().trim();
        if (name.isEmpty()) {
            return;
        }
        ModNetwork.CHANNEL.sendToServer(new CaptureCommandPacket(CaptureCommandPacket.SAVE, name));
        onClose();
    }

    @Override
    public void tick() {
        if (nameField != null) {
            nameField.tick();
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            if (saveButton != null && saveButton.active) {
                save();
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(MatrixStack matrixStack, int mouseX, int mouseY, float partialTicks) {
        renderBackground(matrixStack);
        rect(matrixStack, left - 1, top - 1, left + PANEL_WIDTH + 1, top + PANEL_HEIGHT + 1, BORDER);
        rect(matrixStack, left, top, left + PANEL_WIDTH, top + PANEL_HEIGHT, BG);

        String title = this.title.getString();
        this.font.drawShadow(matrixStack, title,
                left + (PANEL_WIDTH - this.font.width(title)) / 2.0F, top + 8.0F, TEXT);

        ITextComponent size = sizeText();
        if (size != null) {
            String text = size.getString();
            this.font.drawShadow(matrixStack, text,
                    left + (PANEL_WIDTH - this.font.width(text)) / 2.0F, top + 24.0F, TEXT_DIM);
        }

        String hint = new TranslationTextComponent(
                "buildcapture.capture.gui.save_hint").getString();
        this.font.drawShadow(matrixStack,
                this.font.plainSubstrByWidth(hint, PANEL_WIDTH - 16), left + 8.0F, top + 66.0F,
                TEXT_DIM);

        super.render(matrixStack, mouseX, mouseY, partialTicks);
    }

    private ITextComponent sizeText() {
        ClientCaptureBox.Bounds bounds = ClientCaptureBox.bounds();
        if (bounds == null) {
            return null;
        }
        return new TranslationTextComponent("buildcapture.capture.gui.save_size",
                bounds.size(0), bounds.size(1), bounds.size(2),
                bounds.min[0], bounds.min[1], bounds.min[2]);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(null);
        }
    }

    /** Solid rectangle helper, so the screen does not depend on GuiScreen internals. */
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
