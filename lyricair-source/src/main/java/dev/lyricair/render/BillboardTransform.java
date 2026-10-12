package dev.lyricair.render;

import org.joml.Matrix4f;
import org.joml.Quaternionfc;

public final class BillboardTransform {
    private BillboardTransform() { }

    public static Matrix4f matrix(float x, float y, float z, Quaternionfc cameraRotation, float roll, float scale) {
        return new Matrix4f().translation(x, y, z).rotate(cameraRotation).rotateZ(roll).scale(scale, -scale, scale);
    }
}
