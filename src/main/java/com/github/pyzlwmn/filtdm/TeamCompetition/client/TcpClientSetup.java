package com.github.pyzlwmn.filtdm.TeamCompetition.client;

import com.github.pyzlwmn.filtdm.TeamCompetition.TeamCompetitionMap;
import net.ptcrys.fpsmatch.common.client.FPSMGameHudManager;
import net.ptcrys.fpsmatch.common.client.tab.TabManager;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = "filtdm", bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class TcpClientSetup {

    private TcpClientSetup() {
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            FPSMGameHudManager.INSTANCE.registerHud(TeamCompetitionMap.TYPE, new TcpHud());
            TabManager.getInstance().registerRenderer(new TcpTabRenderer());
        });
    }
}
