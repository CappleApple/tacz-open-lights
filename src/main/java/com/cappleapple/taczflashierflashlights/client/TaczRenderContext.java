package com.cappleapple.taczflashierflashlights.client;

import com.cappleapple.openlights.api.client.ShaderCompatibility;
import com.cappleapple.taczflashierflashlights.config.ClientConfig;
import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.client.model.BedrockAttachmentModel;
import com.tacz.guns.client.model.bedrock.BedrockPart;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.regex.Pattern;

/** Limits emitter capture to the selected attachment during its real model traversal. */
public final class TaczRenderContext {
    private static final Pattern AUTOMATIC_EMITTER = Pattern.compile(
            "^(?:flashlight|flash|weapon_light|light|emitter)(?:_illuminated)?(?:_\\d+)?$",
            Pattern.CASE_INSENSITIVE);
    private static final Deque<Context> CONTEXTS = new ArrayDeque<>();

    private TaczRenderContext() {}

    public static void begin(BedrockAttachmentModel model, ItemStack attachmentStack, ItemStack gunStack,
                             PoseStack poseStack, ItemDisplayContext displayContext) {
        Context context = create(model, attachmentStack, gunStack, poseStack, displayContext);
        CONTEXTS.push(context);
    }

    private static Context create(BedrockAttachmentModel model, ItemStack attachmentStack, ItemStack gunStack,
                                   PoseStack poseStack, ItemDisplayContext displayContext) {
        if (ShaderCompatibility.isRenderingShadowPass() || !ClientConfig.enabled()
                || attachmentStack == null || gunStack == null
                || IGun.getIGunOrNull(gunStack) == null) {
            return Context.inactive();
        }
        boolean firstPerson = displayContext == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
                || displayContext == ItemDisplayContext.FIRST_PERSON_LEFT_HAND;
        boolean thirdPerson = displayContext == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND
                || displayContext == ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
        if (!firstPerson && !thirdPerson) {
            return Context.inactive();
        }
        Minecraft minecraft = Minecraft.getInstance();
        LivingEntity owner = firstPerson ? minecraft.player : RenderOwnerTracker.current();
        if (owner == null || !owner.isAlive() || owner.isSpectator()
                || owner.level() != minecraft.level
                || !ItemStack.isSameItemSameTags(owner.getMainHandItem(), gunStack)
                || firstPerson && (!minecraft.options.getCameraType().isFirstPerson()
                || minecraft.getCameraEntity() != owner)) {
            return Context.inactive();
        }
        IAttachment attachment = IAttachment.getIAttachmentOrNull(attachmentStack);
        if (attachment == null) {
            return Context.inactive();
        }
        ResourceLocation attachmentId = attachment.getAttachmentId(attachmentStack);
        if (ClientConfig.disabled(attachmentId)) {
            return Context.inactive();
        }
        String explicitBone = ClientConfig.emitterBone(attachmentId);
        boolean automatic = explicitBone == null && ClientConfig.automaticDetection()
                && attachment.getType(attachmentStack) == AttachmentType.LASER;
        if (explicitBone == null && !automatic) {
            return Context.inactive();
        }
        return new Context(owner, attachmentId, firstPerson, explicitBone, automatic,
                model, new Matrix4f(poseStack.last().pose()));
    }

    public static void capturePart(String boneName, Matrix4f matrix) {
        Context context = CONTEXTS.peek();
        if (context != null && context.owner != null && !context.submitted && context.accepts(boneName)
                && !ShaderCompatibility.isRenderingShadowPass()) {
            context.submit(matrix, boneName);
        }
    }

    public static void end() {
        Context context = CONTEXTS.peek();
        if (context != null) {
            if (context.owner != null && !context.submitted && !ShaderCompatibility.isRenderingShadowPass()) {
                BedrockAttachmentModel model = context.model;
                BedrockPart marker = findEmitter(context, model);
                boolean usingDetailModel = false;
                if (marker == null) {
                    // TaCZ's default third-person LOD meshes omit lamp bones.
                    // Their attachment root still uses the same gun-slot matrix.
                    BedrockAttachmentModel detail = TimelessAPI.getClientAttachmentIndex(context.attachmentId)
                            .map(index -> index.getAttachmentModel()).orElse(null);
                    if (detail != null && detail != model) {
                        marker = findEmitter(context, detail);
                        usingDetailModel = true;
                    }
                }
                // Empty marker bones never enter BedrockPart.render. A missing
                // LOD marker uses the full model's native hierarchy instead.
                if (marker != null && (usingDetailModel || marker.cubes.isEmpty() && marker.children.isEmpty())) {
                    Deque<BedrockPart> path = new ArrayDeque<>();
                    boolean visible = true;
                    for (BedrockPart part = marker; part != null; part = part.getParent()) {
                        path.push(part);
                        visible &= part.visible;
                    }
                    if (visible) {
                        PoseStack stack = new PoseStack();
                        stack.last().pose().set(context.rootMatrix);
                        while (!path.isEmpty()) {
                            path.pop().translateAndRotateAndScale(stack);
                        }
                    }
                }
            }
            CONTEXTS.pop();
        }
    }

    private static BedrockPart findEmitter(Context context, BedrockAttachmentModel model) {
        if (context.explicitBone != null) {
            return model.getNode(context.explicitBone);
        }
        return model.getIndexBones().keySet().stream().filter(context::accepts).sorted()
                .map(model::getNode).filter(java.util.Objects::nonNull).findFirst().orElse(null);
    }
    private static final class Context {
        final LivingEntity owner;
        final ResourceLocation attachmentId;
        final boolean firstPerson;
        final String explicitBone;
        final boolean automatic;
        final BedrockAttachmentModel model;
        final Matrix4f rootMatrix;
        boolean submitted;

        Context(LivingEntity owner, ResourceLocation attachmentId, boolean firstPerson,
                String explicitBone, boolean automatic, BedrockAttachmentModel model, Matrix4f rootMatrix) {
            this.owner = owner;
            this.attachmentId = attachmentId;
            this.firstPerson = firstPerson;
            this.explicitBone = explicitBone;
            this.automatic = automatic;
            this.model = model;
            this.rootMatrix = rootMatrix;
        }

        void submit(Matrix4f matrix, String boneName) {
            submitted = true;
            Matrix4f emitterMatrix = AttachmentEmitterTransforms.atLens(attachmentId.toString(), boneName, matrix);
            WeaponLightManager.capture(owner, attachmentId, firstPerson, emitterMatrix, boneName);
        }
        boolean accepts(String name) {
            return name != null && (explicitBone != null ? explicitBone.equals(name)
                    : automatic && AUTOMATIC_EMITTER.matcher(name).matches());
        }

        static Context inactive() {
            return new Context(null, null, false, null, false, null, null);
        }
    }
}
