package com.cappleapple.taczflashierflashlights.mixin;

import com.cappleapple.taczflashierflashlights.client.TaczRenderContext;
import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.client.model.BedrockAttachmentModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Group;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = BedrockAttachmentModel.class, remap = false)
abstract class BedrockAttachmentModelMixin {
    @Group(name = "taczopenlights$begin", min = 1)
    @Inject(method = "render(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/item/ItemDisplayContext;Lnet/minecraft/client/renderer/RenderType;II)V",
            at = @At("HEAD"), require = 0)
    private void taczflashierflashlights$begin(ItemStack attachment, ItemStack gun, PoseStack poseStack,
                                               ItemDisplayContext context, RenderType renderType,
                                               int light, int overlay, CallbackInfo ci) {
        TaczRenderContext.begin((BedrockAttachmentModel) (Object) this, attachment, gun, poseStack, context);
    }

    @Group(name = "taczopenlights$end", min = 1)
    @Inject(method = "render(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/item/ItemDisplayContext;Lnet/minecraft/client/renderer/RenderType;II)V",
            at = @At("RETURN"), require = 0)
    private void taczflashierflashlights$end(ItemStack attachment, ItemStack gun, PoseStack poseStack,
                                             ItemDisplayContext context, RenderType renderType,
                                             int light, int overlay, CallbackInfo ci) {
        TaczRenderContext.end();
    }

    // TaCZ 1.2 passes the hand/world buffer explicitly and calls this overload directly.
    @Group(name = "taczopenlights$begin", min = 1)
    @Inject(method = "render(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/item/ItemDisplayContext;Lnet/minecraft/client/renderer/RenderType;IIFLnet/minecraft/client/renderer/MultiBufferSource;)V",
            at = @At("HEAD"), require = 0)
    private void taczopenlights$beginBuffered(ItemStack attachment, ItemStack gun, PoseStack poseStack,
                                            ItemDisplayContext context, RenderType renderType,
                                            int light, int overlay, float alpha, MultiBufferSource bufferSource,
                                            CallbackInfo ci) {
        TaczRenderContext.begin((BedrockAttachmentModel) (Object) this, attachment, gun, poseStack, context);
    }

    @Group(name = "taczopenlights$end", min = 1)
    @Inject(method = "render(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/item/ItemDisplayContext;Lnet/minecraft/client/renderer/RenderType;IIFLnet/minecraft/client/renderer/MultiBufferSource;)V",
            at = @At("RETURN"), require = 0)
    private void taczopenlights$endBuffered(ItemStack attachment, ItemStack gun, PoseStack poseStack,
                                          ItemDisplayContext context, RenderType renderType,
                                          int light, int overlay, float alpha, MultiBufferSource bufferSource,
                                          CallbackInfo ci) {
        TaczRenderContext.end();
    }
}
