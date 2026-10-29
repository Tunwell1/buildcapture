package com.tunwell.buildcapture.client;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.systems.RenderSystem;
import com.tunwell.buildcapture.capture.StructureCatalog;
import com.tunwell.buildcapture.network.ModNetwork;
import com.tunwell.buildcapture.network.StructureActionPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.widget.button.Button;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.Util;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.vector.Matrix4f;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.lwjgl.opengl.GL11;

import javax.annotation.Nullable;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The structure browser: search, place with a preview, import an .nbt file,
 * delete, and open the shared library folder.
 */
@OnlyIn(Dist.CLIENT)
public final class CaptureScreen extends Screen {
    private static final int PANEL_WIDTH = 320;
    private static final int PANEL_HEIGHT = 262;
    private static final int ROW_HEIGHT = 18;
    private static final int BG = 0xF00E0E0E;
    private static final int BORDER = 0xFF3C3C3C;
    private static final int ROW_HOVER = 0x30FFFFFF;
    private static final int ROW_SELECTED = 0x604A90D9;
    private static final int TEXT = 0xFFFFFF;
    private static final int TEXT_DIM = 0xA0A0A0;

    private final List<StructureCatalog.Entry> rows = new ArrayList<>();

    private TextFieldWidget searchField;
    private Button placeButton;
    private Button rotateButton;
    private Button importButton;
    private Button deleteButton;
    private Button folderButton;
    private Button undoButton;

    private int left;
    private int top;
    private int panelWidth;
    private int panelHeight;
    private int listLeft;
    private int listTop;
    private int listWidth;
    private int listHeight;
    private int visibleRows;

    private String filter = "";
    private int scroll;
    private int appliedVersion = -1;
    @Nullable
    private String selectedId;
    private int rotationSteps;
    @Nullable
    private String pendingDelete;
    private long pendingDeleteAt;
    private long lastClickAt;
    @Nullable
    private String lastClickId;

    private CaptureScreen() {
        super(new TranslationTextComponent("buildcapture.capture.gui.title"));
    }

    public static void open() {
        Minecraft.getInstance().setScreen(new CaptureScreen());
    }

    @Override
    protected void init() {
        panelWidth = Math.min(this.width - 8, Math.max(300, PANEL_WIDTH));
        panelHeight = Math.min(this.height - 8, PANEL_HEIGHT);
        left = (this.width - panelWidth) / 2;
        top = (this.height - panelHeight) / 2;

        int inner = panelWidth - 16;
        listLeft = left + 8;
        listWidth = inner;
        listTop = top + 40;

        int statusY = top + panelHeight - 14;
        int bottom2 = statusY - 4 - 18;
        int bottom1 = bottom2 - 2 - 18;
        listHeight = Math.max(ROW_HEIGHT, bottom1 - 6 - listTop);
        visibleRows = listHeight / ROW_HEIGHT;

        int searchWidth = inner - 90 - 6;
        searchField = new TextFieldWidget(this.font, left + 8, top + 18, searchWidth, 18,
                new TranslationTextComponent("buildcapture.capture.gui.search"));
        searchField.setMaxLength(64);
        searchField.setResponder(text -> {
            filter = text == null ? "" : text;
            scroll = 0;
            rebuild();
        });
        addButton(searchField);

        Button refresh = addButton(new Button(left + 8 + searchWidth + 6, top + 18, 90, 18,
                new TranslationTextComponent("buildcapture.capture.gui.refresh"),
                button -> ModNetwork.CHANNEL.sendToServer(StructureActionPacket.list())));

        int buttonWidth = (inner - 12) / 4;
        placeButton = addButton(new Button(left + 8, bottom1, buttonWidth, 18,
                new TranslationTextComponent("buildcapture.capture.gui.place"),
                button -> placeSelected()));
        rotateButton = addButton(new Button(left + 8 + buttonWidth + 4, bottom1,
                buttonWidth, 18, rotateLabel(), button -> {
            rotationSteps = (rotationSteps + 1) % 4;
            button.setMessage(rotateLabel());
        }));
        importButton = addButton(new Button(left + 8 + (buttonWidth + 4) * 2, bottom1,
                buttonWidth, 18, new TranslationTextComponent("buildcapture.capture.gui.import"),
                button -> ImportUploader.openFileDialog()));
        deleteButton = addButton(new Button(left + 8 + (buttonWidth + 4) * 3, bottom1,
                buttonWidth, 18, new TranslationTextComponent("buildcapture.capture.gui.delete"),
                button -> deleteSelected()));

        int folderWidth = 110;
        int undoWidth = 70;
        int closeWidth = 64;
        folderButton = addButton(new Button(left + 8, bottom2, folderWidth, 18,
                new TranslationTextComponent("buildcapture.capture.gui.folder"),
                button -> openFolder()));
        undoButton = addButton(new Button(left + 8 + folderWidth + 4, bottom2, undoWidth, 18,
                new TranslationTextComponent("buildcapture.capture.gui.undo"),
                button -> ModNetwork.CHANNEL.sendToServer(StructureActionPacket.undo())));
        addButton(new Button(left + 8 + inner - closeWidth, bottom2, closeWidth, 18,
                new TranslationTextComponent("buildcapture.capture.gui.close"),
                button -> onClose()));

        rebuild();
        ModNetwork.CHANNEL.sendToServer(StructureActionPacket.list());
    }

    private ITextComponent rotateLabel() {
        return new TranslationTextComponent("buildcapture.capture.gui.rotate", rotationSteps * 90);
    }

    @Override
    public void tick() {
        if (appliedVersion != ClientStructureList.version()) {
            appliedVersion = ClientStructureList.version();
            rebuild();
        }
        if (pendingDelete != null && Util.getMillis() - pendingDeleteAt > 4000L) {
            pendingDelete = null;
            deleteButton.setMessage(
                    new TranslationTextComponent("buildcapture.capture.gui.delete"));
        }
        StructureCatalog.Entry entry = selected();
        placeButton.active = entry != null && entry.size().getX() > 0;
        deleteButton.active = entry != null;
        undoButton.active = ClientStructureList.canUndo();
        folderButton.active = isLibraryDirectory(ClientStructureList.libraryPath());
    }

    private static boolean isLibraryDirectory(String path) {
        if (path.isEmpty()) {
            return false;
        }
        try {
            return Files.isDirectory(Paths.get(path));
        } catch (InvalidPathException exception) {
            // A server on another OS can send a path this client cannot parse.
            return false;
        }
    }

    private void rebuild() {
        rows.clear();
        String needle = filter.trim().toLowerCase(Locale.ROOT);
        for (StructureCatalog.Entry entry : ClientStructureList.entries()) {
            if (needle.isEmpty()
                    || entry.id().toString().toLowerCase(Locale.ROOT).contains(needle)) {
                rows.add(entry);
            }
        }
        if (selectedId != null && selected() == null) {
            selectedId = null;
        }
        clampScroll();
    }

    private void clampScroll() {
        int maximum = Math.max(0, rows.size() - visibleRows);
        scroll = Math.max(0, Math.min(scroll, maximum));
    }

    @Nullable
    private StructureCatalog.Entry selected() {
        if (selectedId == null) {
            return null;
        }
        for (StructureCatalog.Entry entry : rows) {
            if (entry.id().toString().equals(selectedId)) {
                return entry;
            }
        }
        return null;
    }

    private void select(StructureCatalog.Entry entry) {
        selectedId = entry.id().toString();
        long now = Util.getMillis();
        boolean doubleClick = entry.id().toString().equals(lastClickId)
                && now - lastClickAt < 400L;
        lastClickId = entry.id().toString();
        lastClickAt = now;
        if (doubleClick) {
            placeSelected();
        }
    }

    private void placeSelected() {
        StructureCatalog.Entry entry = selected();
        if (entry == null) {
            return;
        }
        if (entry.size().getX() <= 0) {
            return;
        }
        PlacementPreview.start(entry, rotationSteps);
        onClose();
        if (this.minecraft != null && this.minecraft.player != null) {
            this.minecraft.player.displayClientMessage(new TranslationTextComponent(
                    "buildcapture.capture.preview_hint", entry.id().toString(),
                    CaptureKeys.FACE_EDIT.getTranslatedKeyMessage(),
                    CaptureKeys.WHEEL_VERTICAL.getTranslatedKeyMessage()), false);
        }
    }

    private void deleteSelected() {
        StructureCatalog.Entry entry = selected();
        if (entry == null) {
            return;
        }
        String id = entry.id().toString();
        long now = Util.getMillis();
        boolean confirmed = id.equals(pendingDelete) && now - pendingDeleteAt <= 4000L;
        if (!confirmed) {
            pendingDelete = id;
            pendingDeleteAt = now;
            deleteButton.setMessage(
                    new TranslationTextComponent("buildcapture.capture.gui.delete_confirm"));
            return;
        }
        pendingDelete = null;
        deleteButton.setMessage(new TranslationTextComponent("buildcapture.capture.gui.delete"));
        ModNetwork.CHANNEL.sendToServer(StructureActionPacket.delete(id));
    }

    private void openFolder() {
        String path = ClientStructureList.libraryPath();
        if (path.isEmpty()) {
            return;
        }
        Util.getPlatform().openFile(new File(path));
    }

    @Override
    public void render(MatrixStack matrixStack, int mouseX, int mouseY, float partialTicks) {
        renderBackground(matrixStack);
        rect(matrixStack, left - 1, top - 1, left + panelWidth + 1, top + panelHeight + 1, BORDER);
        rect(matrixStack, left, top, left + panelWidth, top + panelHeight, BG);

        String title = this.title.getString();
        this.font.drawShadow(matrixStack, title,
                left + (panelWidth - this.font.width(title)) / 2.0F, top + 7.0F, TEXT);

        renderRows(matrixStack, mouseX, mouseY);

        String status = statusText();
        this.font.drawShadow(matrixStack, this.font.plainSubstrByWidth(status, listWidth),
                listLeft, top + panelHeight - 13.0F, TEXT_DIM);

        super.render(matrixStack, mouseX, mouseY, partialTicks);
    }

    private void renderRows(MatrixStack matrixStack, int mouseX, int mouseY) {
        if (rows.isEmpty()) {
            String empty = new TranslationTextComponent(
                    "buildcapture.capture.gui.empty").getString();
            this.font.drawShadow(matrixStack, empty, listLeft + 4.0F, listTop + 4.0F, TEXT_DIM);
            return;
        }
        for (int row = 0; row < visibleRows; row++) {
            int index = scroll + row;
            if (index >= rows.size()) {
                break;
            }
            StructureCatalog.Entry entry = rows.get(index);
            int rowY = listTop + row * ROW_HEIGHT;
            boolean hovered = mouseX >= listLeft && mouseX < listLeft + listWidth
                    && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT;
            if (entry.id().toString().equals(selectedId)) {
                rect(matrixStack, listLeft, rowY, listLeft + listWidth, rowY + ROW_HEIGHT - 1,
                        ROW_SELECTED);
            } else if (hovered) {
                rect(matrixStack, listLeft, rowY, listLeft + listWidth, rowY + ROW_HEIGHT - 1,
                        ROW_HOVER);
            }

            BlockPos size = entry.size();
            String meta = size.getX() > 0
                    ? size.getX() + "x" + size.getY() + "x" + size.getZ()
                            + "  " + kilobytes(entry.bytes())
                    : new TranslationTextComponent("buildcapture.capture.gui.unreadable")
                            .getString();
            int metaWidth = this.font.width(meta);
            String id = this.font.plainSubstrByWidth(entry.id().toString(),
                    Math.max(24, listWidth - metaWidth - 26));
            this.font.drawShadow(matrixStack, id, listLeft + 12.0F, rowY + 5.0F, TEXT);
            this.font.drawShadow(matrixStack, meta,
                    listLeft + listWidth - 4 - metaWidth, rowY + 5.0F, TEXT_DIM);

            int dot = entry.inWorld() && entry.inLibrary() ? 0xFF4CC38A
                    : entry.inWorld() ? 0xFF5AA9E6 : 0xFFE0C341;
            rect(matrixStack, listLeft + 4, rowY + 7, listLeft + 8, rowY + 11, dot);
        }
    }

    private String statusText() {
        double progress = ImportUploader.progress();
        if (progress >= 0.0D) {
            return new TranslationTextComponent("buildcapture.capture.gui.uploading",
                    ImportUploader.currentName(), (int) Math.round(progress * 100.0D))
                    .getString();
        }
        if (pendingDelete != null) {
            return new TranslationTextComponent("buildcapture.capture.gui.delete_wait",
                    pendingDelete).getString();
        }
        StructureCatalog.Entry entry = selected();
        if (entry == null) {
            return new TranslationTextComponent("buildcapture.capture.gui.hint").getString();
        }
        BlockPos size = entry.size();
        return new TranslationTextComponent("buildcapture.capture.gui.selected",
                entry.id().toString(), size.getX(), size.getY(), size.getZ(),
                entry.inWorld() ? new TranslationTextComponent(
                        "buildcapture.capture.gui.world").getString()
                        : new TranslationTextComponent(
                                "buildcapture.capture.gui.library").getString()).getString();
    }

    private static String kilobytes(long bytes) {
        if (bytes < 1024L) {
            return bytes + " B";
        }
        if (bytes < 1024L * 1024L) {
            return (bytes + 1023L) / 1024L + " KB";
        }
        return (bytes + 1024L * 1024L - 1) / (1024L * 1024L) + " MB";
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && mouseX >= listLeft && mouseX < listLeft + listWidth
                && mouseY >= listTop && mouseY < listTop + listHeight) {
            int index = scroll + (int) ((mouseY - listTop) / ROW_HEIGHT);
            if (index >= 0 && index < rows.size()) {
                select(rows.get(index));
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (!rows.isEmpty() && delta != 0.0D) {
            scroll -= (int) Math.signum(delta);
            clampScroll();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
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
