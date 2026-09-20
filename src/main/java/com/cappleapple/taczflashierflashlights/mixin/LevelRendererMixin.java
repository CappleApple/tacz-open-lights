package com.cappleapple.taczflashierflashlights.mixin;

import com.cappleapple.taczflashierflashlights.client.RenderOwnerTracker;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
abstract class LevelRendererMixin {
    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void taczflashierflashlights$beginWorld(CallbackInfo ci) {
        RenderOwnerTracker.beginWorld();
    }

    @Inject(method = "renderLevel", at = @At("RETURN"))
    private void taczflashierflashlights$endWorld(CallbackInfo ci) {
        RenderOwnerTracker.endWorld();
    }
}
