package dev.lyricair.render;

import net.minecraft.util.math.Vec3d;

public final class FollowAnchor {
    private FollowAnchor() {}
    public static Vec3d forward(double yawDegrees) {
        return forward(yawDegrees, 0);
    }
    public static Vec3d forward(double yawDegrees, double pitchDegrees) {
        double yaw = Math.toRadians(yawDegrees);
        double pitch = Math.toRadians(pitchDegrees);
        return new Vec3d(-Math.sin(yaw) * Math.cos(pitch), -Math.sin(pitch), Math.cos(yaw) * Math.cos(pitch));
    }
    public static Vec3d right(double yawDegrees) {
        double yaw = Math.toRadians(yawDegrees);
        return new Vec3d(Math.cos(yaw), 0, Math.sin(yaw));
    }
    public static Vec3d origin(Vec3d camera, double yawDegrees, double distance, double side) {
        return origin(camera, yawDegrees, 0, distance, side);
    }
    public static Vec3d origin(Vec3d camera, double yawDegrees, double pitchDegrees, double distance, double side) {
        Vec3d forward = forward(yawDegrees, pitchDegrees), right = right(yawDegrees);
        Vec3d up = forward.crossProduct(right);
        return camera.add(forward.multiply(distance)).add(right.multiply(side)).add(up.multiply(0.8));
    }
}
