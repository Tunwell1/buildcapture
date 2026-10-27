package com.tunwell.buildcapture.client;

import com.tunwell.buildcapture.BuildCapture;
import com.tunwell.buildcapture.network.CaptureBoundsPacket;
import com.tunwell.buildcapture.network.CaptureCommandPacket;
import com.tunwell.buildcapture.network.ModNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.util.Direction;
import net.minecraft.util.Util;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

import javax.annotation.Nullable;

/**
 * Interactive editing of the capture box. Hold {@link CaptureKeys#FACE_EDIT}
 * (left alt by default) and drag the box with the left mouse button: it slides
 * on the ground plane under the cursor, so it goes where the mouse goes instead
 * of where the camera points. The wheel resizes the face under the crosshair,
 * Ctrl+wheel moves the box up and down, shift+drag on a face resizes it by
 * hand, and the arrow keys nudge it one exact block.
 *
 * <p>Moving is a drag because that is how the box behaves like an object;
 * resizing needs a face, because a side is the only unambiguous way to say
 * which end should grow.</p>
 *
 * <p>The client only ever sends complete bounds. The server clamps them to the
 * build height and sends the result back, so the client never owns the real
 * selection.</p>
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = BuildCapture.MOD_ID, value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CaptureInteraction {
    private static final double EPS = 0.003D;
    private static final double MAX_DISTANCE = 256.0D;
    private static final long ACTION_MILLIS = 1400L;

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

    @Nullable
    private static Vector3d dragAnchor;
    @Nullable
    private static int[] dragStartMin;
    @Nullable
    private static int[] dragStartMax;
    private static double dragPlaneY;

    @Nullable
    private static String actionDirection;
    private static boolean actionResize;
    private static long actionAt;

    /**
     * Set when a screen closes, so the very key press that closed it (Enter to
     * send chat, the save dialog's own Enter, Esc) does not fire a key binding
     * from the world afterwards.
     */
    private static boolean swallowNextKey;

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

    /**
     * What the last box edit did, for the heads-up display, or {@code null} when
     * nothing happened recently.
     */
    @Nullable
    public static ITextComponent lastAction() {
        if (actionDirection == null || Util.getMillis() - actionAt > ACTION_MILLIS) {
            return null;
        }
        return new TranslationTextComponent(
                actionResize ? "buildcapture.hud.face" : "buildcapture.hud.moved",
                new TranslationTextComponent(actionDirection));
    }

    private static void note(int axis, int sign, boolean resize) {
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

    /** Recomputes the highlighted face; called once per rendered frame. */
    public static void updateHover() {
        hoverAxis = -1;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null || minecraft.screen != null
                || PlacementPreview.active()) {
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
    public static void onScreenChanged(GuiOpenEvent event) {
        if (event.getGui() == null) {
            swallowNextKey = true;
        }
    }

    @SubscribeEvent
    public static void onKey(InputEvent.KeyInputEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        // Always drain the queued clicks: chat lets key bindings count presses
        // (passEvents), so a stale click must not survive until the world is
        // back on screen.
        boolean menu = CaptureKeys.MENU.consumeClick();
        boolean corner1 = CaptureKeys.CORNER1.consumeClick();
        boolean corner2 = CaptureKeys.CORNER2.consumeClick();
        boolean save = CaptureKeys.SAVE.consumeClick();
        boolean swallowed = swallowNextKey;
        swallowNextKey = false;

        if (minecraft.player == null || minecraft.level == null
                || minecraft.screen != null || swallowed) {
            return;
        }
        // The ghost owns the save key and the preview movement bindings while it
        // is on screen; Enter places it instead of opening the save dialog.
        if (PlacementPreview.active()) {
            if (save) {
                PlacementPreview.confirm();
            }
            return;
        }
        if (menu) {
            CaptureScreen.open();
            return;
        }
        if (corner1) {
            requestCorner(CaptureCommandPacket.CORNER1);
            return;
        }
        if (corner2) {
            requestCorner(CaptureCommandPacket.CORNER2);
            return;
        }
        if (save) {
            requestSave();
            return;
        }
        if (!CaptureKeys.FACE_EDIT.isDown()) {
            return;
        }
        if (event.getAction() != GLFW.GLFW_PRESS && event.getAction() != GLFW.GLFW_REPEAT) {
            return;
        }
        int step = Screen.hasShiftDown() ? 5 : 1;
        switch (event.getKey()) {
            case GLFW.GLFW_KEY_UP:
                moveForward(step);
                break;
            case GLFW.GLFW_KEY_DOWN:
                moveForward(-step);
                break;
            case GLFW.GLFW_KEY_LEFT:
                moveSideways(-step);
                break;
            case GLFW.GLFW_KEY_RIGHT:
                moveSideways(step);
                break;
            case GLFW.GLFW_KEY_PAGE_UP:
                translate(1, step);
                break;
            case GLFW.GLFW_KEY_PAGE_DOWN:
                translate(1, -step);
                break;
            default:
                return;
        }
        event.setCanceled(true);
    }

    private static void requestCorner(int corner) {
        ModNetwork.CHANNEL.sendToServer(new CaptureCommandPacket(corner, ""));
    }

    private static void requestSave() {
        Minecraft minecraft = Minecraft.getInstance();
        if (ClientCaptureBox.bounds() == null) {
            if (minecraft.player != null) {
                minecraft.player.displayClientMessage(new TranslationTextComponent(
                        "buildcapture.capture.status_empty"), false);
            }
            return;
        }
        SaveSelectionScreen.open();
    }

    @SubscribeEvent
    public static void onRawMouse(InputEvent.RawMouseEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null || minecraft.screen != null
                || PlacementPreview.active()) {
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
                || PlacementPreview.active()
                || !CaptureKeys.FACE_EDIT.isDown()
                || ClientCaptureBox.bounds() == null) {
            return;
        }
        double delta = event.getScrollDelta();
        if (delta == 0.0D) {
            return;
        }
        int step = (int) Math.signum(delta) * (Screen.hasShiftDown() ? 5 : 1);
        // Vertical has no face of its own, so it works from any view direction.
        if (CaptureKeys.WHEEL_VERTICAL.isDown()) {
            translate(1, step);
            event.setCanceled(true);
            return;
        }
        Face face = pick();
        if (face == null) {
            return;
        }
        ClientCaptureBox.Bounds bounds = ClientCaptureBox.bounds();
        if (bounds == null) {
            return;
        }
        // The wheel resizes the face under the crosshair; dragging the box body
        // is what moves it now.
        int current = face.side > 0 ? bounds.max[face.axis] : bounds.min[face.axis];
        moveFace(face.axis, face.side, current + step * face.side);
        event.setCanceled(true);
    }

    /** Moves the whole box along the player's horizontal facing. */
    private static void moveForward(int blocks) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        Direction facing = minecraft.player.getDirection();
        if (facing.getStepX() != 0) {
            translate(0, facing.getStepX() * blocks);
        } else {
            translate(2, facing.getStepZ() * blocks);
        }
    }

    /** Moves the whole box to the player's right, relative to where they look. */
    private static void moveSideways(int blocks) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        Direction facing = minecraft.player.getDirection();
        if (facing.getStepX() != 0) {
            translate(2, facing.getStepX() * blocks);
        } else {
            translate(0, -facing.getStepZ() * blocks);
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            // A screen closed by a mouse click should not eat the next key press.
            swallowNextKey = false;
            return;
        }
        if (!dragging) {
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
        // Dragging the box body moves it; holding shift turns the drag into a
        // face resize, which is the precise version of the wheel.
        dragSide = Screen.hasShiftDown() ? face.side : 0;
        dragNormal = face.side;
        dragOrigin = face.hit;
        dragFace = face.side > 0 ? bounds.max[face.axis] + 1 : bounds.min[face.axis];
        lastDelta = 0;
        depthMode = false;
        dragPitch = minecraft.player.xRot;
        pitchAccumulator = 0.0D;
        hoverAxis = face.axis;
        hoverSide = face.side;
        if (dragSide == 0) {
            dragStartMin = bounds.min.clone();
            dragStartMax = bounds.max.clone();
            // Drag on the horizontal plane through the grabbed point, so the
            // box stays under the cursor whatever height on the box was hit.
            dragPlaneY = face.hit.y;
            dragAnchor = planePoint(dragPlaneY,
                    minecraft.player.getEyePosition(1.0F),
                    minecraft.player.getViewVector(1.0F));
        }
    }

    private static void endDrag() {
        dragging = false;
        dragOrigin = null;
        lastDelta = 0;
        depthMode = false;
        hoverAxis = -1;
        dragAnchor = null;
        dragStartMin = null;
        dragStartMax = null;
    }

    private static void updateDrag() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        if (dragSide == 0) {
            updateBoxDrag(minecraft);
            return;
        }
        if (dragOrigin == null) {
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
        int coordinate = dragSide > 0 ? boundary - 1 : boundary;
        moveFace(dragAxis, dragSide, coordinate);
    }

    /**
     * Slides the whole box on the plane the grab started on. The baseline is
     * the point the drag started at, so the box follows the mouse without
     * drifting; if the view swings too close to the horizon to project onto the
     * plane, the baseline is dropped and rebuilt on the next usable frame, so
     * the box never jumps.
     */
    private static void updateBoxDrag(Minecraft minecraft) {
        ClientCaptureBox.Bounds bounds = ClientCaptureBox.bounds();
        if (bounds == null || minecraft.player == null) {
            return;
        }
        Vector3d point = planePoint(dragPlaneY,
                minecraft.player.getEyePosition(1.0F),
                minecraft.player.getViewVector(1.0F));
        if (point == null) {
            dragAnchor = null;
            return;
        }
        if (dragAnchor == null || dragStartMin == null || dragStartMax == null) {
            dragAnchor = point;
            dragStartMin = bounds.min.clone();
            dragStartMax = bounds.max.clone();
            return;
        }
        int dx = (int) Math.round(point.x - dragAnchor.x);
        int dz = (int) Math.round(point.z - dragAnchor.z);
        if (dx == 0 && dz == 0) {
            return;
        }
        int[] min = dragStartMin.clone();
        int[] max = dragStartMax.clone();
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

    /**
     * Where the view ray meets a horizontal plane. Looking too close to the
     * horizon gives no usable point: the ray never meets the plane in front of
     * the player, or meets it so far away that the mapping explodes.
     */
    @Nullable
    private static Vector3d planePoint(double planeY, Vector3d eye, Vector3d view) {
        if (Math.abs(view.y) < 0.05D) {
            return null;
        }
        double distance = (planeY - eye.y) / view.y;
        if (distance <= 0.0D || distance > MAX_DISTANCE) {
            return null;
        }
        return eye.add(view.scale(distance));
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
        if (side > 0) {
            max[axis] = value;
        } else {
            min[axis] = value;
        }
        note(axis, side, true);
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
        note(axis, delta, false);
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
