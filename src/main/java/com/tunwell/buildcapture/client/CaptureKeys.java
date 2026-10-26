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
 * In-world keys for capture work. {@link #FACE_EDIT} is only a modifier: the
 * mouse buttons keep their normal meaning when it is not held.
 *
 * <p>The corner and save keys exist so the usual flow does not need the chat
 * window at all. They all go through the same server-side code as the
 * {@code /capture} commands, so the messages and checks are identical.</p>
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = BuildCapture.MOD_ID, value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.MOD)
public final class CaptureKeys {
    public static final KeyBinding FACE_EDIT = new KeyBinding(
            "key.buildcapture.capture_face_edit", GLFW.GLFW_KEY_LEFT_ALT,
            "key.buildcapture.capture");
    public static final KeyBinding MENU = new KeyBinding(
            "key.buildcapture.capture_menu", GLFW.GLFW_KEY_C,
            "key.buildcapture.capture");
    public static final KeyBinding CORNER1 = new KeyBinding(
            "key.buildcapture.corner1", GLFW.GLFW_KEY_LEFT_BRACKET,
            "key.buildcapture.capture");
    public static final KeyBinding CORNER2 = new KeyBinding(
            "key.buildcapture.corner2", GLFW.GLFW_KEY_RIGHT_BRACKET,
            "key.buildcapture.capture");
    public static final KeyBinding SAVE = new KeyBinding(
            "key.buildcapture.save", GLFW.GLFW_KEY_ENTER,
            "key.buildcapture.capture");

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
            ClientDropHandler.install();
        });
    }
}
