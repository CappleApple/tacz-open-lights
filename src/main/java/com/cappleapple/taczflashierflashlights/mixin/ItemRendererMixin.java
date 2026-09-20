package com.cappleapple.taczflashierflashlights.mixin;

import com.cappleapple.taczflashierflashlights.client.RenderOwnerTracker;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemRenderer.class)
abstract class ItemRendererMixin {
    @Inject(method = "renderStatic(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/world/level/Level;III)V",
            at = @At("HEAD"))
    private void taczflashierflashlights$begin(LivingEntity owner, ItemStack stack, ItemDisplayContext context,
                                               boolean leftHand, PoseStack poseStack, MultiBufferSource buffers,
                                               Level level, int light, int overlay, int seed, CallbackInfo ci) {
        RenderOwnerTracker.beginItem(owner, stack, context, leftHand);
    }

    @Inject(method = "renderStatic(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/world/level/Level;III)V",
            at = @At("RETURN"))
    private void taczflashierflashlights$end(LivingEntity owner, ItemStack stack, ItemDisplayContext context,
                                             boolean leftHand, PoseStack poseStack, MultiBufferSource buffers,
                                             Level level, int light, int overlay, int seed, CallbackInfo ci) {
        RenderOwnerTracker.endItem();
    }
}
