package com.tunwell.buildcapture.client;

import com.tunwell.buildcapture.capture.StructureCatalog;
import com.tunwell.buildcapture.network.ModNetwork;
import com.tunwell.buildcapture.network.PreviewRequestPacket;
import com.tunwell.buildcapture.network.StructureActionPacket;
import net.minecraft.block.BlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Direction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import javax.annotation.Nullable;

/**
 * "Ghost" placement: the structure is not placed until the player confirms, so
 * the exact spot can be adjusted first. The server sends the block list once
 * the preview starts, and the client draws it as translucent block models; the
 * server still validates the position and the id.
 */
@OnlyIn(Dist.CLIENT)
public final class PlacementPreview {
    @Nullable
    private static String id;
    @Nullable
    private static BlockPos origin;
    @Nullable
    private static GhostStructure ghost;
    private static int sizeX;
    private static int sizeY;
    private static int sizeZ;
    private static int rotationSteps;

    private PlacementPreview() {
    }

    public static boolean active() {
        return id != null && origin != null;
    }

    @Nullable
    public static String id() {
        return id;
    }

    @Nullable
    public static BlockPos origin() {
        return origin;
    }

    public static int rotationSteps() {
        return rotationSteps;
    }

    /** Block list from the server, or {@code null} while loading or when it is too large. */
    @Nullable
    public static GhostStructure ghost() {
        return ghost;
    }

    /** Footprint after rotation, as {x, y, z}. */
    public static int[] size() {
        if (rotationSteps % 2 == 0) {
            return new int[]{sizeX, sizeY, sizeZ};
        }
        return new int[]{sizeZ, sizeY, sizeX};
    }

    public static void start(StructureCatalog.Entry entry, int rotation) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        BlockPos size = entry.size();
        id = entry.id().toString();
        ghost = null;
        sizeX = Math.max(1, size.getX());
        sizeY = Math.max(1, size.getY());
        sizeZ = Math.max(1, size.getZ());
        rotationSteps = Math.floorMod(rotation, 4);
        origin = defaultOrigin(minecraft);
        clamp();
        ModNetwork.CHANNEL.sendToServer(new PreviewRequestPacket(id));
    }

    /** One block in front of the player, sitting on the block they stand on. */
    private static BlockPos defaultOrigin(Minecraft minecraft) {
        if (minecraft.player == null) {
            return BlockPos.ZERO;
        }
        Direction facing = minecraft.player.getDirection();
        return minecraft.player.blockPosition().relative(facing, 2).below();
    }

    public static void reset() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        origin = defaultOrigin(minecraft);
        clamp();
    }

    public static void move(int dx, int dy, int dz) {
        if (!active() || origin == null) {
            return;
        }
        origin = origin.offset(dx, dy, dz);
        clamp();
    }

    /** Moves along the player's horizontal facing. */
    public static void moveForward(int blocks) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        Direction facing = minecraft.player.getDirection();
        move(facing.getStepX() * blocks, 0, facing.getStepZ() * blocks);
    }

    /** Moves to the player's right, relative to where they look. */
    public static void moveSideways(int blocks) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        Direction facing = minecraft.player.getDirection();
        move(-facing.getStepZ() * blocks, 0, facing.getStepX() * blocks);
    }

    /**
     * Turns the preview by a number of quarter turns. Positive is the same
     * direction as the {@code R} key; negative comes from scrolling the other
     * way, so the wheel can be used as a dial instead of only paging forward.
     */
    public static void rotate(int steps) {
        if (!active() || steps == 0) {
            return;
        }
        rotationSteps = Math.floorMod(rotationSteps + steps, 4);
        if (ghost != null) {
            ghost.rotate(rotationSteps);
        }
        clamp();
        showRotation();
    }

    /** One quarter turn, as used by {@code R}. */
    public static void rotate() {
        rotate(1);
    }

    /** Action bar feedback, so the wheel does not rotate silently. */
    private static void showRotation() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        minecraft.player.displayClientMessage(new TranslationTextComponent(
                "buildcapture.preview.rotation", rotationSteps * 90), true);
    }

    public static void confirm() {
        if (!active() || id == null || origin == null) {
            return;
        }
        ModNetwork.CHANNEL.sendToServer(
                StructureActionPacket.place(id, rotationSteps, origin));
        stop();
    }

    public static void stop() {
        id = null;
        origin = null;
        ghost = null;
        rotationSteps = 0;
    }

    /**
     * Called from the network thread's scheduled client task. A reply for an id
     * that is no longer being previewed is ignored, which also covers a fast
     * switch from one structure to another.
     */
    public static void setGhost(String ghostId, int sizeX, int sizeY, int sizeZ, int total,
                                boolean limited, BlockState[] palette, int[] positions,
                                int[] indexes) {
        if (id == null || !id.equals(ghostId)) {
            return;
        }
        if (limited) {
            ghost = null;
            showLimited(total);
            return;
        }
        if (positions.length == 0 || palette.length == 0
                || positions.length != indexes.length) {
            ghost = null;
            return;
        }
        ghost = new GhostStructure(ghostId, sizeX, sizeY, sizeZ, total,
                palette, positions, indexes);
        ghost.rotate(rotationSteps);
    }

    private static void showLimited(int total) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        String count = total > 0 ? Integer.toString(total) : "?";
        minecraft.player.displayClientMessage(new TranslationTextComponent(
                "buildcapture.preview.limited", count), false);
    }

    /** True when the footprint would leave the build height. */
    public static boolean outsideWorld() {
        if (origin == null) {
            return false;
        }
        int height = buildHeight();
        return origin.getY() < 0 || origin.getY() + size()[1] > height;
    }

    private static void clamp() {
        if (origin == null) {
            return;
        }
        int height = buildHeight();
        int top = height - size()[1];
        int y = Math.max(0, Math.min(origin.getY(), Math.max(0, top)));
        origin = new BlockPos(origin.getX(), y, origin.getZ());
    }

    private static int buildHeight() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.level == null ? 256 : minecraft.level.dimensionType().logicalHeight();
    }
}
