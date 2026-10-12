package dev.lyricair.render;

import java.util.List;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.phys.Vec3;

public final class FloatingLyric {
    public final List<FormattedCharSequence> rows;
    public final Vec3 origin;
    public final Vec3 right;
    public final Vec3 forward;
    public final double born;
    public final double seed;
    public final boolean orbit;
    public final int color;
    public final double side;
    public double expires;

    public FloatingLyric(List<FormattedCharSequence> rows, Vec3 origin, Vec3 right, Vec3 forward,
                         double born, double lifetime, double seed, boolean orbit, int color, double side) {
        this.rows = rows;
        this.origin = origin;
        this.right = right;
        this.forward = forward;
        this.born = born;
        this.expires = born + lifetime;
        this.seed = seed;
        this.orbit = orbit;
        this.color = color;
        this.side = side;
    }

    public Vec3 position(double time) {
        return position(time, origin, right, forward);
    }

    public Vec3 position(double time, Vec3 anchor, Vec3 rightAxis, Vec3 forwardAxis) {
        double age = Math.max(0, time - born);
        double entrance = 1 - Math.exp(-age * 8);
        double drift = orbit ? Math.sin(age * 0.65 + seed) * 0.9 : Math.sin(age * 1.15 + seed) * 0.12;
        return anchor.add(rightAxis.scale(drift)).add(forwardAxis.scale(orbit ? Math.cos(age * 0.65 + seed) * 0.25 : 0))
                .add(0, -0.35 * (1 - entrance) + age * 0.10 + Math.sin(age * 1.7 + seed) * 0.07, 0);
    }

    public float scale(double time) {
        double age = Math.max(0, time - born);
        // Damped spring: a brief overshoot makes each line pop into the world.
        return (float) Math.max(0.03, 1 - Math.exp(-age * 7.5) * Math.cos(age * 12));
    }

    public float opacity(double time) {
        return (float) Math.clamp(Math.min((time - born) / 0.18, (expires - time) / 0.9), 0, 1);
    }
}

