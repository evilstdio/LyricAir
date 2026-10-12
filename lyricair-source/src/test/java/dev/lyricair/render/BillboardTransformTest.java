package dev.lyricair.render;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BillboardTransformTest {
    @Test void glyphTrianglesFaceTheCameraAtDifferentViewAngles() {
        for (float yaw : new float[] {0, 0.8f, 3.14f, -1.2f}) {
            Quaternionf rotation = new Quaternionf().rotationYXZ(yaw, 0.25f, 0);
            Vector3f origin = rotation.transform(new Vector3f(0, 1, -5));
            Matrix4f matrix = BillboardTransform.matrix(origin.x, origin.y, origin.z, rotation, 0.03f, 0.027f);
            // Minecraft glyph winding: bottom-left, bottom-right, top-right.
            Vector3f a = matrix.transformPosition(new Vector3f(-8, 8, 0));
            Vector3f b = matrix.transformPosition(new Vector3f(8, 8, 0));
            Vector3f c = matrix.transformPosition(new Vector3f(8, 0, 0));
            Vector3f normal = new Vector3f(b).sub(a).cross(new Vector3f(c).sub(a));
            assertTrue(normal.dot(new Vector3f(origin).negate()) > 0,
                    "Glyph winding must face the viewer; flipping both X and Y culls the text");
        }
    }

    @Test void mapsScreenRightToPositiveXAndScreenDownToNegativeY() {
        Matrix4f matrix = BillboardTransform.matrix(2, 3, -5, new Quaternionf(), 0, 0.03f);
        Vector3f start = matrix.transformPosition(new Vector3f());
        Vector3f right = matrix.transformPosition(new Vector3f(10, 0, 0));
        Vector3f down = matrix.transformPosition(new Vector3f(0, 10, 0));
        assertEquals(2, start.x, 0.00001);
        assertEquals(0.3, right.x - start.x, 0.00001);
        assertEquals(-0.3, down.y - start.y, 0.00001);
    }
}
