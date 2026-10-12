package dev.lyricair.render;

import org.joml.Matrix4f;
import org.joml.Quaternionfc;

/** Matches Minecraft 1.21.1's name-tag transform: positive X, negative Y, positive Z. */
public final class BillboardTransform {
    private BillboardTransform() { }

    public static Matrix4f matrix(float x, float y, float z, Quaternionfc cameraRotation, float roll, float scale) {
        return new Matrix4f().translation(x, y, z).rotate(cameraRotation).rotateZ(roll).scale(scale, -scale, scale);
    }
}
