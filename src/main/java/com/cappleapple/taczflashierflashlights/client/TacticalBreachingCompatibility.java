package com.cappleapple.taczflashierflashlights.client;

import com.cappleapple.taczflashierflashlights.config.ClientConfig;
import com.cappleapple.taczflashierflashlights.mixin.KeyMappingAccessor;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.logging.LogUtils;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.config.ConfigTracker;
import net.minecraftforge.fml.config.ModConfig;

/** Applies optional-addon defaults once, after Minecraft has loaded its saved controls. */
public final class TacticalBreachingCompatibility {
    public static final String MOD_ID = "tacz_tactical_breaching";
    public static final String TOGGLE_KEY = "key.tacz_tactical_breaching.toggle_nightstick_flashlight";
    public static final String ENABLE_PATH = "nightstick_flashlight.enableNightstickFlashlight";
    private static boolean initialized;

    private TacticalBreachingCompatibility() {}

    public static void initialize() {
        if (initialized) return;
        initialized = true;
        if (!ModList.get().isLoaded(MOD_ID)) return;

        Minecraft minecraft = Minecraft.getInstance();
        KeyMapping toggle = null;
        for (KeyMapping key : minecraft.options.keyMappings) {
            if (TOGGLE_KEY.equals(key.getName())) { toggle = key; break; }
        }
        if (toggle == null) {
            LogUtils.getLogger().warn("Tactical Breaching flashlight key was not found; leaving its settings unchanged");
            return;
        }
        // Reset in Controls should agree with the default supplied by this addon.
        ((KeyMappingAccessor) toggle).taczopenlights$setDefaultKey(InputConstants.UNKNOWN);
        if (ClientConfig.TACTICAL_BREACHING_DEFAULTS_APPLIED.get()) return;

        for (ModConfig config : ConfigTracker.INSTANCE.configSets().get(ModConfig.Type.COMMON)) {
            if (!MOD_ID.equals(config.getModId()) || !(config.getSpec() instanceof ForgeConfigSpec spec)
                    || !spec.isLoaded()) continue;
            Object value = spec.getValues().get(ENABLE_PATH);
            if (!(value instanceof ForgeConfigSpec.BooleanValue enabled)) continue;
            enabled.set(false);
            config.save();
            toggle.setKeyModifierAndCode(net.minecraftforge.client.settings.KeyModifier.NONE, InputConstants.UNKNOWN);
            toggle.setDown(false);
            while (toggle.consumeClick()) { /* Discard input queued before initialization. */ }
            KeyMapping.resetMapping();
            minecraft.options.save();
            ClientConfig.TACTICAL_BREACHING_DEFAULTS_APPLIED.set(true);
            ClientConfig.SPEC.save();
            LogUtils.getLogger().info("Disabled Tactical Breaching's Nightstick flashlight and unbound its toggle; later user changes will be preserved");
            return;
        }
        LogUtils.getLogger().warn("Tactical Breaching flashlight config was not found; leaving its settings unchanged");
    }
}
