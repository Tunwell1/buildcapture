package com.tunwell.buildcapture.client;

import net.minecraft.util.math.vector.Vector3d;

import javax.annotation.Nullable;

/**
 * Ray and plane math for the capture box, with no event or state handling.
 * Everything works on the client's current bounds and the eye ray.
 */
final class BoxGeometry {
    private static final double EPS = 0.003D;
    static final double MAX_DISTANCE = 256.0D;

    private BoxGeometry() {
    }

    /** Ray test against the current box; returns the face the crosshair enters. */
    @Nullable
    static Face pick(ClientCaptureBox.Bounds bounds, Vector3d eye, Vector3d view) {
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
     * Where the view ray meets a horizontal plane. Looking too close to the
     * horizon gives no usable point: the ray never meets the plane in front of
     * the player, or meets it so far away that the mapping explodes.
     */
    @Nullable
    static Vector3d planePoint(double planeY, Vector3d eye, Vector3d view) {
        if (Math.abs(view.y) < 0.05D) {
            return null;
        }
        double distance = (planeY - eye.y) / view.y;
        if (distance <= 0.0D || distance > MAX_DISTANCE) {
            return null;
        }
        return eye.add(view.scale(distance));
    }

    /**
     * Closest point on the world axis through {@code origin} to the view ray.
     * Dragging a face slides along this line, which keeps the face under the
     * crosshair even when the camera is not square to the face.
     */
    @Nullable
    static double[] closestPointOnAxis(Vector3d eye, Vector3d view,
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

    static double component(Vector3d vector, int axis) {
        switch (axis) {
            case 0:
                return vector.x;
            case 1:
                return vector.y;
            default:
                return vector.z;
        }
    }

    static final class Face {
        final int axis;
        final int side;
        final Vector3d hit;

        Face(int axis, int side, Vector3d hit) {
            this.axis = axis;
            this.side = side;
            this.hit = hit;
        }
    }
}
