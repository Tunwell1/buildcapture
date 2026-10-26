package com.tunwell.buildcapture.client;

import com.tunwell.buildcapture.BuildCapture;
import net.minecraft.client.Minecraft;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWDropCallback;
import org.lwjgl.glfw.GLFWDropCallbackI;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Lets the player drop .nbt files onto the game window. GLFW hands the paths to
 * the client, which then uploads them like a file chosen in the dialog.
 */
@OnlyIn(Dist.CLIENT)
public final class ClientDropHandler {
    /** Held in a static field so the JVM cannot collect the live callback. */
    private static final GLFWDropCallbackI CALLBACK = (window, count, names) -> {
        List<Path> files = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            String name = GLFWDropCallback.getName(names, index);
            if (name != null && name.toLowerCase(Locale.ROOT).endsWith(".nbt")) {
                files.add(Paths.get(name));
            }
        }
        if (files.isEmpty()) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player != null) {
                minecraft.player.displayClientMessage(new TranslationTextComponent(
                        "buildcapture.capture.import_not_nbt"), false);
            }
            return;
        }
        for (Path file : files) {
            ImportUploader.enqueue(file);
        }
    };

    private ClientDropHandler() {
    }

    public static void install() {
        try {
            long handle = Minecraft.getInstance().getWindow().getWindow();
            GLFW.glfwSetDropCallback(handle, CALLBACK);
        } catch (Throwable throwable) {
            BuildCapture.LOGGER.warn("Failed to install the drag-and-drop handler: {}",
                    throwable.toString());
        }
    }
}
