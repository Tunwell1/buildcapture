package com.tunwell.buildcapture.client;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.vector.Vector3d;

import javax.annotation.Nullable;

/**
 * State of one mouse drag on the box. Dragging the body slides it on the
 * horizontal plane the grab started on; shift+dragging a face resizes it, and a
 * face square to the camera falls back to a vertical push/pull gesture.
 */
final class BoxDrag {
    private boolean dragging;
    private int dragAxis;
    private int dragSide;
    private int dragNormal;
    @Nullable
    private Vector3d dragOrigin;
    private boolean depthMode;
    private float dragPitch;
    private double pitchAccumulator;

    @Nullable
    private Vector3d dragAnchor;
    @Nullable
    private int[] dragStartMin;
    @Nullable
    private int[] dragStartMax;
    private double dragPlaneY;

    boolean active() {
        return dragging;
    }

    int axis() {
        return dragAxis;
    }

    int side() {
        return dragNormal;
    }

    void begin(BoxGeometry.Face face, boolean resize, ClientCaptureBox.Bounds bounds,
               PlayerEntity player) {
        dragging = true;
        dragAxis = face.axis;
        // Dragging the box body moves it; holding shift turns the drag into a
        // face resize, which is the precise version of the wheel.
        dragSide = resize ? face.side : 0;
        dragNormal = face.side;
        dragOrigin = face.hit;
        depthMode = false;
        dragPitch = player.xRot;
        pitchAccumulator = 0.0D;
        if (dragSide == 0) {
            dragStartMin = bounds.min.clone();
            dragStartMax = bounds.max.clone();
            // Drag on the horizontal plane through the grabbed point, so the
            // box stays under the cursor whatever height on the box was hit.
            dragPlaneY = face.hit.y;
            dragAnchor = BoxGeometry.planePoint(dragPlaneY,
                    player.getEyePosition(1.0F), player.getViewVector(1.0F));
        }
    }

    void end() {
        dragging = false;
        dragOrigin = null;
        depthMode = false;
        dragAnchor = null;
        dragStartMin = null;
        dragStartMax = null;
    }

    void update(PlayerEntity player) {
        if (dragSide == 0) {
            updateBox(player);
            return;
        }
        if (dragOrigin == null) {
            return;
        }
        Vector3d eye = player.getEyePosition(1.0F);
        Vector3d view = player.getViewVector(1.0F);

        // A face seen head-on cannot be moved by pointing at it: looking around
        // does not change the distance along its normal. Fall back to a vertical
        // "push/pull" gesture there, and to the crosshair projection otherwise.
        boolean headOn = Math.abs(BoxGeometry.component(view, dragAxis)) > 0.98D;
        if (headOn != depthMode) {
            reanchor(ClientCaptureBox.bounds(), eye, view, headOn, player.xRot);
        }
        if (headOn) {
            updateDepth(player.xRot);
            return;
        }

        double[] point = BoxGeometry.closestPointOnAxis(eye, view, dragOrigin, dragAxis);
        if (point == null) {
            return;
        }

        int boundary = (int) Math.round(point[dragAxis]);
        int coordinate = dragSide > 0 ? boundary - 1 : boundary;
        BoxEdit.moveFace(dragAxis, dragSide, coordinate);
    }

    /** Resets the gesture baseline when switching between drag mappings. */
    private void reanchor(@Nullable ClientCaptureBox.Bounds bounds, Vector3d eye,
                          Vector3d view, boolean headOn, float pitch) {
        depthMode = headOn;
        pitchAccumulator = 0.0D;
        dragPitch = pitch;
        if (headOn || bounds == null) {
            return;
        }
        double plane = dragNormal > 0 ? bounds.max[dragAxis] + 1.0D : bounds.min[dragAxis];
        double direction = BoxGeometry.component(view, dragAxis);
        if (Math.abs(direction) < 1.0E-5D) {
            return;
        }
        double distance = (plane - BoxGeometry.component(eye, dragAxis)) / direction;
        if (distance > 0.0D) {
            dragOrigin = eye.add(view.scale(distance));
        }
    }

    /** Vertical push/pull used when the dragged face is square to the camera. */
    private void updateDepth(float pitch) {
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
        BoxEdit.moveFace(dragAxis, dragNormal, current + steps * dragNormal);
    }

    /**
     * Slides the whole box on the plane the grab started on. The baseline is
     * the point the drag started at, so the box follows the mouse without
     * drifting; if the view swings too close to the horizon to project onto the
     * plane, the baseline is dropped and rebuilt on the next usable frame, so
     * the box never jumps.
     */
    private void updateBox(PlayerEntity player) {
        ClientCaptureBox.Bounds bounds = ClientCaptureBox.bounds();
        if (bounds == null) {
            return;
        }
        Vector3d point = BoxGeometry.planePoint(dragPlaneY,
                player.getEyePosition(1.0F), player.getViewVector(1.0F));
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
        BoxEdit.moveBox(dragStartMin, dragStartMax, dx, dz);
    }
}
