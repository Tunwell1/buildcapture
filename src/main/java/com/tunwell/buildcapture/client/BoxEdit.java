package com.tunwell.buildcapture.client;

import com.tunwell.buildcapture.network.CaptureBoundsPacket;
import com.tunwell.buildcapture.network.ModNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Util;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TranslationTextComponent;

import javax.annotation.Nullable;

/**
 * Mutations of the client's copy of the box. Every method sends complete bounds
 * to the server, which clamps them; the client never owns the real selection.
 */
final class BoxEdit {
    private static final long ACTION_MILLIS = 1400L;

    @Nullable
    private static String actionDirection;
    private static boolean actionResize;
    private static long actionAt;

    private BoxEdit() {
    }

    /**
     * What the last box edit did, for the heads-up display, or {@code null} when
     * nothing happened recently.
     */
    @Nullable
    static ITextComponent lastAction() {
        if (actionDirection == null || Util.getMillis() - actionAt > ACTION_MILLIS) {
            return null;
        }
        return new TranslationTextComponent(
                actionResize ? "buildcapture.hud.face" : "buildcapture.hud.moved",
                new TranslationTextComponent(actionDirection));
    }

    static void note(int axis, int sign, boolean resize) {
        if (sign == 0) {
            return;
        }
        actionDirection = "buildcapture.capture.dir." + directionName(axis, sign);
        actionResize = resize;
        actionAt = Util.getMillis();
    }

    private static String directionName(int axis, int sign) {
        if (axis == 1) {
            return sign > 0 ? "up" : "down";
        }
        if (axis == 0) {
            return sign > 0 ? "east" : "west";
        }
        return sign > 0 ? "south" : "north";
    }

    static void moveFace(int axis, int side, int coordinate) {
        ClientCaptureBox.Bounds bounds = ClientCaptureBox.bounds();
        if (bounds == null) {
            return;
        }
        int[] min = bounds.min.clone();
        int[] max = bounds.max.clone();
        int fixed = side > 0 ? min[axis] : max[axis];
        int value = coordinate;
        if (axis == 1) {
            value = side > 0 ? Math.min(value, buildTop()) : Math.max(value, 0);
        }
        if (side > 0) {
            value = Math.max(value, fixed);
        } else {
            value = Math.min(value, fixed);
        }
        if (side > 0) {
            max[axis] = value;
        } else {
            min[axis] = value;
        }
        note(axis, side, true);
        submit(min, max, axis, side);
    }

    static void translate(int axis, int step) {
        ClientCaptureBox.Bounds bounds = ClientCaptureBox.bounds();
        if (bounds == null) {
            return;
        }
        int[] min = bounds.min.clone();
        int[] max = bounds.max.clone();
        int delta = step;
        if (axis == 1) {
            if (min[1] + delta < 0) {
                delta = -min[1];
            }
            int top = buildTop();
            if (max[1] + delta > top) {
                delta = top - max[1];
            }
        }
        if (delta == 0) {
            return;
        }
        min[axis] += delta;
        max[axis] += delta;
        note(axis, delta, false);
        submit(min, max, axis, 0);
    }

    /** Slides the box by a whole-block offset from the drag's starting bounds. */
    static void moveBox(int[] startMin, int[] startMax, int dx, int dz) {
        int[] min = startMin.clone();
        int[] max = startMax.clone();
        min[0] += dx;
        max[0] += dx;
        min[2] += dz;
        max[2] += dz;
        if (Math.abs(dx) >= Math.abs(dz)) {
            note(0, dx, false);
        } else {
            note(2, dz, false);
        }
        submit(min, max, 0, 0);
    }

    private static void submit(int[] min, int[] max, int axis, int side) {
        BlockPos minPos = new BlockPos(min[0], min[1], min[2]);
        BlockPos maxPos = new BlockPos(max[0], max[1], max[2]);
        ClientCaptureBox.Bounds current = ClientCaptureBox.bounds();
        if (current != null && current.minPos().equals(minPos) && current.maxPos().equals(maxPos)) {
            return;
        }
        ClientCaptureBox.apply(minPos, maxPos);
        ModNetwork.CHANNEL.sendToServer(new CaptureBoundsPacket(minPos, maxPos, axis, side));
    }

    static int buildTop() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.level == null ? 255 : minecraft.level.dimensionType().logicalHeight() - 1;
    }
}
