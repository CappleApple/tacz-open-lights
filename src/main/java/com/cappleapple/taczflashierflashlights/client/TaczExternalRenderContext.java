package com.cappleapple.taczflashierflashlights.client;

import com.cappleapple.openlights.api.client.ShaderCompatibility;
import com.cappleapple.taczflashierflashlights.config.ClientConfig;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.logging.LogUtils;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.model.BedrockAttachmentModel;
import com.tacz.guns.client.model.bedrock.BedrockPart;
import com.tacz.guns.client.resource.index.ClientAttachmentIndex;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Method;

/** Resolves TaCZ 1.2's baked external poses without linking its classes on TaCZ 1.1. */
public final class TaczExternalRenderContext {
    private static Bridge bridge;
    private static boolean unavailable;

    private TaczExternalRenderContext() {}

    public static void capture(Object externalModel, ItemStack attachmentStack, PoseStack renderedPose) {
        if (!ClientConfig.enabled() || ShaderCompatibility.isRenderingShadowPass() || unavailable) return;
        var owner = RenderOwnerTracker.current();
        var minecraft = Minecraft.getInstance();
        if (owner == null || !owner.isAlive() || owner.isSpectator() || owner.level() != minecraft.level
                || IGun.getIGunOrNull(owner.getMainHandItem()) == null) return;
        IAttachment attachment = IAttachment.getIAttachmentOrNull(attachmentStack);
        if (attachment == null) return;
        ResourceLocation attachmentId = attachment.getAttachmentId(attachmentStack);
        if (ClientConfig.disabled(attachmentId)) return;
        ClientAttachmentIndex index = TimelessAPI.getClientAttachmentIndex(attachmentId).orElse(null);
        if (index == null || index.getAttachmentModel() == null) return;
        BedrockAttachmentModel detail = index.getAttachmentModel();
        String boneName = TaczRenderContext.emitterBone(attachmentId, attachment.getType(attachmentStack), detail);
        if (boneName == null) return;
        BedrockPart marker = detail.getNode(boneName);
        for (BedrockPart part = marker; part != null; part = part.getParent()) {
            if (!part.visible) return;
        }
        try {
            if (bridge == null) bridge = new Bridge();
            Object instance = bridge.displayInstance.invoke(externalModel);
            int bone = (int) bridge.getIndex.invoke(instance, boneName);
            if (bone < 0) {
                // Distant attachment meshes may omit lamps; retain the rendered slot and use
                // the full model's native baked hierarchy, matching the legacy LOD fallback.
                Object fullModel = bridge.getExternalModel.invoke(index);
                if (fullModel == null) return;
                instance = bridge.displayInstance.invoke(fullModel);
                bone = (int) bridge.getIndex.invoke(instance, boneName);
            }
            if (bone < 0) return;
            PoseStack emitterPose = new PoseStack();
            emitterPose.last().pose().set(renderedPose.last().pose());
            bridge.applyBonePivot.invoke(null, emitterPose, instance, bone);
            // The native external renderer uses SBM axes; lamp offsets use Bedrock axes.
            emitterPose.scale(-1, -1, 1);
            WeaponLightManager.capture(owner, attachmentId, false,
                    AttachmentEmitterTransforms.atLens(attachmentId.toString(), boneName,
                            emitterPose.last().pose()), boneName);
        } catch (ReflectiveOperationException | LinkageError exception) {
            unavailable = true;
            LogUtils.getLogger().warn("Unable to capture TaCZ external attachment poses; using aim-based flashlight poses", exception);
        }
    }

    private static final class Bridge {
        final Method displayInstance;
        final Method getIndex;
        final Method applyBonePivot;
        final Method getExternalModel;

        Bridge() throws ReflectiveOperationException {
            Class<?> external = Class.forName("com.tacz.guns.client.model.ExternalGunModel");
            Class<?> instance = Class.forName("com.tacz.guns.client.model.DisplayGunModelInstance");
            Class<?> transforms = Class.forName("com.tacz.guns.client.renderer.item.SbmGunTransforms");
            displayInstance = external.getMethod("displayInstance");
            getIndex = instance.getMethod("getIndex", String.class);
            applyBonePivot = transforms.getMethod("applyBonePivot", PoseStack.class, instance, int.class);
            getExternalModel = ClientAttachmentIndex.class.getMethod("getExternalModel");
        }
    }
}
