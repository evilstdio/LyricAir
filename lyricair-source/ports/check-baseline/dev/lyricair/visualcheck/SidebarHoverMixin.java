package dev.lyricair.visualcheck.mixin;

import dev.lyricair.ui.LyricAirScreen;
import dev.lyricair.visualcheck.VisualCheckClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Test-only hover coordinates, supplied during the engine's real GUI rendering pass. */
@Mixin(LyricAirScreen.class)
public abstract class SidebarHoverMixin {
    @ModifyVariable(method = "render", at = @At("HEAD"), argsOnly = true, ordinal = 0, remap = false)
    private int lyricair$mouseX(int value) { return VisualCheckClient.hoverX < 0 ? value : VisualCheckClient.hoverX; }
    @ModifyVariable(method = "render", at = @At("HEAD"), argsOnly = true, ordinal = 1, remap = false)
    private int lyricair$mouseY(int value) { return VisualCheckClient.hoverY < 0 ? value : VisualCheckClient.hoverY; }
}
