package com.cappleapple.taczflashierflashlights.client;

import com.cappleapple.openlights.api.client.ShaderCompatibility;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayDeque;
import java.util.Deque;

/** Tracks the owner of an actual world-rendered main-hand item, including players. */
public final class RenderOwnerTracker {
    private static final Deque<Scope> OWNERS = new ArrayDeque<>();
    private static int worldDepth;

    private RenderOwnerTracker() {}

    public static void beginWorld() {
        if (worldDepth++ == 0) {
            OWNERS.clear();
        }
    }

    public static void endWorld() {
        if (worldDepth > 0 && --worldDepth == 0) {
            OWNERS.clear();
        }
    }

    public static void beginItem(LivingEntity entity, ItemStack renderedStack,
                                 ItemDisplayContext context, boolean leftHand) {
        boolean heldContext = context == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND
                || context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
        boolean valid = worldDepth > 0 && !ShaderCompatibility.isRenderingShadowPass()
                && entity != null && entity.isAlive()
                && entity.level() == Minecraft.getInstance().level && heldContext
                && leftHand == (entity.getMainArm() == HumanoidArm.LEFT)
                && ItemStack.isSameItemSameTags(renderedStack, entity.getMainHandItem());
        // Keep a balanced inactive scope during shadow rendering so an enclosing
        // normal item render retains its owner when the nested scope ends.
        OWNERS.push(new Scope(valid ? entity : null));
    }

    public static void endItem() {
        if (!OWNERS.isEmpty()) {
            OWNERS.pop();
        }
    }

    public static LivingEntity current() {
        if (ShaderCompatibility.isRenderingShadowPass()) return null;
        Scope scope = OWNERS.peek();
        return scope == null ? null : scope.owner;
    }

    private record Scope(LivingEntity owner) {}
}
