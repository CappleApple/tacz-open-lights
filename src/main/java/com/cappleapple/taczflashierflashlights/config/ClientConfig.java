package com.cappleapple.taczflashierflashlights.config;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.ForgeConfigSpec;
import java.util.List;

public final class ClientConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue TACTICAL_BREACHING_DEFAULTS_APPLIED;
    private static final ForgeConfigSpec.BooleanValue ENABLED;
    private static final ForgeConfigSpec.BooleanValue AUTOMATIC;
    public static final ForgeConfigSpec.DoubleValue REMOTE_DISTANCE;
    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> EMITTERS;
    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> DISABLED;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        ENABLED = builder.comment("Render TaCZ attachment flashlights.").define("enabled", true);
        AUTOMATIC = builder.comment("Recognize flashlight, flash, weapon_light, light and emitter bones on LASER attachments.",
                "Laser-only bones are not automatically treated as flashlights.").define("automaticDetection", true);
        REMOTE_DISTANCE = builder.comment("Maximum camera distance for other players' weapon flashlights.")
                .defineInRange("remoteDistance", 64.0, 1.0, 256.0);
        EMITTERS = builder.comment("Attachment ID followed by = and its model emitter bone.",
                "Overrides automatic detection; bone names are case sensitive.")
                .defineList("emitters", List.of("tacz:laser_peq15=flashlight",
                        "tacz:laser_peq6=flash_illuminated",
                        "tacz:laser_nightstick=laser_illuminated"), ClientConfig::validEmitter);
        DISABLED = builder.comment("Attachment IDs that must never emit a flashlight beam.")
                .defineListAllowEmpty("disabledAttachments", List.of("tacz:laser_compact"), value ->
                        value instanceof String text && ResourceLocation.tryParse(text) != null);
        TACTICAL_BREACHING_DEFAULTS_APPLIED = builder.comment(
                "Tracks the one-time Tactical Breaching flashlight defaults migration.",
                "Once applied, later flashlight config and keybind choices are preserved.",
                "Set false to apply the disabled/unbound defaults again on the next client launch.")
                .define("tacticalBreachingDefaultsApplied", false);
        SPEC = builder.build();
    }

    private static boolean validEmitter(Object value) {
        if (!(value instanceof String text)) return false;
        int separator = text.indexOf('=');
        return separator > 0 && separator < text.length() - 1
                && ResourceLocation.tryParse(text.substring(0, separator)) != null;
    }

    public static boolean enabled() { return ENABLED.get(); }
    public static boolean automaticDetection() { return AUTOMATIC.get(); }
    public static boolean disabled(ResourceLocation id) { return DISABLED.get().contains(id.toString()); }
    public static String emitterBone(ResourceLocation id) {
        String prefix = id + "=";
        for (String entry : EMITTERS.get()) {
            if (entry.startsWith(prefix)) return entry.substring(prefix.length());
        }
        return null;
    }

    private ClientConfig() {}
}
