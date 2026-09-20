package com.cappleapple.taczflashierflashlights.client;

import com.cappleapple.taczflashierflashlights.config.ClientConfig;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.regex.Pattern;

/** Approximate held-light pose when the attachment model is outside the rendered view. */
public final class FallbackWeaponPose {
    private static final Pattern AUTOMATIC_EMITTER = Pattern.compile(
            "^(?:flashlight|flash|weapon_light|light|emitter)(?:_illuminated)?(?:_\\d+)?$",
            Pattern.CASE_INSENSITIVE);

    private FallbackWeaponPose() {
    }

    public static boolean recognizes(ItemStack gun) {
        IGun gunItem = IGun.getIGunOrNull(gun);
        if (gunItem == null || !ClientConfig.enabled()) {
            return false;
        }
        ItemStack attachmentStack = gunItem.getAttachment(gun, AttachmentType.LASER);
        if (attachmentStack.isEmpty()) {
            attachmentStack = gunItem.getBuiltinAttachment(gun, AttachmentType.LASER);
        }
        IAttachment attachment = IAttachment.getIAttachmentOrNull(attachmentStack);
        if (attachment == null || attachment.getType(attachmentStack) != AttachmentType.LASER) {
            return false;
        }
        ResourceLocation id = attachment.getAttachmentId(attachmentStack);
        if (ClientConfig.disabled(id)) {
            return false;
        }
        String explicitBone = ClientConfig.emitterBone(id);
        if (explicitBone == null && !ClientConfig.automaticDetection()) {
            return false;
        }
        var index = TimelessAPI.getClientAttachmentIndex(id).orElse(null);
        if (index == null) {
            return false;
        }
        var model = index.getAttachmentModel();
        if (model == null) {
            return false;
        }
        if (explicitBone != null) {
            return model.getNode(explicitBone) != null;
        }
        for (String bone : model.getIndexBones().keySet()) {
            if (bone != null && AUTOMATIC_EMITTER.matcher(bone).matches()) {
                return true;
            }
        }
        return false;
    }

    /** Returns null if the current main-hand gun has no recognized flashlight attachment. */
    @Nullable
    public static WeaponLightManager.Beam create(LivingEntity owner, float partialTick) {
        if (!recognizes(owner.getMainHandItem())) {
            return null;
        }
        Vec3 direction = owner.getViewVector(partialTick).normalize();
        double yaw = Math.toRadians(owner.getViewYRot(partialTick));
        double side = owner.getMainArm() == HumanoidArm.RIGHT ? 0.25 : -0.25;
        // A yaw-based right vector remains stable when looking straight up or down.
        Vec3 right = new Vec3(-Math.cos(yaw), 0.0, -Math.sin(yaw));
        Vec3 origin = owner.getEyePosition(partialTick).add(right.scale(side))
                .add(0.0, -0.25, 0.0).add(direction.scale(0.35));
                ItemStack held=owner.getMainHandItem();
        IGun gun=IGun.getIGunOrNull(held);
        ItemStack attached=gun.getAttachment(held,AttachmentType.LASER);
        if(attached.isEmpty())attached=gun.getBuiltinAttachment(held,AttachmentType.LASER);
        ResourceLocation attachmentId=IAttachment.getIAttachmentOrNull(attached).getAttachmentId(attached);
        return new WeaponLightManager.Beam(owner.getUUID(), origin, direction, attachmentId);
    }
}
