package dev.lyricair.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Display;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Display.TextDisplay.class)
public interface TextDisplayAccess {
    @Invoker("setText") void lyricair$text(Component value);
    @Invoker("setLineWidth") void lyricair$lineWidth(int value);
    @Invoker("setBackgroundColor") void lyricair$background(int value);
    @Invoker("setTextOpacity") void lyricair$opacity(byte value);
    @Invoker("setFlags") void lyricair$flags(byte value);
}
