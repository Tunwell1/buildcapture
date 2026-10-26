package com.tunwell.buildcapture.client;

import com.tunwell.buildcapture.BuildCapture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.IngameMenuScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/**
 * Input for the placement ghost while no screen is open: scroll to move,
 * Ctrl+scroll for the vertical axis, arrows for single blocks, R to rotate,
 * right click (or Enter) to confirm, middle click or Escape to cancel.
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = BuildCapture.MOD_ID, value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class PlacementController {
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
        int step = (int) Math.signum(delta) * (Screen.hasShiftDown() ? 5 : 1);
        if (Screen.hasControlDown()) {
            PlacementPreview.move(0, step, 0);
        } else {
            PlacementPreview.moveForward(step);
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
