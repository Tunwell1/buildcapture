package com.tunwell.buildcapture.client;

import com.tunwell.buildcapture.BuildCapture;
import com.tunwell.buildcapture.network.CaptureCommandPacket;
import com.tunwell.buildcapture.network.ModNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.util.Direction;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

import javax.annotation.Nullable;

/**
 * Interactive editing of the capture box. Hold {@link CaptureKeys#FACE_EDIT}
 * (left alt by default) and drag the box with the left mouse button: it slides
 * on the ground plane under the cursor, so it goes where the mouse goes instead
 * of where the camera points. The wheel resizes the face under the crosshair,
 * Ctrl+wheel moves the box up and down, shift+drag on a face resizes it by
 * hand, and the arrow keys nudge it one exact block.
 *
 * <p>Moving is a drag because that is how the box behaves like an object;
 * resizing needs a face, because a side is the only unambiguous way to say
 * which end should grow.</p>
 *
 * <p>The client only ever sends complete bounds. The server clamps them to the
 * build height and sends the result back, so the client never owns the real
 * selection.</p>
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = BuildCapture.MOD_ID, value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CaptureInteraction {
    private static final BoxDrag DRAG = new BoxDrag();

    private static int hoverAxis = -1;
    private static int hoverSide;

    /**
     * Set when a screen closes, so the very key press that closed it (Enter to
     * send chat, the save dialog's own Enter, Esc) does not fire a key binding
     * from the world afterwards.
     */
    private static boolean swallowNextKey;

    private CaptureInteraction() {
    }

    /** Axis of the face under the crosshair, or -1 when nothing is highlighted. */
    public static int hoveredAxis() {
        return hoverAxis;
    }

    /** +1 for the face at the maximum corner, -1 for the minimum one. */
    public static int hoveredSide() {
        return hoverSide;
    }

    /**
     * What the last box edit did, for the heads-up display, or {@code null} when
     * nothing happened recently.
     */
    @Nullable
    public static ITextComponent lastAction() {
        return BoxEdit.lastAction();
    }

    /** Recomputes the highlighted face; called once per rendered frame. */
    public static void updateHover() {
        hoverAxis = -1;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null || minecraft.screen != null
                || PlacementPreview.active()) {
            return;
        }
        if (DRAG.active()) {
            hoverAxis = DRAG.axis();
            hoverSide = DRAG.side();
            return;
        }
        if (!CaptureKeys.FACE_EDIT.isDown()) {
            return;
        }
        BoxGeometry.Face face = pick();
        if (face != null) {
            hoverAxis = face.axis;
            hoverSide = face.side;
        }
    }

    @SubscribeEvent
    public static void onScreenChanged(GuiOpenEvent event) {
        if (event.getGui() == null) {
            swallowNextKey = true;
        }
    }

    @SubscribeEvent
    public static void onKey(InputEvent.KeyInputEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        // Always drain the queued clicks: chat lets key bindings count presses
        // (passEvents), so a stale click must not survive until the world is
        // back on screen.
        boolean menu = CaptureKeys.MENU.consumeClick();
        boolean corner1 = CaptureKeys.CORNER1.consumeClick();
        boolean corner2 = CaptureKeys.CORNER2.consumeClick();
        boolean save = CaptureKeys.SAVE.consumeClick();
        boolean swallowed = swallowNextKey;
        swallowNextKey = false;

        if (minecraft.player == null || minecraft.level == null
                || minecraft.screen != null || swallowed) {
            return;
        }
        // The ghost owns the save key and the preview movement bindings while it
        // is on screen; Enter places it instead of opening the save dialog.
        if (PlacementPreview.active()) {
            if (save) {
                PlacementPreview.confirm();
            }
            return;
        }
        if (menu) {
            CaptureScreen.open();
            return;
        }
        if (corner1) {
            requestCorner(CaptureCommandPacket.CORNER1);
            return;
        }
        if (corner2) {
            requestCorner(CaptureCommandPacket.CORNER2);
            return;
        }
        if (save) {
            requestSave();
            return;
        }
        if (!CaptureKeys.FACE_EDIT.isDown()) {
            return;
        }
        if (event.getAction() != GLFW.GLFW_PRESS && event.getAction() != GLFW.GLFW_REPEAT) {
            return;
        }
        int step = Screen.hasShiftDown() ? 5 : 1;
        switch (event.getKey()) {
            case GLFW.GLFW_KEY_UP:
                moveForward(step);
                break;
            case GLFW.GLFW_KEY_DOWN:
                moveForward(-step);
                break;
            case GLFW.GLFW_KEY_LEFT:
                moveSideways(-step);
                break;
            case GLFW.GLFW_KEY_RIGHT:
                moveSideways(step);
                break;
            case GLFW.GLFW_KEY_PAGE_UP:
                BoxEdit.translate(1, step);
                break;
            case GLFW.GLFW_KEY_PAGE_DOWN:
                BoxEdit.translate(1, -step);
                break;
            default:
                return;
        }
        event.setCanceled(true);
    }

    private static void requestCorner(int corner) {
        ModNetwork.CHANNEL.sendToServer(new CaptureCommandPacket(corner, ""));
    }

    private static void requestSave() {
        Minecraft minecraft = Minecraft.getInstance();
        if (ClientCaptureBox.bounds() == null) {
            if (minecraft.player != null) {
                minecraft.player.displayClientMessage(new TranslationTextComponent(
                        "buildcapture.capture.status_empty"), false);
            }
            return;
        }
        SaveSelectionScreen.open();
    }

    @SubscribeEvent
    public static void onRawMouse(InputEvent.RawMouseEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null || minecraft.screen != null
                || PlacementPreview.active()) {
            return;
        }
        if (event.getButton() != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return;
        }
        if (event.getAction() == GLFW.GLFW_PRESS) {
            if (DRAG.active() || !CaptureKeys.FACE_EDIT.isDown()
                    || !minecraft.mouseHandler.isMouseGrabbed()) {
                return;
            }
            BoxGeometry.Face face = pick();
            if (face == null) {
                return;
            }
            beginDrag(face);
            // Keep the vanilla attack key from breaking the block behind the box.
            event.setCanceled(true);
        } else if (event.getAction() == GLFW.GLFW_RELEASE && DRAG.active()) {
            // Apply a last step so a fast flick is not lost between ticks.
            updateDrag();
            endDrag();
        }
    }

    @SubscribeEvent
    public static void onScroll(InputEvent.MouseScrollEvent event) {
        if (DRAG.active()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null || minecraft.screen != null
                || PlacementPreview.active()
                || !CaptureKeys.FACE_EDIT.isDown()
                || ClientCaptureBox.bounds() == null) {
            return;
        }
        double delta = event.getScrollDelta();
        if (delta == 0.0D) {
            return;
        }
        int step = (int) Math.signum(delta) * (Screen.hasShiftDown() ? 5 : 1);
        // Vertical has no face of its own, so it works from any view direction.
        if (CaptureKeys.WHEEL_VERTICAL.isDown()) {
            BoxEdit.translate(1, step);
            event.setCanceled(true);
            return;
        }
        BoxGeometry.Face face = pick();
        if (face == null) {
            return;
        }
        ClientCaptureBox.Bounds bounds = ClientCaptureBox.bounds();
        if (bounds == null) {
            return;
        }
        // The wheel resizes the face under the crosshair; dragging the box body
        // is what moves it now.
        int current = face.side > 0 ? bounds.max[face.axis] : bounds.min[face.axis];
        BoxEdit.moveFace(face.axis, face.side, current + step * face.side);
        event.setCanceled(true);
    }

    /** Moves the whole box along the player's horizontal facing. */
    private static void moveForward(int blocks) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        Direction facing = minecraft.player.getDirection();
        if (facing.getStepX() != 0) {
            BoxEdit.translate(0, facing.getStepX() * blocks);
        } else {
            BoxEdit.translate(2, facing.getStepZ() * blocks);
        }
    }

    /** Moves the whole box to the player's right, relative to where they look. */
    private static void moveSideways(int blocks) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        Direction facing = minecraft.player.getDirection();
        if (facing.getStepX() != 0) {
            BoxEdit.translate(2, facing.getStepX() * blocks);
        } else {
            BoxEdit.translate(0, -facing.getStepZ() * blocks);
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            // A screen closed by a mouse click should not eat the next key press.
            swallowNextKey = false;
            return;
        }
        if (!DRAG.active()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null
                || minecraft.screen != null || !minecraft.isWindowActive()
                || !minecraft.mouseHandler.isMouseGrabbed()
                || ClientCaptureBox.bounds() == null) {
            endDrag();
            return;
        }
        updateDrag();
    }

    private static void beginDrag(BoxGeometry.Face face) {
        ClientCaptureBox.Bounds bounds = ClientCaptureBox.bounds();
        Minecraft minecraft = Minecraft.getInstance();
        if (bounds == null || minecraft.player == null) {
            return;
        }
        DRAG.begin(face, Screen.hasShiftDown(), bounds, minecraft.player);
        hoverAxis = face.axis;
        hoverSide = face.side;
    }

    private static void endDrag() {
        DRAG.end();
        hoverAxis = -1;
    }

    private static void updateDrag() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            DRAG.update(minecraft.player);
        }
    }

    @Nullable
    private static BoxGeometry.Face pick() {
        ClientCaptureBox.Bounds bounds = ClientCaptureBox.bounds();
        Minecraft minecraft = Minecraft.getInstance();
        if (bounds == null || minecraft.player == null || minecraft.level == null) {
            return null;
        }
        Vector3d eye = minecraft.player.getEyePosition(1.0F);
        Vector3d view = minecraft.player.getViewVector(1.0F);
        return BoxGeometry.pick(bounds, eye, view);
    }
}
