package com.tunwell.buildcapture.client;

import com.tunwell.buildcapture.BuildCapture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.IngameMenuScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.util.Util;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/**
 * Input for the placement ghost while no screen is open: the wheel rotates the
 * ghost (the same quarter turn as {@code R}), Shift+wheel moves it forward or
 * back five blocks, Ctrl+wheel changes the height, the arrow keys move it one
 * block, right click (or Enter) confirms and middle click or Escape cancels.
 *
 * <p>The wheel rotates because that is the thing you change while choosing a
 * spot; position is set by where you stand and the arrow keys, and rotating by
 * key only is hard to hit.</p>
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
        if (Screen.hasControlDown()) {
            PlacementPreview.move(0, step, 0);
        } else if (Screen.hasShiftDown()) {
            PlacementPreview.moveForward(step);
        } else {
            // The wheel is the rotate dial: up is the R key's direction, down is
            // the other way, one quarter turn per notch.
            long now = Util.getMillis();
            if (now - lastRotateAt >= ROTATE_COOLDOWN_MILLIS) {
                lastRotateAt = now;
                PlacementPreview.rotate(direction);
            }
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
        if (!PlacementPreview.active() || Minecraft.getInstance().screen != null) {
            return;
        }
        if (event.getAction() != GLFW.GLFW_PRESS && event.getAction() != GLFW.GLFW_REPEAT) {
            return;
        }
        int step = Screen.hasShiftDown() ? 5 : 1;
        switch (event.getKey()) {
            case GLFW.GLFW_KEY_R:
                PlacementPreview.rotate();
                break;
            case GLFW.GLFW_KEY_ENTER:
            case GLFW.GLFW_KEY_KP_ENTER:
                PlacementPreview.confirm();
                break;
            case GLFW.GLFW_KEY_LEFT:
                PlacementPreview.moveSideways(-step);
                break;
            case GLFW.GLFW_KEY_RIGHT:
                PlacementPreview.moveSideways(step);
                break;
            case GLFW.GLFW_KEY_UP:
                PlacementPreview.moveForward(step);
                break;
            case GLFW.GLFW_KEY_DOWN:
                PlacementPreview.moveForward(-step);
                break;
            case GLFW.GLFW_KEY_PAGE_UP:
                PlacementPreview.move(0, step, 0);
                break;
            case GLFW.GLFW_KEY_PAGE_DOWN:
                PlacementPreview.move(0, -step, 0);
                break;
            default:
                break;
        }
    }
}
