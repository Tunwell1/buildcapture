package com.tunwell.buildcapture.client;

import com.tunwell.buildcapture.capture.StructureCatalog;
import com.tunwell.buildcapture.network.ModNetwork;
import com.tunwell.buildcapture.network.StructureActionPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Direction;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import javax.annotation.Nullable;

/**
 * "Ghost" placement: the structure is not placed until the player confirms, so
 * the exact spot can be adjusted first. The ghost is drawn as a box of the
 * structure's footprint; the server still validates the position and the id.
 */
@OnlyIn(Dist.CLIENT)
public final class PlacementPreview {
    @Nullable
    private static String id;
    @Nullable
    private static BlockPos origin;
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
        sizeX = Math.max(1, size.getX());
        sizeY = Math.max(1, size.getY());
        sizeZ = Math.max(1, size.getZ());
        rotationSteps = Math.floorMod(rotation, 4);
        origin = defaultOrigin(minecraft);
        clamp();
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

    public static void rotate() {
        rotationSteps = (rotationSteps + 1) % 4;
        clamp();
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
        rotationSteps = 0;
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
