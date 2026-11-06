package com.github.pyzlwmn.filtdm.TeamCompetition;

import com.github.pyzlwmn.filtdm.TeamCompetition.net.TcpNetwork;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;

@Mod.EventBusSubscriber(modid = "filtdm", bus = Mod.EventBusSubscriber.Bus.MOD)
public final class TcpInit {

    private TcpInit() {
    }

    @SubscribeEvent
    public static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(TcpNetwork::register);
    }
}
