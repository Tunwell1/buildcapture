package com.tunwell.buildcapture.client;

import net.minecraft.util.math.BlockPos;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import javax.annotation.Nullable;

/**
 * The capture corners currently known to the client, used for rendering and
 * interactive drags.
 */
@OnlyIn(Dist.CLIENT)
public final class ClientCaptureBox {
    @Nullable
    private static BlockPos first;
    @Nullable
    private static BlockPos second;

    private ClientCaptureBox() {
    }

    public static void set(BlockPos first, BlockPos second) {
        ClientCaptureBox.first = first;
        ClientCaptureBox.second = second;
    }

    /** Replaces the corners. */
    public static void apply(BlockPos first, BlockPos second) {
        ClientCaptureBox.first = first;
        ClientCaptureBox.second = second;
    }

    public static void clear() {
        first = null;
        second = null;
    }

    @Nullable
    public static BlockPos first() {
        return first;
    }

    @Nullable
    public static BlockPos second() {
        return second;
    }

    /** Normalised min/max corners, or {@code null} when the box is not visible. */
    @Nullable
    public static Bounds bounds() {
        if (first == null || second == null) {
            return null;
        }
        Bounds bounds = new Bounds();
        bounds.min[0] = Math.min(first.getX(), second.getX());
        bounds.min[1] = Math.min(first.getY(), second.getY());
        bounds.min[2] = Math.min(first.getZ(), second.getZ());
        bounds.max[0] = Math.max(first.getX(), second.getX());
        bounds.max[1] = Math.max(first.getY(), second.getY());
        bounds.max[2] = Math.max(first.getZ(), second.getZ());
        return bounds;
    }

    public static final class Bounds {
        public final int[] min = new int[3];
        public final int[] max = new int[3];

        public BlockPos minPos() {
            return new BlockPos(min[0], min[1], min[2]);
        }

        public BlockPos maxPos() {
            return new BlockPos(max[0], max[1], max[2]);
        }

        public int size(int axis) {
            return max[axis] - min[axis] + 1;
        }
    }
}
