package com.tunwell.buildcapture.template;

import com.tunwell.buildcapture.BuildCapture;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.gen.feature.template.Template;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.server.FMLServerStoppedEvent;

import javax.annotation.Nullable;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Template loading cache shared by capture, placement and undo. A saved
 * structure is kept in memory until the world is reloaded, so
 * {@code /capture place} works immediately after {@code /capture save} without
 * waiting for a datapack reload.
 */
@Mod.EventBusSubscriber(modid = BuildCapture.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TemplateExport {
    private static final Map<ResourceLocation, CompoundNBT> SAVED = new ConcurrentHashMap<>();

    private TemplateExport() {
    }

    /** Drops every in-memory copy so the next lookup reads from disk again. */
    public static void clearCache() {
        SAVED.clear();
    }

    /**
     * A stopped server means a different world may be loaded next (singleplayer
     * world switching), so cached captures must not leak across worlds.
     */
    @SubscribeEvent
    public static void onServerStopped(FMLServerStoppedEvent event) {
        clearCache();
    }

    /**
     * Loads a structure from the world or enabled datapacks. Returns {@code null}
     * when it does not exist; callers fall back to the shared library.
     */
    @Nullable
    public static Template load(MinecraftServer server, ResourceLocation id) {
        CompoundNBT saved = SAVED.get(id);
        if (saved != null) {
            try {
                return server.getStructureManager().readStructure(saved.copy());
            } catch (Exception exception) {
                BuildCapture.LOGGER.warn("Failed to read cached structure {}", id, exception);
            }
        }

        // StructureManager#get reads world/generated first and falls back to the
        // resource manager, which includes world datapacks.
        try {
            Template cached = server.getStructureManager().get(id);
            if (cached != null && !cached.getSize().equals(BlockPos.ZERO)) {
                return cached;
            }
        } catch (Exception exception) {
            BuildCapture.LOGGER.warn("Failed to load structure {} from the world or datapacks", id, exception);
        }
        return null;
    }

    public static int countJigsaws(ServerWorld world, BlockPos origin, BlockPos size) {
        return countBlocks(world, origin, size, Blocks.JIGSAW);
    }

    public static int countBlocks(ServerWorld world, BlockPos origin, BlockPos size, Block block) {
        BlockPos end = origin.offset(size).offset(-1, -1, -1);
        int count = 0;
        for (BlockPos pos : BlockPos.betweenClosed(origin, end)) {
            if (world.getBlockState(pos).getBlock() == block) {
                count++;
            }
        }
        return count;
    }

    public static void updateCache(MinecraftServer server, ResourceLocation id, CompoundNBT nbt) {
        try {
            Template template = server.getStructureManager().getOrCreate(id);
            template.load(nbt);
            SAVED.put(id, nbt.copy());
        } catch (Exception exception) {
            BuildCapture.LOGGER.warn("Failed to update cached template {}", id, exception);
        }
    }

    /** Drops every in-memory copy so the next lookup reads from disk again. */
    public static void forget(MinecraftServer server, ResourceLocation id) {
        SAVED.remove(id);
        server.getStructureManager().remove(id);
    }

    public static boolean writeNbt(Path path, CompoundNBT nbt) {
        Path target = path.toAbsolutePath();
        Path parent = target.getParent();
        Path temp = null;
        try {
            if (parent != null) {
                Files.createDirectories(parent);
            }
            temp = Files.createTempFile(parent, target.getFileName().toString(), ".tmp");
            try (OutputStream out = Files.newOutputStream(temp)) {
                CompressedStreamTools.writeCompressed(nbt, out);
            }
            try {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (Exception exception) {
            BuildCapture.LOGGER.error("Failed to write template to {}", path, exception);
            return false;
        } finally {
            if (temp != null) {
                try {
                    Files.deleteIfExists(temp);
                } catch (Exception ignored) {
                    // The rename already happened; a leftover temp file is harmless.
                }
            }
        }
    }
}
