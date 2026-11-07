package com.cappleapple.taczflashierflashlights.mixin;

import com.cappleapple.taczflashierflashlights.client.TaczExternalRenderContext;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** TaCZ 1.2's external renderer does not traverse BedrockAttachmentModel. */
@Pseudo
@Mixin(targets = "com.tacz.guns.client.renderer.external.ExternalGunEffects", remap = false)
abstract class ExternalGunEffectsMixin {
    @Inject(method = "renderLasers(Lcom/tacz/guns/client/model/ExternalGunModel;Lnet/minecraft/world/item/ItemStack;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;)V",
            at = @At("HEAD"))
    private static void taczopenlights$captureExternal(@Coerce Object model, ItemStack attachment,
                                                      PoseStack pose, MultiBufferSource buffers,
                                                      CallbackInfo ci) {
        TaczExternalRenderContext.capture(model, attachment, pose);
    }
}
