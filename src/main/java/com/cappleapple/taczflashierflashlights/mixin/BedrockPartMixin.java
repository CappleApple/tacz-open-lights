package com.cappleapple.taczflashierflashlights.mixin;

import com.cappleapple.taczflashierflashlights.client.TaczRenderContext;
import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.client.model.bedrock.BedrockPart;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = BedrockPart.class, remap = false)
abstract class BedrockPartMixin {
    @Shadow @Final public String name;

    @Inject(method = "translateAndRotateAndScale", at = @At("TAIL"))
    private void taczflashierflashlights$capture(PoseStack poseStack, CallbackInfo ci) {
        TaczRenderContext.capturePart(name, poseStack.last().pose());
    }
}
