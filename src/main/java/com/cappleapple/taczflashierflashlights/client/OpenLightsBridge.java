package com.cappleapple.taczflashierflashlights.client;

import com.cappleapple.openlights.api.client.CollectLightsEvent;
import com.cappleapple.openlights.api.client.BeamLights;
import com.cappleapple.openlights.beam.BeamProfiles;
import com.cappleapple.openlights.api.client.LightKey;
import com.cappleapple.taczflashierflashlights.TaczFlashierFlashlights;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid=TaczFlashierFlashlights.MOD_ID,value=Dist.CLIENT)
public final class OpenLightsBridge {
    private static final ResourceLocation OWNER = new ResourceLocation("taczopenlights","weapon");
    @SubscribeEvent public static void collect(CollectLightsEvent event) {
        for(var beam:WeaponLightManager.activeLights()) {
            event.add(new LightKey(OWNER,beam.id()),BeamLights.spot(beam.worldOrigin(),beam.worldDirection(),new Vec3(0,1,0),BeamProfiles.clientProfile(beam.attachmentId())));
        }
    }
    private OpenLightsBridge() {}
}
