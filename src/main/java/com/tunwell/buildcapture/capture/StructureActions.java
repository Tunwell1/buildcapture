package com.tunwell.buildcapture.capture;

import com.tunwell.buildcapture.BuildCapture;
import com.tunwell.buildcapture.network.ModNetwork;
import com.tunwell.buildcapture.network.StructureListPacket;
import com.tunwell.buildcapture.template.TemplateExport;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.ResourceLocation;
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

        PlacementSettings settings = new PlacementSettings()
                .setRotation(rotation(rotationSteps))
                .setIgnoreEntities(false)
                .setKnownShape(true);
        template.placeInWorld(world, origin, settings, world.random);
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
