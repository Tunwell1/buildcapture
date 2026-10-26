package com.tunwell.buildcapture.capture;

import com.tunwell.buildcapture.BuildCapture;
import com.tunwell.buildcapture.config.BuildCaptureConfig;
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
import java.nio.file.Path;
import java.util.List;

/**
 * Server side of the in-game browser: lists captures, places one at an explicit
 * position and deletes one. Keeping this out of the packet class lets the
 * command layer reuse the same checks.
 */
public final class StructureActions {
    private static final double MAX_PLACE_DISTANCE = 256.0D;

    /**
     * Hard upper bound on the blocks sent for a ghost. The config value is
     * clamped to this so a modified config cannot exceed the 1 MiB clientbound
     * payload limit.
     */
    private static final int MAX_GHOST_BLOCKS = 80_000;
    private static final int MAX_GHOST_PALETTE = 1_536;
    /** Positions are packed into 10 bits per axis in {@link PreviewDataPacket}. */
    private static final int MAX_GHOST_AXIS = 1_024;
    private static final BlockState[] NO_STATES = new BlockState[0];
    private static final int[] NO_POSITIONS = new int[0];

    private StructureActions() {
    }

    public static void sendList(ServerPlayerEntity player) {
        if (player.getServer() == null) {
            return;
        }
        List<StructureCatalog.Entry> entries = StructureCatalog.scan(player.getServer());
        Path root = StructureLibrary.root();
        String libraryPath = root == null ? "" : root.toAbsolutePath().toString();
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new StructureListPacket(entries, libraryPath,
                        CaptureSelection.last(player) != null));
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
        if (id == null || server == null || !validId(id)) {
            return;
        }
        Template template = TemplateExport.load(server, id);
        if (template == null || template.getSize().equals(BlockPos.ZERO)) {
            template = StructureLibrary.load(server, id);
        }
        if (template == null || template.getSize().equals(BlockPos.ZERO)) {
            fail(player, "buildcapture.capture.not_found", id.toString());
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
        if (sizeX > MAX_GHOST_AXIS || sizeY > MAX_GHOST_AXIS || sizeZ > MAX_GHOST_AXIS
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
            positions[cursor] = PreviewDataPacket.pack(x, y, z);
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

    /** Rejects traversal segments before an id is turned into a file path. */
    private static boolean validId(ResourceLocation id) {
        String namespace = id.getNamespace();
        if (namespace.isEmpty() || ".".equals(namespace) || "..".equals(namespace)) {
            return false;
        }
        for (String segment : id.getPath().split("/")) {
            if (segment.isEmpty() || ".".equals(segment) || "..".equals(segment)) {
                return false;
            }
        }
        return true;
    }

    public static void place(ServerPlayerEntity player, @Nullable ResourceLocation id,
                             int rotationSteps, BlockPos origin) {
        if (id == null) {
            fail(player, "buildcapture.capture.invalid_name");
            return;
        }
        Template template = TemplateExport.load(player.getServer(), id);
        if (template == null || template.getSize().equals(BlockPos.ZERO)) {
            template = StructureLibrary.load(player.getServer(), id);
        }
        if (template == null || template.getSize().equals(BlockPos.ZERO)) {
            fail(player, "buildcapture.capture.not_found", id.toString());
            return;
        }

        ServerWorld world = player.getLevel();
        if (player.blockPosition().distSqr(origin) > MAX_PLACE_DISTANCE * MAX_PLACE_DISTANCE) {
            fail(player, "buildcapture.capture.too_far");
            return;
        }
        BlockPos size = template.getSize();
        if (origin.getY() < 0 || origin.getY() + size.getY() > world.dimensionType().logicalHeight()) {
            fail(player, "buildcapture.capture.out_of_world");
            return;
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
        player.sendMessage(new TranslationTextComponent(
                "buildcapture.capture.placed", id.toString(),
                origin.getX(), origin.getY(), origin.getZ(), rotationSteps * 90), Util.NIL_UUID);
    }

    public static void delete(ServerPlayerEntity player, @Nullable ResourceLocation id) {
        if (id == null || player.getServer() == null) {
            return;
        }
        boolean removed = false;

        Path worldFile = CaptureExporter.datapackPath(player.getServer(), id);
        Path worldBackup = null;
        if (worldFile.toFile().exists()) {
            worldBackup = CaptureBackups.backup(player.getServer(), id, worldFile);
            removed |= CaptureBackups.delete(worldFile);
        }

        Path libraryFile = StructureLibrary.path(id);
        Path libraryBackup = null;
        if (libraryFile != null && libraryFile.toFile().exists()) {
            libraryBackup = CaptureBackups.backup(player.getServer(), id, libraryFile, "library");
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
