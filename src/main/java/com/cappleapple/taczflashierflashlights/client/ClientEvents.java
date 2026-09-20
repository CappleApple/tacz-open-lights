package com.cappleapple.taczflashierflashlights.client;

import com.cappleapple.taczflashierflashlights.TaczFlashierFlashlights;
import com.cappleapple.taczflashierflashlights.network.FlashlightNetwork;
import com.mojang.blaze3d.platform.InputConstants;
import com.tacz.guns.api.item.IGun;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = TaczFlashierFlashlights.MOD_ID, value = Dist.CLIENT)
public final class ClientEvents {
    public static final KeyMapping TOGGLE = new KeyMapping("key.taczflashierflashlights.toggle",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, "key.categories.taczflashierflashlights");

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        TacticalBreachingCompatibility.initialize();
        while (TOGGLE.consumeClick()) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.screen != null || minecraft.player == null
                    || IGun.getIGunOrNull(minecraft.player.getMainHandItem()) == null) continue;
            boolean enabled = WeaponLightManager.toggleLocal();
            FlashlightNetwork.sendLocalState(enabled);
            minecraft.player.displayClientMessage(Component.translatable(enabled
                    ? "message.taczflashierflashlights.on" : "message.taczflashierflashlights.off"), true);
        }
    }

    @SubscribeEvent
    public static void renderTick(TickEvent.RenderTickEvent event) {
        if (event.phase == TickEvent.Phase.START) WeaponLightManager.beginFrame();
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { WeaponLightManager.clear(); }

    @SubscribeEvent
    public static void unload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) WeaponLightManager.clearWorld();
    }

    @Mod.EventBusSubscriber(modid = TaczFlashierFlashlights.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class ModEvents {
        @SubscribeEvent
        public static void registerKeys(RegisterKeyMappingsEvent event) { event.register(TOGGLE); }

        @SubscribeEvent
        public static void setup(FMLClientSetupEvent event) {
            event.enqueueWork(() -> {
                FlashlightNetwork.setClientReceiver(WeaponLightManager::acceptState);
                FlashlightNetwork.setClientConnectionSupplier(() -> {
                    var connection = Minecraft.getInstance().getConnection();
                    return connection == null ? null : connection.getConnection();
                });
            });
        }
    }

    private ClientEvents() {}
}
