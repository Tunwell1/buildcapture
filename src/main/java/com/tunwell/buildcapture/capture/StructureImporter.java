package com.tunwell.buildcapture.capture;

import com.tunwell.buildcapture.BuildCapture;
import com.tunwell.buildcapture.config.BuildCaptureConfig;
import com.tunwell.buildcapture.template.TemplateExport;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.ListNBT;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Util;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Receives an .nbt file from the client in chunks, validates it, and writes it
 * to both the world datapack and the shared library. The server never trusts
 * the client: the file has to parse as a structure and fit the configured
 * limits before anything is written.
 */
@Mod.EventBusSubscriber(modid = BuildCapture.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class StructureImporter {
    /** Chunk payload size; the serverbound packet limit of 1.16.5 is 32 KiB. */
    public static final int CHUNK_SIZE = 22_000;
    private static final int MAX_SIDE = 512;
    /** DataVersion of 1.16.5; anything newer may contain unknown blocks. */
    private static final int DATA_VERSION_1_16_5 = 2586;

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    private StructureImporter() {
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        SESSIONS.remove(event.getPlayer().getUUID());
    }

    public static void start(ServerPlayerEntity player, String rawName, int total) {
        int limit = BuildCaptureConfig.CAPTURE_IMPORT_MAX_MB.get() * 1024 * 1024;
        if (total <= 0 || total > limit) {
            fail(player, "buildcapture.capture.import_too_large",
                    BuildCaptureConfig.CAPTURE_IMPORT_MAX_MB.get());
            return;
        }
        SESSIONS.put(player.getUUID(), new Session(sanitize(rawName), total));
    }

    public static void chunk(ServerPlayerEntity player, int index, byte[] data) {
        Session session = SESSIONS.get(player.getUUID());
        if (session == null) {
            return;
        }
        if (index != session.index || data.length == 0
                || session.bytes.size() + data.length > session.total) {
            SESSIONS.remove(player.getUUID());
            fail(player, "buildcapture.capture.import_failed");
            return;
        }
        session.index++;
        session.bytes.write(data, 0, data.length);
    }

    public static void finish(ServerPlayerEntity player) {
        Session session = SESSIONS.remove(player.getUUID());
        if (session == null) {
            return;
        }
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        byte[] data = session.bytes.toByteArray();
        if (data.length != session.total) {
            fail(player, "buildcapture.capture.import_failed");
            return;
        }

        CompoundNBT nbt;
        try {
            nbt = CompressedStreamTools.readCompressed(new ByteArrayInputStream(data));
        } catch (Exception exception) {
            fail(player, "buildcapture.capture.import_not_nbt");
            return;
        }

        ListNBT size = nbt.getList("size", 3);
        if (size.size() != 3 || !nbt.contains("palette") || !nbt.contains("blocks")) {
            fail(player, "buildcapture.capture.import_not_nbt");
            return;
        }
        int x = size.getInt(0);
        int y = size.getInt(1);
        int z = size.getInt(2);
        if (x < 1 || y < 1 || z < 1 || x > MAX_SIDE || y > MAX_SIDE || z > MAX_SIDE) {
            fail(player, "buildcapture.capture.import_bad_size", x, y, z, MAX_SIDE);
            return;
        }

        ResourceLocation id = uniqueId(server, session.name);
        if (!CaptureExporter.writePackMeta(server)) {
            fail(player, "buildcapture.capture.import_failed");
            return;
        }

        Path worldFile = CaptureExporter.datapackPath(server, id);
        CaptureBackups.backup(server, id, worldFile);
        if (!TemplateExport.writeNbt(worldFile, nbt)) {
            fail(player, "buildcapture.capture.import_failed");
            return;
        }
        TemplateExport.updateCache(server, id, nbt);

        Path libraryFile = StructureLibrary.write(id, nbt);
        CaptureSelection.remember(player, id, worldFile, null, libraryFile, null);
        player.sendMessage(new TranslationTextComponent(
                "buildcapture.capture.import_done", id.toString(), x, y, z), Util.NIL_UUID);
        if (libraryFile != null) {
            player.sendMessage(new TranslationTextComponent(
                    "buildcapture.capture.library_saved",
                    StructureLibrary.describe(libraryFile)), Util.NIL_UUID);
        }

        int dataVersion = nbt.contains("DataVersion") ? nbt.getInt("DataVersion") : -1;
        if (dataVersion > DATA_VERSION_1_16_5) {
            player.sendMessage(new TranslationTextComponent(
                    "buildcapture.capture.import_newer", dataVersion), Util.NIL_UUID);
        }
        StructureActions.sendList(player);
    }

    public static void abort(ServerPlayerEntity player) {
        SESSIONS.remove(player.getUUID());
    }

    /**
     * Turns a file name into an id that does not overwrite an existing capture,
     * so importing the same file twice keeps both copies.
     */
    private static ResourceLocation uniqueId(MinecraftServer server, String name) {
        String base = name.isEmpty() ? "imported" : name;
        for (int attempt = 1; attempt < 100; attempt++) {
            String path = attempt == 1 ? base : base + "_" + attempt;
            ResourceLocation id = ResourceLocation.tryParse(BuildCapture.MOD_ID + ":" + path);
            if (id == null) {
                continue;
            }
            Path worldFile = CaptureExporter.datapackPath(server, id);
            Path libraryFile = StructureLibrary.path(id);
            boolean taken = Files.exists(worldFile)
                    || (libraryFile != null && Files.exists(libraryFile));
            if (!taken) {
                return id;
            }
        }
        return new ResourceLocation(BuildCapture.MOD_ID, base + "_" + System.currentTimeMillis());
    }

    private static String sanitize(String rawName) {
        String name = rawName == null ? "" : rawName.trim().toLowerCase(Locale.ROOT);
        if (name.endsWith(".nbt")) {
            name = name.substring(0, name.length() - 4);
        }
        StringBuilder builder = new StringBuilder(name.length());
        for (int index = 0; index < name.length() && builder.length() < 60; index++) {
            char character = name.charAt(index);
            boolean valid = character >= 'a' && character <= 'z'
                    || character >= '0' && character <= '9'
                    || character == '_' || character == '-' || character == '.'
                    || character == '/' || character == ' ';
            builder.append(valid && character != ' ' ? character : '_');
        }
        while (builder.length() > 0 && builder.charAt(0) == '_') {
            builder.deleteCharAt(0);
        }
        while (builder.length() > 0 && builder.charAt(builder.length() - 1) == '_') {
            builder.deleteCharAt(builder.length() - 1);
        }
        return builder.toString();
    }

    private static void fail(ServerPlayerEntity player, String key, Object... arguments) {
        player.sendMessage(new TranslationTextComponent(key, arguments), Util.NIL_UUID);
    }

    private static final class Session {
        private final String name;
        private final int total;
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private int index;

        private Session(String name, int total) {
            this.name = name;
            this.total = total;
        }
    }
}
