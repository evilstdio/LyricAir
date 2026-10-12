package dev.lyricair.mixin;

import com.mojang.math.Transformation;
import net.minecraft.util.Brightness;
import net.minecraft.world.entity.Display;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Display.class)
public interface DisplayAccess {
    @Invoker("setTransformation") void lyricair$transform(Transformation value);
    @Invoker("setBillboardConstraints") void lyricair$billboard(Display.BillboardConstraints value);
    @Invoker("setBrightnessOverride") void lyricair$brightness(Brightness value);
    @Invoker("setTransformationInterpolationDuration") void lyricair$interpolation(int ticks);
}
