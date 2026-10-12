package dev.lyricair.render;

import net.minecraft.world.phys.Vec3;

public final class FollowAnchor {
    private FollowAnchor() {}
    public static Vec3 forward(double yawDegrees) {
        return forward(yawDegrees, 0);
    }
    public static Vec3 forward(double yawDegrees, double pitchDegrees) {
        double yaw = Math.toRadians(yawDegrees);
        double pitch = Math.toRadians(pitchDegrees);
        return new Vec3(-Math.sin(yaw) * Math.cos(pitch), -Math.sin(pitch), Math.cos(yaw) * Math.cos(pitch));
    }
    public static Vec3 right(double yawDegrees) {
        double yaw = Math.toRadians(yawDegrees);
        return new Vec3(Math.cos(yaw), 0, Math.sin(yaw));
    }
    public static Vec3 origin(Vec3 camera, double yawDegrees, double distance, double side) {
        return origin(camera, yawDegrees, 0, distance, side);
    }
    public static Vec3 origin(Vec3 camera, double yawDegrees, double pitchDegrees, double distance, double side) {
        Vec3 forward = forward(yawDegrees, pitchDegrees), right = right(yawDegrees);
        Vec3 up = forward.cross(right);
        return camera.add(forward.scale(distance)).add(right.scale(side)).add(up.scale(0.8));
    }
}
