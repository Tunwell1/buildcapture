package com.tunwell.buildcapture.client;

import com.tunwell.buildcapture.BuildCapture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.IngameMenuScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.Util;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/**
 * Input for the placement ghost while no screen is open. The action keys exist
 * as real key bindings, so they can be rebound or given a modifier under
 * Controls; the wheel itself cannot be bound, so its two functions are on
 * hold keys: the face-edit key turns the ghost and
 * {@link CaptureKeys#WHEEL_VERTICAL} changes its height. A plain wheel moves it
 * five blocks, matching the old Shift+wheel step.
 *
 * <p>Right click (or Enter, through the save binding) confirms, middle click or
 * Escape cancels. The mouse gestures stay fixed because they are the vanilla
 * buttons.</p>
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = BuildCapture.MOD_ID, value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class PlacementController {
    /**
     * Free-spinning wheels can send several scroll events per notch; without a
     * floor the ghost would do a full turn from one flick of the finger. A
     * quarter turn per 100 ms is still far faster than anyone needs.
     */
    private static final long ROTATE_COOLDOWN_MILLIS = 100L;

    private static long lastRotateAt;

    private PlacementController() {
    }

    @SubscribeEvent
    public static void onScreenOpening(GuiOpenEvent event) {
        if (event.getGui() == null) {
            // A key typed into a screen can leave a queued click behind when the
            // screen is closed with the mouse; drop those before the world sees
            // them.
            drainActionKeys();
        }
        // Closing the screen fires with a null gui; the preview must survive that,
        // otherwise starting it from the browser would cancel it immediately.
        if (!PlacementPreview.active() || event.getGui() == null) {
            return;
        }
        PlacementPreview.stop();
        // Escape should only cancel the ghost, not also open the pause menu.
        if (event.getGui() instanceof IngameMenuScreen) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onScroll(InputEvent.MouseScrollEvent event) {
        if (!PlacementPreview.active() || Minecraft.getInstance().screen != null) {
            return;
        }
        double delta = event.getScrollDelta();
        if (delta == 0.0D) {
            return;
        }
        int direction = (int) Math.signum(delta);
        int step = direction * (Screen.hasShiftDown() ? 5 : 1);

        if (CaptureKeys.FACE_EDIT.isDown()) {
            long now = Util.getMillis();
            if (now - lastRotateAt >= ROTATE_COOLDOWN_MILLIS) {
                lastRotateAt = now;
                PlacementPreview.rotate(direction);
            }
        } else if (CaptureKeys.WHEEL_VERTICAL.isDown()) {
            PlacementPreview.move(0, step, 0);
        } else {
            // Five blocks per notch, like the old Shift+wheel; the arrow keys are
            // the one-block control.
            PlacementPreview.moveForward(direction * 5);
        }
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onRawMouse(InputEvent.RawMouseEvent event) {
        if (!PlacementPreview.active() || Minecraft.getInstance().screen != null) {
            return;
        }
        if (event.getAction() != GLFW.GLFW_PRESS) {
            return;
        }
        if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_MIDDLE) {
            PlacementPreview.stop();
        } else if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            PlacementPreview.confirm();
        }
        // Keep the click from breaking or using the block behind the ghost.
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onKey(InputEvent.KeyInputEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        // Drain on every key press, including while a screen is open, so a click
        // queued inside the chat cannot fire after it closes.
        boolean rotate = CaptureKeys.PREVIEW_ROTATE.consumeClick();
        boolean forward = CaptureKeys.PREVIEW_FORWARD.consumeClick();
        boolean back = CaptureKeys.PREVIEW_BACK.consumeClick();
        boolean left = CaptureKeys.PREVIEW_LEFT.consumeClick();
        boolean right = CaptureKeys.PREVIEW_RIGHT.consumeClick();
        boolean up = CaptureKeys.PREVIEW_UP.consumeClick();
        boolean down = CaptureKeys.PREVIEW_DOWN.consumeClick();

        if (!PlacementPreview.active() || minecraft.screen != null) {
            return;
        }
        boolean acted = false;
        int step = Screen.hasShiftDown() ? 5 : 1;
        if (rotate) {
            PlacementPreview.rotate();
            acted = true;
        }
        if (forward) {
            PlacementPreview.moveForward(step);
            acted = true;
        }
        if (back) {
            PlacementPreview.moveForward(-step);
            acted = true;
        }
        if (left) {
            PlacementPreview.moveSideways(-step);
            acted = true;
        }
        if (right) {
            PlacementPreview.moveSideways(step);
            acted = true;
        }
        if (up) {
            PlacementPreview.move(0, step, 0);
            acted = true;
        }
        if (down) {
            PlacementPreview.move(0, -step, 0);
            acted = true;
        }
        if (acted) {
            event.setCanceled(true);
        }
    }

    private static void drainActionKeys() {
        drain(CaptureKeys.PREVIEW_ROTATE);
        drain(CaptureKeys.PREVIEW_FORWARD);
        drain(CaptureKeys.PREVIEW_BACK);
        drain(CaptureKeys.PREVIEW_LEFT);
        drain(CaptureKeys.PREVIEW_RIGHT);
        drain(CaptureKeys.PREVIEW_UP);
        drain(CaptureKeys.PREVIEW_DOWN);
    }

    private static void drain(KeyBinding binding) {
        while (binding.consumeClick()) {
            // Discard the whole queue.
        }
    }
}
