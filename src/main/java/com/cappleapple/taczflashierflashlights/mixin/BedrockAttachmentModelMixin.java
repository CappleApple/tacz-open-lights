package com.cappleapple.taczflashierflashlights.mixin;

import com.cappleapple.taczflashierflashlights.client.TaczRenderContext;
import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.client.model.BedrockAttachmentModel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = BedrockAttachmentModel.class, remap = false)
abstract class BedrockAttachmentModelMixin {
    @Inject(method = "render", at = @At("HEAD"))
    private void taczflashierflashlights$begin(ItemStack attachment, ItemStack gun, PoseStack poseStack,
                                               ItemDisplayContext context, RenderType renderType,
                                               int light, int overlay, CallbackInfo ci) {
        TaczRenderContext.begin((BedrockAttachmentModel) (Object) this, attachment, gun, poseStack, context);
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void taczflashierflashlights$end(ItemStack attachment, ItemStack gun, PoseStack poseStack,
                                             ItemDisplayContext context, RenderType renderType,
                                             int light, int overlay, CallbackInfo ci) {
        TaczRenderContext.end();
    }
}
