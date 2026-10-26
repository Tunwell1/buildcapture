package com.tunwell.buildcapture.client;

import com.tunwell.buildcapture.BuildCapture;
import com.tunwell.buildcapture.capture.StructureImporter;
import com.tunwell.buildcapture.network.ImportChunkPacket;
import com.tunwell.buildcapture.network.ImportEndPacket;
import com.tunwell.buildcapture.network.ImportStartPacket;
import com.tunwell.buildcapture.network.ModNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.awt.FileDialog;
import java.awt.Frame;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.Locale;

/**
 * Uploads .nbt files chosen in the file dialog or dropped onto the game window.
 * Files are sent in chunks because a single packet is limited to 32 KiB; the
 * server validates and stores the file only after the last chunk arrives.
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = BuildCapture.MOD_ID, value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ImportUploader {
    private static final int CHUNKS_PER_TICK = 3;
    private static final long MAX_BYTES = 64L * 1024 * 1024;

    private static final Deque<Task> QUEUE = new ArrayDeque<>();

    @Nullable
    private static Task current;

    private ImportUploader() {
    }

    /** Opens the native file dialog; runs off the render thread because it blocks. */
    public static void openFileDialog() {
        Thread thread = new Thread(() -> {
            try {
                String title = new TranslationTextComponent(
                        "buildcapture.capture.import_dialog").getString();
                FileDialog dialog = new FileDialog((Frame) null, title, FileDialog.LOAD);
                dialog.setFile("*.nbt");
                dialog.setFilenameFilter((dir, name) ->
                        name.toLowerCase(Locale.ROOT).endsWith(".nbt"));
                dialog.setAlwaysOnTop(true);
                dialog.setVisible(true);
                String file = dialog.getFile();
                String directory = dialog.getDirectory();
                dialog.dispose();
                if (file == null || directory == null) {
                    return;
                }
                enqueue(Paths.get(directory, file));
            } catch (Throwable throwable) {
                BuildCapture.LOGGER.warn("Failed to open the structure file dialog: {}",
                        throwable.toString());
                report("buildcapture.capture.import_read_failed", "?");
            }
        }, "BuildCapture-Import");
        thread.setDaemon(true);
        thread.start();
    }

    /** Queues a file; safe to call from any thread. */
    public static void enqueue(Path path) {
        Minecraft.getInstance().execute(() -> queueOnClientThread(path));
    }

    private static void queueOnClientThread(Path path) {
        String name = path.getFileName() == null ? "" : path.getFileName().toString();
        if (!name.toLowerCase(Locale.ROOT).endsWith(".nbt")) {
            report("buildcapture.capture.import_not_nbt");
            return;
        }
        byte[] data;
        try {
            long size = Files.size(path);
            if (size <= 0 || size > MAX_BYTES) {
                report("buildcapture.capture.import_too_large", MAX_BYTES / (1024 * 1024));
                return;
            }
            data = Files.readAllBytes(path);
        } catch (IOException exception) {
            BuildCapture.LOGGER.warn("Failed to read {}", path, exception);
            report("buildcapture.capture.import_read_failed", name);
            return;
        }
        synchronized (QUEUE) {
            QUEUE.add(new Task(name, data));
        }
        if (Minecraft.getInstance().screen == null) {
            CaptureScreen.open();
        }
    }

    /** 0..1 while a file is uploading, or -1 when idle. */
    public static double progress() {
        Task task = current;
        if (task == null || task.data.length == 0) {
            return -1.0D;
        }
        return (double) task.offset / (double) task.data.length;
    }

    @Nullable
    public static String currentName() {
        Task task = current;
        return task == null ? null : task.name;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (Minecraft.getInstance().getConnection() == null) {
            // Leaving the server must not keep a half-finished upload alive.
            current = null;
            synchronized (QUEUE) {
                QUEUE.clear();
            }
            return;
        }
        if (current == null) {
            synchronized (QUEUE) {
                current = QUEUE.poll();
            }
            if (current == null) {
                return;
            }
            ModNetwork.CHANNEL.sendToServer(
                    new ImportStartPacket(current.name, current.data.length));
        }

        int sent = 0;
        while (sent < CHUNKS_PER_TICK && current.offset < current.data.length) {
            int size = Math.min(StructureImporter.CHUNK_SIZE,
                    current.data.length - current.offset);
            byte[] chunk = Arrays.copyOfRange(current.data, current.offset,
                    current.offset + size);
            ModNetwork.CHANNEL.sendToServer(new ImportChunkPacket(current.index, chunk));
            current.offset += size;
            current.index++;
            sent++;
        }
        if (current.offset >= current.data.length) {
            ModNetwork.CHANNEL.sendToServer(new ImportEndPacket());
            current = null;
        }
    }

    private static void report(String key, Object... arguments) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            minecraft.player.displayClientMessage(new TranslationTextComponent(key, arguments),
                    false);
        }
    }

    private static final class Task {
        private final String name;
        private final byte[] data;
        private int offset;
        private int index;

        private Task(String name, byte[] data) {
            this.name = name;
            this.data = data;
        }
    }
}
