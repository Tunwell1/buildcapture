package com.tunwell.buildcapture.capture;

import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;

/**
 * Applies selection bounds sent by the client while a face is being dragged.
 * The client already clamps to the same rules; this is the authoritative copy,
 * so a stale client or a paused server cannot produce an invalid box.
 */
public final class CaptureBounds {
    private CaptureBounds() {
    }

    /**
     * @param axis the dragged axis (0 = X, 1 = Y, 2 = Z), or -1 for a translation
     * @param side +1 for the max face, -1 for the min face, 0 for a translation
     * @return true when the selection was updated
     */
    public static boolean apply(ServerPlayerEntity player, BlockPos rawMin, BlockPos rawMax,
                                int axis, int side) {
        if (rawMin == null || rawMax == null) {
            return false;
        }
        if (axis < -1 || axis > 2 || (axis == -1 && side != 0) || side < -1 || side > 1) {
            return false;
        }

        int top = player.getLevel().dimensionType().logicalHeight() - 1;
        int[] min = {
                Math.min(rawMin.getX(), rawMax.getX()),
                Math.min(rawMin.getY(), rawMax.getY()),
                Math.min(rawMin.getZ(), rawMax.getZ())
        };
        int[] max = {
                Math.max(rawMin.getX(), rawMax.getX()),
                Math.max(rawMin.getY(), rawMax.getY()),
                Math.max(rawMin.getZ(), rawMax.getZ())
        };

        if (side == 0) {
            // Moving must not change the size, so clamp the whole box instead of
            // its individual corners.
            int sizeY = max[1] - min[1] + 1;
            if (min[1] < 0) {
                max[1] -= min[1];
                min[1] = 0;
            }
            if (max[1] > top) {
                min[1] -= max[1] - top;
                max[1] = top;
            }
            if (min[1] < 0 || max[1] - min[1] + 1 != sizeY) {
                return false;
            }
        } else {
            if (axis == 1) {
                if (side > 0) {
                    max[1] = Math.min(max[1], top);
                } else {
                    min[1] = Math.max(min[1], 0);
                }
            }
            // A face drag on X or Z does not otherwise touch the vertical
            // extent, so keep a straight client from storing an invalid one.
            min[1] = Math.max(min[1], 0);
            max[1] = Math.min(max[1], top);
            if (max[1] < min[1]) {
                return false;
            }
            if (max[axis] < min[axis]) {
                if (side > 0) {
                    max[axis] = min[axis];
                } else {
                    min[axis] = max[axis];
                }
            }
        }

        CaptureSelection.setBounds(player,
                new BlockPos(min[0], min[1], min[2]),
                new BlockPos(max[0], max[1], max[2]));
        return true;
    }
}
