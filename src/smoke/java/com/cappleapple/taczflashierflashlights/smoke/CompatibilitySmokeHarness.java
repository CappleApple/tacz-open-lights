package com.cappleapple.taczflashierflashlights.smoke;

import com.cappleapple.taczflashierflashlights.client.ClientEvents;
import com.cappleapple.taczflashierflashlights.client.TacticalBreachingCompatibility;
import com.cappleapple.taczflashierflashlights.config.ClientConfig;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.logging.LogUtils;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ConfigTracker;
import net.minecraftforge.fml.config.ModConfig;
import org.lwjgl.glfw.GLFW;

import java.util.Arrays;

/** Runs in the disposable development client; deliberately edits only that client's settings. */
@Mod.EventBusSubscriber(modid = "taczopenlights", value = Dist.CLIENT)
public final class CompatibilitySmokeHarness {
    private static boolean finished;

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void tick(TickEvent.ClientTickEvent event) {
        String mode = System.getProperty("taczopenlights.compatSmoke", "");
        Minecraft mc = Minecraft.getInstance();
        if (mode.isEmpty() || finished || event.phase != TickEvent.Phase.END
                || !(mc.screen instanceof TitleScreen)) return;
        finished = true;
        TacticalBreachingCompatibility.initialize();
        if (mode.equals("absent")) {
            require(!net.minecraftforge.fml.ModList.get().isLoaded(TacticalBreachingCompatibility.MOD_ID), "optional addon absent");
            require(ClientEvents.TOGGLE.getKey().getValue() == GLFW.GLFW_KEY_K, "Open Lights K without optional addon");
            LogUtils.getLogger().info("COMPAT_SMOKE_SUCCESS mode=absent optional addon is not required");
            mc.stop();
            return;
        }
        KeyMapping key = Arrays.stream(mc.options.keyMappings)
                .filter(mapping -> TacticalBreachingCompatibility.TOGGLE_KEY.equals(mapping.getName()))
                .findFirst().orElseThrow();
        ModConfig config = ConfigTracker.INSTANCE.configSets().get(ModConfig.Type.COMMON).stream()
                .filter(candidate -> TacticalBreachingCompatibility.MOD_ID.equals(candidate.getModId()))
                .findFirst().orElseThrow();
        ForgeConfigSpec spec = (ForgeConfigSpec) config.getSpec();
        ForgeConfigSpec.BooleanValue enabled = spec.getValues().get(TacticalBreachingCompatibility.ENABLE_PATH);
        require(ClientConfig.TACTICAL_BREACHING_DEFAULTS_APPLIED.get(), "migration marker");
        require(key.getDefaultKey().equals(InputConstants.UNKNOWN), "unbound reset default");
        require(ClientEvents.TOGGLE.getKey().getValue() == GLFW.GLFW_KEY_K, "Open Lights K binding");
        KeyMapping other = Arrays.stream(mc.options.keyMappings)
                .filter(mapping -> "key.tacz_tactical_breaching.clear_malfunction".equals(mapping.getName()))
                .findFirst().orElseThrow();
        require(other.getKey().getValue() == GLFW.GLFW_KEY_J, "unrelated Tactical Breaching binding");
        if (mode.equals("migrate")) {
            require(!enabled.get() && key.isUnbound(), "disabled emission and unbound saved key");
            // A second launch must preserve a deliberate user change.
            enabled.set(true);
            key.setKey(InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_L));
        } else if (mode.equals("preserve")) {
            require(enabled.get() && key.getKey().getValue() == GLFW.GLFW_KEY_L, "saved user overrides");
            enabled.set(false);
            key.setKey(key.getDefaultKey());
        } else throw new IllegalArgumentException("Unknown compatibility smoke mode " + mode);
        config.save();
        KeyMapping.resetMapping();
        mc.options.save();
        LogUtils.getLogger().info("COMPAT_SMOKE_SUCCESS mode={} checked migration, reset default, unrelated bindings, and saved choices", mode);
        mc.stop();
    }

    private static void require(boolean value, String description) {
        if (!value) throw new IllegalStateException("COMPAT_SMOKE_FAILURE " + description);
    }
}
