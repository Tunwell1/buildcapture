package com.tunwell.buildcapture.capture;

import com.tunwell.buildcapture.BuildCapture;
import com.tunwell.buildcapture.config.BuildCaptureConfig;
import com.tunwell.buildcapture.core.PreviewPacking;
import com.tunwell.buildcapture.network.ModNetwork;
import com.tunwell.buildcapture.network.PreviewDataPacket;
import com.tunwell.buildcapture.network.StructureListPacket;
import com.tunwell.buildcapture.template.TemplateExport;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.nbt.NBTUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Mirror;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Rotation;
import net.minecraft.util.Util;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.gen.feature.template.PlacementSettings;
import net.minecraft.world.gen.feature.template.Template;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.fml.network.PacketDistributor;

import javax.annotation.Nullable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Server side of the in-game browser: lists captures, places one at an explicit
 * position and deletes one. Keeping this out of the packet class lets the
 * command layer reuse the same checks.
 */
public final class StructureActions {
    private static final double MAX_PLACE_DISTANCE = 256.0D;

    /**
     * The block list is sent in a single packet, and the protocol rejects
     * frames above 2 MiB, so 150,000 blocks with a large palette is the safe
     * ceiling. Rendering is cached, so this is a transfer limit, not a speed one.
     */
    private static final int MAX_GHOST_BLOCKS = 150_000;
    private static final int MAX_GHOST_PALETTE = 1_536;
    private static final BlockState[] NO_STATES = new BlockState[0];
    private static final int[] NO_POSITIONS = new int[0];

    /**
     * Walking the datapack and the library reads a header per structure, which
     * is far too slow to do on the tick thread when a server has hundreds of
     * captures. One worker thread scans, one list request per player at a time.
     */
    private static final ExecutorService SCAN_EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "buildcapture-catalog");
        thread.setDaemon(true);
        return thread;
    });
    private static final Set<UUID> SCAN_QUEUED = ConcurrentHashMap.newKeySet();

    private StructureActions() {
    }

    public static void sendList(ServerPlayerEntity player) {
        MinecraftServer server = player.getServer();
        if (server == null || !SCAN_QUEUED.add(player.getUUID())) {
            return;
        }
        UUID uuid = player.getUUID();
        SCAN_EXECUTOR.execute(() -> {
            SCAN_QUEUED.remove(uuid);
            List<StructureCatalog.Entry> entries;
            try {
                entries = StructureCatalog.scan(server);
            } catch (Exception exception) {
                BuildCapture.LOGGER.warn("Failed to scan the structure catalog", exception);
                return;
            }
            Path root = StructureLibrary.root();
            String libraryPath = root == null ? "" : root.toAbsolutePath().toString();
            List<StructureCatalog.Entry> result = entries;
            server.execute(() -> {
                ServerPlayerEntity current = server.getPlayerList().getPlayer(uuid);
                if (current == null) {
                    return;
                }
                ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> current),
                        new StructureListPacket(result, libraryPath,
                                CaptureSelection.last(current) != null));
            });
        });
    }

    /**
     * Sends the block list of one structure for the placement ghost. The
     * template is loaded through the same cache and datafixer path as
     * {@link #place}, so the ghost matches what will actually be placed. Very
     * large structures still get a reply, but with {@code limited} set so the
     * client keeps the footprint box.
     */
    public static void sendPreview(ServerPlayerEntity player, @Nullable ResourceLocation id) {
        MinecraftServer server = player.getServer();
        if (id == null || server == null || !CaptureExporter.isSafeId(id)) {
            return;
        }
        Template template = TemplateExport.load(server, id);
        if (template == null || template.getSize().equals(BlockPos.ZERO)) {
            template = StructureLibrary.load(server, id);
        }
        if (template == null || template.getSize().equals(BlockPos.ZERO)) {
            fail(player, stored(server, id)
                    ? "buildcapture.capture.unreadable" : "buildcapture.capture.not_found",
                    id.toString());
            return;
        }

        BlockPos templateSize = template.getSize();
        int sizeX = templateSize.getX();
        int sizeY = templateSize.getY();
        int sizeZ = templateSize.getZ();
        if (sizeX <= 0 || sizeY <= 0 || sizeZ <= 0) {
            return;
        }

        CompoundNBT nbt = template.save(new CompoundNBT());
        ListNBT paletteList = nbt.getList("palette", 10);
        ListNBT blocksList = nbt.getList("blocks", 10);
        if (paletteList.isEmpty() || blocksList.isEmpty()) {
            sendPreviewData(player, id, sizeX, sizeY, sizeZ, 0, false,
                    NO_STATES, NO_POSITIONS, NO_POSITIONS);
            return;
        }
        if (sizeX > PreviewPacking.MAX_AXIS || sizeY > PreviewPacking.MAX_AXIS
                || sizeZ > PreviewPacking.MAX_AXIS
                || paletteList.size() > MAX_GHOST_PALETTE) {
            sendPreviewData(player, id, sizeX, sizeY, sizeZ, blocksList.size(), true,
                    NO_STATES, NO_POSITIONS, NO_POSITIONS);
            return;
        }

        BlockState[] palette = new BlockState[paletteList.size()];
        for (int index = 0; index < palette.length; index++) {
            try {
                palette[index] = NBTUtil.readBlockState(paletteList.getCompound(index));
            } catch (Exception exception) {
                palette[index] = Blocks.AIR.defaultBlockState();
            }
        }

        int total = countGhostBlocks(palette, blocksList, sizeX, sizeY, sizeZ);
        int maxBlocks = Math.min(MAX_GHOST_BLOCKS,
                BuildCaptureConfig.CAPTURE_PREVIEW_MAX_BLOCKS.get());
        if (total > maxBlocks) {
            sendPreviewData(player, id, sizeX, sizeY, sizeZ, total, true,
                    NO_STATES, NO_POSITIONS, NO_POSITIONS);
            return;
        }

        int[] positions = new int[total];
        int[] indexes = new int[total];
        int cursor = 0;
        for (int index = 0; index < blocksList.size() && cursor < total; index++) {
            CompoundNBT block = blocksList.getCompound(index);
            int stateIndex = block.getInt("state");
            if (stateIndex < 0 || stateIndex >= palette.length) {
                continue;
            }
            BlockState state = palette[stateIndex];
            if (state.isAir() || state.getBlock() == Blocks.STRUCTURE_VOID) {
                continue;
            }
            ListNBT posList = block.getList("pos", 3);
            if (posList.size() != 3) {
                continue;
            }
            int x = posList.getInt(0);
            int y = posList.getInt(1);
            int z = posList.getInt(2);
            if (x < 0 || y < 0 || z < 0 || x >= sizeX || y >= sizeY || z >= sizeZ) {
                continue;
            }
            positions[cursor] = PreviewPacking.pack(x, y, z);
            indexes[cursor] = stateIndex;
            cursor++;
        }
        if (cursor != total) {
            // Malformed entries changed between the two passes; fall back safely.
            sendPreviewData(player, id, sizeX, sizeY, sizeZ, total, true,
                    NO_STATES, NO_POSITIONS, NO_POSITIONS);
            return;
        }
        sendPreviewData(player, id, sizeX, sizeY, sizeZ, total, false,
                palette, positions, indexes);
    }

    private static int countGhostBlocks(BlockState[] palette, ListNBT blocksList,
                                        int sizeX, int sizeY, int sizeZ) {
        int count = 0;
        for (int index = 0; index < blocksList.size(); index++) {
            CompoundNBT block = blocksList.getCompound(index);
            int stateIndex = block.getInt("state");
            if (stateIndex < 0 || stateIndex >= palette.length) {
                continue;
            }
            BlockState state = palette[stateIndex];
            if (state.isAir() || state.getBlock() == Blocks.STRUCTURE_VOID) {
                continue;
            }
            ListNBT posList = block.getList("pos", 3);
            if (posList.size() != 3) {
                continue;
            }
            int x = posList.getInt(0);
            int y = posList.getInt(1);
            int z = posList.getInt(2);
            if (x < 0 || y < 0 || z < 0 || x >= sizeX || y >= sizeY || z >= sizeZ) {
                continue;
            }
            count++;
        }
        return count;
    }

    private static void sendPreviewData(ServerPlayerEntity player, ResourceLocation id,
                                        int sizeX, int sizeY, int sizeZ, int total,
                                        boolean limited, BlockState[] palette,
                                        int[] positions, int[] indexes) {
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new PreviewDataPacket(id.toString(), sizeX, sizeY, sizeZ, total, limited,
                        palette, positions, indexes));
    }

    public enum PlaceStatus {
        PLACED,
        INVALID,
        NOT_FOUND,
        /** The file is there but the template loader rejected it. */
        UNREADABLE,
        TOO_FAR,
        OUT_OF_WORLD
    }

    /** True when a file exists for this id, whatever its contents are. */
    private static boolean stored(@Nullable MinecraftServer server, ResourceLocation id) {
        if (server == null) {
            return false;
        }
        Path world = CaptureExporter.datapackPath(server, id);
        Path library = StructureLibrary.path(id);
        return (world != null && Files.isRegularFile(world))
                || (library != null && Files.isRegularFile(library));
    }

    /**
     * Checks and performs a placement. Both the command and the browser go
     * through here so the distance, build-height and rotation rules cannot
     * drift apart; the caller owns the wording of the result.
     */
    public static PlaceStatus place(ServerPlayerEntity player, @Nullable ResourceLocation id,
                                    int rotationSteps, BlockPos origin) {
        if (id == null || !CaptureExporter.isSafeId(id)) {
            return PlaceStatus.INVALID;
        }
        Template template = TemplateExport.load(player.getServer(), id);
        if (template == null || template.getSize().equals(BlockPos.ZERO)) {
            template = StructureLibrary.load(player.getServer(), id);
        }
        if (template == null || template.getSize().equals(BlockPos.ZERO)) {
            return stored(player.getServer(), id) ? PlaceStatus.UNREADABLE : PlaceStatus.NOT_FOUND;
        }

        ServerWorld world = player.getLevel();
        if (player.blockPosition().distSqr(origin) > MAX_PLACE_DISTANCE * MAX_PLACE_DISTANCE) {
            return PlaceStatus.TOO_FAR;
        }
        BlockPos size = template.getSize();
        if (origin.getY() < 0
                || (long) origin.getY() + size.getY() > world.dimensionType().logicalHeight()) {
            return PlaceStatus.OUT_OF_WORLD;
        }

        // Template rotation happens around the world origin, so the corner has to
        // be moved first or a rotated structure lands behind the previewed box.
        Rotation rotation = rotation(rotationSteps);
        BlockPos placementOrigin = Template.getZeroPositionWithTransform(
                origin, Mirror.NONE, rotation, size.getX(), size.getZ());
        PlacementSettings settings = new PlacementSettings()
                .setRotation(rotation)
                .setIgnoreEntities(false)
                .setKnownShape(true);
        template.placeInWorld(world, placementOrigin, settings, world.random);
        return PlaceStatus.PLACED;
    }

    public static void delete(ServerPlayerEntity player, @Nullable ResourceLocation id) {
        if (id == null || !CaptureExporter.isSafeId(id) || player.getServer() == null) {
            fail(player, "buildcapture.capture.invalid_name");
            return;
        }
        Path worldFile = CaptureExporter.datapackPath(player.getServer(), id);
        Path libraryFile = StructureLibrary.path(id);
        boolean worldExists = worldFile != null && Files.exists(worldFile);
        boolean libraryExists = libraryFile != null && Files.exists(libraryFile);
        if (!worldExists && !libraryExists) {
            fail(player, "buildcapture.capture.not_found", id.toString());
            return;
        }

        // Back up everything before deleting anything: a failed backup must not
        // leave a half-deleted capture that undo cannot restore.
        Path worldBackup = worldExists
                ? CaptureBackups.backup(player.getServer(), id, worldFile)
                : null;
        if (worldExists && worldBackup == null) {
            fail(player, "buildcapture.capture.backup_failed", id.toString());
            return;
        }
        Path libraryBackup = libraryExists
                ? CaptureBackups.backup(player.getServer(), id, libraryFile, "library")
                : null;
        if (libraryExists && libraryBackup == null) {
            fail(player, "buildcapture.capture.backup_failed", id.toString());
            return;
        }

        boolean removed = false;
        if (worldExists) {
            removed |= CaptureBackups.delete(worldFile);
        }
        if (libraryExists) {
            removed |= CaptureBackups.delete(libraryFile);
        }
        if (!removed) {
            fail(player, "buildcapture.capture.not_found", id.toString());
            return;
        }
        TemplateExport.forget(player.getServer(), id);
        CaptureSelection.remember(player, id, worldFile, worldBackup, libraryFile, libraryBackup);
        player.sendMessage(new TranslationTextComponent(
                "buildcapture.capture.deleted", id.toString()), Util.NIL_UUID);
        sendList(player);
    }

    /** Reverts the player's last export, then refreshes the browser. */
    public static void undo(ServerPlayerEntity player) {
        CaptureUndo.Result result = CaptureUndo.undo(player);
        switch (result.status()) {
            case NOTHING:
                fail(player, "buildcapture.capture.nothing_to_undo");
                return;
            case FAILED:
                fail(player, "buildcapture.capture.undo_failed", result.id());
                return;
            case RESTORED:
                player.sendMessage(new TranslationTextComponent(
                        "buildcapture.capture.undo_restored", result.id()), Util.NIL_UUID);
                break;
            case REMOVED:
            default:
                player.sendMessage(new TranslationTextComponent(
                        "buildcapture.capture.undo_removed", result.id()), Util.NIL_UUID);
                break;
        }
        sendList(player);
    }

    private static net.minecraft.util.Rotation rotation(int steps) {
        switch (Math.floorMod(steps, 4)) {
            case 1:
                return net.minecraft.util.Rotation.CLOCKWISE_90;
            case 2:
                return net.minecraft.util.Rotation.CLOCKWISE_180;
            case 3:
                return net.minecraft.util.Rotation.COUNTERCLOCKWISE_90;
            default:
                return net.minecraft.util.Rotation.NONE;
        }
    }

    private static void fail(ServerPlayerEntity player, String key, Object... arguments) {
        player.sendMessage(new TranslationTextComponent(key, arguments), Util.NIL_UUID);
        BuildCapture.LOGGER.debug("Capture GUI action failed: {}", key);
    }
}
