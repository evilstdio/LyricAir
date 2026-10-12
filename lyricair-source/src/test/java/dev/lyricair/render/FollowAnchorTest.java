package dev.lyricair.render;

import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FollowAnchorTest {
    @Test void movingTheCameraMovesTheFollowAnchorByTheSameAmount() {
        Vec3d movement = new Vec3d(20, 3, -8);
        Vec3d first = FollowAnchor.origin(new Vec3d(5, 70, 2), 36, -20, 5, 0.25);
        Vec3d second = FollowAnchor.origin(new Vec3d(5, 70, 2).add(movement), 36, -20, 5, 0.25);
        assertEquals(0, second.subtract(first).subtract(movement).length(), 0.000001);
    }
    @Test void turningAndLookingDownPreservesTheLocalLayout() {
        Vec3d camera = new Vec3d(0, 80, 0);
        for (double yaw : new double[]{0, 90, 180, -120}) for (double pitch : new double[]{0, 40, -60}) {
            Vec3d offset = FollowAnchor.origin(camera, yaw, pitch, 5, 0.25).subtract(camera);
            Vec3d forward = FollowAnchor.forward(yaw, pitch), right = FollowAnchor.right(yaw);
            assertEquals(5, offset.dotProduct(forward), 0.000001);
            assertEquals(0.25, offset.dotProduct(right), 0.000001);
            assertEquals(0.8, offset.dotProduct(forward.crossProduct(right)), 0.000001);
        }
    }
}
