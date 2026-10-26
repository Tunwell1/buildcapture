package com.tunwell.buildcapture.client;

import com.tunwell.buildcapture.BuildCapture;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.lwjgl.glfw.GLFW;

/**
 * In-world keys for capture work. Every binding here can be changed under
 * Controls, including key combinations: Forge captures the modifier that is
 * held when the new key is pressed, so "Ctrl+R" or "Alt+Up" are valid bindings.
 *
 * <p>{@link #FACE_EDIT} and {@link #WHEEL_VERTICAL} are hold keys: they do not
 * do anything on their own, they change what the mouse does. The scroll wheel
 * itself cannot be bound by Minecraft, so the wheel's second function is the
 * modifier that must be held while scrolling.</p>
 *
 * <p>The corner and save keys exist so the usual flow does not need the chat
 * window at all. They all go through the same server-side code as the
 * {@code /capture} commands, so the messages and checks are identical.</p>
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = BuildCapture.MOD_ID, value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.MOD)
public final class CaptureKeys {
    private static final String CATEGORY = "key.buildcapture.capture";

    public static final KeyBinding FACE_EDIT = new KeyBinding(
            "key.buildcapture.capture_face_edit", GLFW.GLFW_KEY_LEFT_ALT, CATEGORY);
    public static final KeyBinding MENU = new KeyBinding(
            "key.buildcapture.capture_menu", GLFW.GLFW_KEY_C, CATEGORY);
    public static final KeyBinding CORNER1 = new KeyBinding(
            "key.buildcapture.corner1", GLFW.GLFW_KEY_LEFT_BRACKET, CATEGORY);
    public static final KeyBinding CORNER2 = new KeyBinding(
            "key.buildcapture.corner2", GLFW.GLFW_KEY_RIGHT_BRACKET, CATEGORY);
    public static final KeyBinding SAVE = new KeyBinding(
            "key.buildcapture.save", GLFW.GLFW_KEY_ENTER, CATEGORY);

    // Placement preview. These are separate bindings rather than raw key codes so
    // the whole control scheme is visible and rebindable under Controls.
    public static final KeyBinding PREVIEW_ROTATE = new KeyBinding(
            "key.buildcapture.preview_rotate", GLFW.GLFW_KEY_R, CATEGORY);
    public static final KeyBinding PREVIEW_FORWARD = new KeyBinding(
            "key.buildcapture.preview_forward", GLFW.GLFW_KEY_UP, CATEGORY);
    public static final KeyBinding PREVIEW_BACK = new KeyBinding(
            "key.buildcapture.preview_back", GLFW.GLFW_KEY_DOWN, CATEGORY);
    public static final KeyBinding PREVIEW_LEFT = new KeyBinding(
            "key.buildcapture.preview_left", GLFW.GLFW_KEY_LEFT, CATEGORY);
    public static final KeyBinding PREVIEW_RIGHT = new KeyBinding(
            "key.buildcapture.preview_right", GLFW.GLFW_KEY_RIGHT, CATEGORY);
    public static final KeyBinding PREVIEW_UP = new KeyBinding(
            "key.buildcapture.preview_up", GLFW.GLFW_KEY_PAGE_UP, CATEGORY);
    public static final KeyBinding PREVIEW_DOWN = new KeyBinding(
            "key.buildcapture.preview_down", GLFW.GLFW_KEY_PAGE_DOWN, CATEGORY);

    // Hold modifier for the wheel's vertical move. The turn modifier is the same
    // "edit" key the capture box uses, so Alt means "turn it" in both places and
    // the Controls screen has no duplicate binding to flag.
    public static final KeyBinding WHEEL_VERTICAL = new KeyBinding(
            "key.buildcapture.wheel_vertical", GLFW.GLFW_KEY_LEFT_CONTROL, CATEGORY);

    private CaptureKeys() {
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            ClientRegistry.registerKeyBinding(FACE_EDIT);
            ClientRegistry.registerKeyBinding(MENU);
            ClientRegistry.registerKeyBinding(CORNER1);
            ClientRegistry.registerKeyBinding(CORNER2);
            ClientRegistry.registerKeyBinding(SAVE);
            ClientRegistry.registerKeyBinding(PREVIEW_ROTATE);
            ClientRegistry.registerKeyBinding(PREVIEW_FORWARD);
            ClientRegistry.registerKeyBinding(PREVIEW_BACK);
            ClientRegistry.registerKeyBinding(PREVIEW_LEFT);
            ClientRegistry.registerKeyBinding(PREVIEW_RIGHT);
            ClientRegistry.registerKeyBinding(PREVIEW_UP);
            ClientRegistry.registerKeyBinding(PREVIEW_DOWN);
            ClientRegistry.registerKeyBinding(WHEEL_VERTICAL);
            ClientDropHandler.install();
        });
    }
}
