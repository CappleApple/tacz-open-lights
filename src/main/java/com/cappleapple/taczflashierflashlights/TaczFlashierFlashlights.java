package com.cappleapple.taczflashierflashlights;

import com.cappleapple.taczflashierflashlights.config.ClientConfig;
import com.cappleapple.taczflashierflashlights.network.FlashlightNetwork;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;

@Mod(TaczFlashierFlashlights.MOD_ID)
public final class TaczFlashierFlashlights {
    public static final String MOD_ID = "taczopenlights";

    public TaczFlashierFlashlights() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);
        FlashlightNetwork.register();
    }
}
