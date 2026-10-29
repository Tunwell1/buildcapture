package com.tunwell.buildcapture.capture;

import com.tunwell.buildcapture.BuildCapture;
import com.tunwell.buildcapture.config.BuildCaptureConfig;
import com.tunwell.buildcapture.core.StructureNames;
import com.tunwell.buildcapture.template.TemplateExport;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.ListNBT;
import net.minecraft.nbt.NBTSizeTracker;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Util;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.gen.feature.template.Template;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.zip.GZIPInputStream;

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
    /** DataVersion of 1.16.5; anything newer may contain unknown blocks. */
    private static final int DATA_VERSION_1_16_5 = 2586;
    /**
     * Cap on the decompressed NBT, not the uploaded file: a handful of MB of
     * gzip can expand to gigabytes, and the vanilla reader checks nothing.
     */
    private static final long MAX_NBT_BYTES = 128L * 1024 * 1024;
    private static final long SESSION_TIMEOUT_MS = 120_000L;

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
        if (session.expired()) {
            SESSIONS.remove(player.getUUID());
            fail(player, "buildcapture.capture.import_timeout");
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
        if (session.expired()) {
            fail(player, "buildcapture.capture.import_timeout");
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
        try (DataInputStream input = new DataInputStream(new BufferedInputStream(
                new GZIPInputStream(new ByteArrayInputStream(data))))) {
            nbt = CompressedStreamTools.read(input, new NBTSizeTracker(MAX_NBT_BYTES));
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
        if (x < 1 || y < 1 || z < 1) {
            fail(player, "buildcapture.capture.import_bad_size", x, y, z);
            return;
        }

        // Parse through the same path that placement uses, so a file that would
        // fail to load is rejected now instead of being written and listed.
        try {
            Template template = server.getStructureManager().readStructure(nbt);
            if (template.getSize().equals(BlockPos.ZERO)) {
                fail(player, "buildcapture.capture.import_not_nbt");
                return;
            }
        } catch (Exception exception) {
            fail(player, "buildcapture.capture.import_not_nbt");
            return;
        }

        ResourceLocation id = uniqueId(server, session.name);
        Path worldFile = CaptureExporter.datapackPath(server, id);
        if (worldFile == null) {
            fail(player, "buildcapture.capture.import_failed");
            return;
        }
        if (!CaptureExporter.writePackMeta(server)) {
            fail(player, "buildcapture.capture.import_failed");
            return;
        }

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
            if (id == null || !CaptureExporter.isSafeId(id)) {
                continue;
            }
            Path worldFile = CaptureExporter.datapackPath(server, id);
            Path libraryFile = StructureLibrary.path(id);
            boolean taken = (worldFile != null && Files.exists(worldFile))
                    || (libraryFile != null && Files.exists(libraryFile));
            if (!taken) {
                return id;
            }
        }
        return new ResourceLocation(BuildCapture.MOD_ID, base + "_" + System.currentTimeMillis());
    }

    private static String sanitize(String rawName) {
        return StructureNames.sanitizeFileName(rawName);
    }

    private static void fail(ServerPlayerEntity player, String key, Object... arguments) {
        player.sendMessage(new TranslationTextComponent(key, arguments), Util.NIL_UUID);
    }

    private static final class Session {
        private final String name;
        private final int total;
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private final long startedAt = System.currentTimeMillis();
        private int index;

        private Session(String name, int total) {
            this.name = name;
            this.total = total;
        }

        private boolean expired() {
            return System.currentTimeMillis() - startedAt > SESSION_TIMEOUT_MS;
        }
    }
}
