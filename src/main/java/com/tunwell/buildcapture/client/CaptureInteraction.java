package com.tunwell.buildcapture.client;

import com.tunwell.buildcapture.BuildCapture;
import com.tunwell.buildcapture.network.CaptureBoundsPacket;
import com.tunwell.buildcapture.network.ModNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

import javax.annotation.Nullable;

/**
 * Interactive editing of the capture box: hold {@link CaptureKeys#FACE_EDIT}
 * (left alt by default) to highlight the face under the crosshair, then drag it
 * with the left mouse button. Holding shift while dragging moves the whole box
 * instead of one face; the scroll wheel nudges the face one block at a time.
 *
 * <p>The client only ever sends complete bounds. The server clamps them to the
 * build height and {@code capture.maxSize} and sends the result back, so the
 * client never owns the real selection.</p>
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = BuildCapture.MOD_ID, value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CaptureInteraction {
    private static final double EPS = 0.003D;
    private static final double MAX_DISTANCE = 256.0D;

    private static int hoverAxis = -1;
    private static int hoverSide;

    private static boolean dragging;
    private static int dragAxis;
    private static int dragSide;
    private static int dragNormal;
    @Nullable
    private static Vector3d dragOrigin;
    private static int dragFace;
    private static int lastDelta;
    private static boolean depthMode;
    private static float dragPitch;
    private static double pitchAccumulator;

    private CaptureInteraction() {
    }

    /** Axis of the face under the crosshair, or -1 when nothing is highlighted. */
    public static int hoveredAxis() {
        return hoverAxis;
    }

    /** +1 for the face at the maximum corner, -1 for the minimum one. */
    public static int hoveredSide() {
        return hoverSide;
    }

    /** Recomputes the highlighted face; called once per rendered frame. */
    public static void updateHover() {
        hoverAxis = -1;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null || minecraft.screen != null) {
            return;
        }
        if (dragging) {
            hoverAxis = dragAxis;
            return;
        }
        if (!CaptureKeys.FACE_EDIT.isDown()) {
            return;
        }
        Face face = pick();
        if (face != null) {
            hoverAxis = face.axis;
            hoverSide = face.side;
        }
    }

    @SubscribeEvent
    public static void onRawMouse(InputEvent.RawMouseEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null || minecraft.screen != null) {
            return;
        }
        if (event.getButton() != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return;
        }
        if (event.getAction() == GLFW.GLFW_PRESS) {
            if (dragging || !CaptureKeys.FACE_EDIT.isDown()
                    || !minecraft.mouseHandler.isMouseGrabbed()) {
                return;
            }
            Face face = pick();
            if (face == null) {
                return;
            }
            beginDrag(face);
            // Keep the vanilla attack key from breaking the block behind the box.
            event.setCanceled(true);
        } else if (event.getAction() == GLFW.GLFW_RELEASE && dragging) {
            // Apply a last step so a fast flick is not lost between ticks.
            updateDrag();
            endDrag();
        }
    }

    @SubscribeEvent
    public static void onScroll(InputEvent.MouseScrollEvent event) {
        if (dragging) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null || minecraft.screen != null
                || !CaptureKeys.FACE_EDIT.isDown()) {
            return;
        }
        double delta = event.getScrollDelta();
        Face face = pick();
        if (face == null || delta == 0.0D) {
            return;
        }
        ClientCaptureBox.Bounds bounds = ClientCaptureBox.bounds();
        if (bounds == null) {
            return;
        }
        int step = (int) Math.signum(delta) * (Screen.hasShiftDown() ? 5 : 1);
        // Scrolling up pushes the face outwards.
        int current = face.side > 0 ? bounds.max[face.axis] : bounds.min[face.axis];
        moveFace(face.axis, face.side, current + step * face.side);
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !dragging) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null
                || minecraft.screen != null || !minecraft.isWindowActive()
                || !minecraft.mouseHandler.isMouseGrabbed()
                || ClientCaptureBox.bounds() == null) {
            endDrag();
            return;
        }
        updateDrag();
    }

    private static void beginDrag(Face face) {
        ClientCaptureBox.Bounds bounds = ClientCaptureBox.bounds();
        Minecraft minecraft = Minecraft.getInstance();
        if (bounds == null || minecraft.player == null) {
            return;
        }
        dragging = true;
        dragAxis = face.axis;
        dragSide = Screen.hasShiftDown() ? 0 : face.side;
        dragNormal = face.side;
        dragOrigin = face.hit;
        dragFace = face.side > 0 ? bounds.max[face.axis] + 1 : bounds.min[face.axis];
        lastDelta = 0;
        depthMode = false;
        dragPitch = minecraft.player.xRot;
        pitchAccumulator = 0.0D;
        hoverAxis = face.axis;
        hoverSide = face.side;
    }

    private static void endDrag() {
        dragging = false;
        dragOrigin = null;
        lastDelta = 0;
        depthMode = false;
        hoverAxis = -1;
    }

    private static void updateDrag() {
        Minecraft minecraft = Minecraft.getInstance();
        if (dragOrigin == null || minecraft.player == null) {
            return;
        }
        Vector3d eye = minecraft.player.getEyePosition(1.0F);
        Vector3d view = minecraft.player.getViewVector(1.0F);

        // A face seen head-on cannot be moved by pointing at it: looking around
        // does not change the distance along its normal. Fall back to a vertical
        // "push/pull" gesture there, and to the crosshair projection otherwise.
        boolean headOn = Math.abs(component(view, dragAxis)) > 0.98D;
        if (headOn != depthMode) {
            reanchor(minecraft, eye, view, headOn);
        }
        if (headOn) {
            updateDepthDrag(minecraft.player.xRot);
            return;
        }

        double[] point = closestPointOnAxis(eye, view, dragOrigin, dragAxis);
        if (point == null) {
            return;
        }

        int boundary = (int) Math.round(point[dragAxis]);
        if (dragSide == 0) {
            int delta = boundary - dragFace;
            int step = delta - lastDelta;
            if (step == 0) {
                return;
            }
            lastDelta = delta;
            translate(dragAxis, step);
        } else {
            int coordinate = dragSide > 0 ? boundary - 1 : boundary;
            moveFace(dragAxis, dragSide, coordinate);
        }
    }

    /** Resets the gesture baseline when switching between drag mappings. */
    private static void reanchor(Minecraft minecraft, Vector3d eye, Vector3d view,
                                 boolean headOn) {
        depthMode = headOn;
        pitchAccumulator = 0.0D;
        if (minecraft.player != null) {
            dragPitch = minecraft.player.xRot;
        }
        ClientCaptureBox.Bounds bounds = ClientCaptureBox.bounds();
        if (headOn || bounds == null) {
            return;
        }
        double plane = dragNormal > 0 ? bounds.max[dragAxis] + 1.0D : bounds.min[dragAxis];
        double direction = component(view, dragAxis);
        if (Math.abs(direction) < 1.0E-5D) {
            return;
        }
        double distance = (plane - component(eye, dragAxis)) / direction;
        if (distance > 0.0D) {
            dragOrigin = eye.add(view.scale(distance));
        }
        dragFace = (int) Math.round(plane);
        lastDelta = 0;
    }

    /** Vertical push/pull used when the dragged face is square to the camera. */
    private static void updateDepthDrag(float pitch) {
        pitchAccumulator += (pitch - dragPitch) / 2.0D;
        dragPitch = pitch;
        int steps = (int) pitchAccumulator;
        if (steps == 0) {
            return;
        }
        pitchAccumulator -= steps;
        if (dragSide == 0) {
            translate(dragAxis, steps * dragNormal);
            return;
        }
        ClientCaptureBox.Bounds bounds = ClientCaptureBox.bounds();
        if (bounds == null) {
            return;
        }
        int current = dragNormal > 0 ? bounds.max[dragAxis] : bounds.min[dragAxis];
        moveFace(dragAxis, dragNormal, current + steps * dragNormal);
    }

    private static void moveFace(int axis, int side, int coordinate) {
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
        int limit = ClientCaptureBox.limit();
        if (limit > 0) {
            if (side > 0 && value - fixed + 1 > limit) {
                value = fixed + limit - 1;
            } else if (side < 0 && fixed - value + 1 > limit) {
                value = fixed - limit + 1;
            }
        }
        if (side > 0) {
            max[axis] = value;
        } else {
            min[axis] = value;
        }
        submit(min, max, axis, side);
    }

    private static void translate(int axis, int step) {
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
        submit(min, max, axis, 0);
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

    private static int buildTop() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.level == null ? 255 : minecraft.level.dimensionType().logicalHeight() - 1;
    }

    /** Ray test against the current box; returns the face the crosshair enters. */
    @Nullable
    private static Face pick() {
        ClientCaptureBox.Bounds bounds = ClientCaptureBox.bounds();
        Minecraft minecraft = Minecraft.getInstance();
        if (bounds == null || minecraft.player == null || minecraft.level == null) {
            return null;
        }
        Vector3d eye = minecraft.player.getEyePosition(1.0F);
        Vector3d view = minecraft.player.getViewVector(1.0F);

        double[] low = {
                bounds.min[0] - EPS,
                bounds.min[1] - EPS,
                bounds.min[2] - EPS
        };
        double[] high = {
                bounds.max[0] + 1.0D + EPS,
                bounds.max[1] + 1.0D + EPS,
                bounds.max[2] + 1.0D + EPS
        };

        double enter = 0.0D;
        double exit = MAX_DISTANCE;
        int axis = -1;
        boolean entersHigh = false;
        int exitAxis = -1;
        boolean exitsHigh = false;
        for (int index = 0; index < 3; index++) {
            double origin = component(eye, index);
            double direction = component(view, index);
            if (Math.abs(direction) < 1.0E-7D) {
                if (origin < low[index] || origin > high[index]) {
                    return null;
                }
                continue;
            }
            double near = (low[index] - origin) / direction;
            double far = (high[index] - origin) / direction;
            boolean highSide = false;
            if (near > far) {
                double swap = near;
                near = far;
                far = swap;
                highSide = true;
            }
            if (near > enter) {
                enter = near;
                axis = index;
                entersHigh = highSide;
            }
            if (far < exit) {
                exit = far;
                exitAxis = index;
                exitsHigh = !highSide;
            }
            if (enter > exit) {
                return null;
            }
        }
        if (axis < 0) {
            // The camera is inside the box, so no face is entered; the face the
            // crosshair leaves through is the one being looked at.
            if (exitAxis < 0 || exit > MAX_DISTANCE) {
                return null;
            }
            return new Face(exitAxis, exitsHigh ? 1 : -1, eye.add(view.scale(exit)));
        }
        if (enter > MAX_DISTANCE) {
            return null;
        }
        return new Face(axis, entersHigh ? 1 : -1, eye.add(view.scale(enter)));
    }

    /**
     * Closest point on the world axis through {@code origin} to the view ray.
     * Dragging a face slides along this line, which keeps the face under the
     * crosshair even when the camera is not square to the face.
     */
    @Nullable
    private static double[] closestPointOnAxis(Vector3d eye, Vector3d view,
                                               Vector3d origin, int axis) {
        double vx = axis == 0 ? 1.0D : 0.0D;
        double vy = axis == 1 ? 1.0D : 0.0D;
        double vz = axis == 2 ? 1.0D : 0.0D;
        double wx = eye.x - origin.x;
        double wy = eye.y - origin.y;
        double wz = eye.z - origin.z;

        double b = view.x * vx + view.y * vy + view.z * vz;
        double d = view.x * wx + view.y * wy + view.z * wz;
        double e = vx * wx + vy * wy + vz * wz;
        double denominator = 1.0D - b * b;
        if (denominator < 1.0E-4D) {
            // Looking straight down the axis: the projection is unstable.
            return null;
        }
        double rayParam = (b * e - d) / denominator;
        double axisParam = (e - b * d) / denominator;
        if (rayParam <= 0.0D) {
            return null;
        }
        return new double[]{
                origin.x + vx * axisParam,
                origin.y + vy * axisParam,
                origin.z + vz * axisParam
        };
    }

    private static double component(Vector3d vector, int axis) {
        switch (axis) {
            case 0:
                return vector.x;
            case 1:
                return vector.y;
            default:
                return vector.z;
        }
    }

    private static final class Face {
        private final int axis;
        private final int side;
        private final Vector3d hit;

        private Face(int axis, int side, Vector3d hit) {
            this.axis = axis;
            this.side = side;
            this.hit = hit;
        }
    }
}
