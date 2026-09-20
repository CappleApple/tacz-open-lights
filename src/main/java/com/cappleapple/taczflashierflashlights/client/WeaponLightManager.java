package com.cappleapple.taczflashierflashlights.client;

import com.cappleapple.taczflashierflashlights.config.ClientConfig;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.mojang.blaze3d.systems.RenderSystem;
import org.joml.Quaternionf;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Render-thread state only. No lights or GL objects are allocated during model traversal. */
public final class WeaponLightManager {
    private static final Map<UUID, Snapshot> POSES = new HashMap<>();
    private static final Map<UUID, Boolean> ENABLED = new HashMap<>();
    private static long frame;

    public static void beginFrame() {
        frame++;
        POSES.values().removeIf(pose -> frame - pose.frame() > 1);
    }

    public static void capture(LivingEntity owner, ResourceLocation attachmentId, boolean firstPerson,
                               Matrix4f renderedTransform, String boneName) {
        if (!ClientConfig.enabled()
                || !owner.isAlive() || !isEnabled(owner.getUUID())) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || owner.level() != minecraft.level) return;
        ItemStack held = owner.getMainHandItem();
        if (IGun.getIGunOrNull(held) == null) return;
        POSES.put(owner.getUUID(), new Snapshot(owner, held, attachmentId, firstPerson,
                new Matrix4f(renderedTransform), frame));
    }

    public static Collection<Beam> activeLights() {
        Minecraft minecraft = Minecraft.getInstance();
        if (!ClientConfig.enabled() || minecraft.level == null || minecraft.player == null) return java.util.List.of();
        var camera = minecraft.gameRenderer.getMainCamera();
        Vec3 cameraPosition = camera.getPosition();
        var lights = new ArrayList<Beam>();
        for (var entry : POSES.entrySet()) {
            Snapshot pose = entry.getValue();
            LivingEntity owner = pose.owner();
            boolean local = owner == minecraft.player;
            if (!isEnabled(entry.getKey()) || !owner.isAlive() || owner.isRemoved()
                    || owner.level() != minecraft.level || owner.getMainHandItem() != pose.gun()
                    || !hasAttachment(pose.gun(), pose.attachmentId())
                    || frame - pose.frame() > (pose.firstPerson() ? 1 : 0)
                    || (local && pose.firstPerson() != minecraft.options.getCameraType().isFirstPerson())
                    || (pose.firstPerson() && (minecraft.options.hideGui || camera.getEntity() != owner))) continue;
            if (!local && owner.distanceToSqr(cameraPosition) > Math.pow(ClientConfig.REMOTE_DISTANCE.get(), 2)) continue;
            // First-person hands draw after the backend's world-light pass. Reuse only the previous
            // frame's animation pose, with the current camera, to avoid a one-frame aiming delay.
            var transform = EmitterTransforms.toWorld(pose.matrix(), pose.firstPerson(), new Quaternionf().setFromNormalized(RenderSystem.getInverseViewRotationMatrix()),
                    cameraPosition.x, cameraPosition.y, cameraPosition.z);
            if (transform == null) continue;
            lights.add(new Beam(entry.getKey(), new Vec3(transform.x(), transform.y(), transform.z()),
                    new Vec3(transform.direction().x, transform.direction().y, transform.direction().z), pose.attachmentId()));
        }
        var capturedOwners = new java.util.HashSet<UUID>();
        lights.forEach(beam -> capturedOwners.add(beam.id()));
        for (var player : minecraft.level.players()) {
            if (capturedOwners.contains(player.getUUID()) || !player.isAlive() || player.isSpectator()
                    || !isEnabled(player.getUUID()) || player.distanceToSqr(cameraPosition)
                    > Math.pow(ClientConfig.REMOTE_DISTANCE.get(), 2)) continue;
            if (player == minecraft.player && camera.getEntity() != player) continue;
            // Models may be culled or hidden by F1. Keep the physical lamp on with an aim-based pose.
            Beam fallback = FallbackWeaponPose.create(player, minecraft.getFrameTime());
            if (fallback != null) lights.add(fallback);
        }
        return lights;
    }

    public static boolean toggleLocal() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return true;
        UUID id = minecraft.player.getUUID();
        boolean enabled = !isEnabled(id);
        acceptState(id, enabled);
        return enabled;
    }

    public static void acceptState(UUID id, boolean enabled) {
        ENABLED.put(id, enabled);
        if (!enabled) POSES.remove(id);
    }

    public static boolean isEnabled(UUID id) { return ENABLED.getOrDefault(id, true); }

    private static boolean hasAttachment(ItemStack stack, ResourceLocation expected) {
        IGun gun = IGun.getIGunOrNull(stack);
        if (gun == null) return false;
        for (AttachmentType type : AttachmentType.values()) {
            if (type == AttachmentType.NONE) continue;
            ItemStack attached = gun.getAttachment(stack, type);
            if (attached.isEmpty()) attached = gun.getBuiltinAttachment(stack, type);
            IAttachment attachment = IAttachment.getIAttachmentOrNull(attached);
            if (attachment != null && expected.equals(attachment.getAttachmentId(attached))) return true;
        }
        return false;
    }

    public static void clearWorld() {
        POSES.clear();
        frame = 0;
    }

    public static void clear() {
        POSES.clear();
        ENABLED.clear();
        frame = 0;
    }

    public record Beam(UUID id, Vec3 worldOrigin, Vec3 worldDirection, ResourceLocation attachmentId) {}
    private record Snapshot(LivingEntity owner, ItemStack gun, ResourceLocation attachmentId, boolean firstPerson, Matrix4f matrix, long frame) {}
    private WeaponLightManager() {}
}
