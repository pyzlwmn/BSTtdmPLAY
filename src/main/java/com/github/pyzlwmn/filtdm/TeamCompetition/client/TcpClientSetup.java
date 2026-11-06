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
            // ★ v68：三种玩法共用同一套 HUD
            FPSMGameHudManager.INSTANCE.registerHud(TeamCompetitionMap.TYPE, new TcpHud());
            FPSMGameHudManager.INSTANCE.registerHud(TeamCompetitionMap.TYPE_PAIR, new TcpHud());
            FPSMGameHudManager.INSTANCE.registerHud(TeamCompetitionMap.TYPE_SOLO, new TcpHud());
            // ★ v68：TabManager 按地图的 gameType 精确查表 → pair / solo 各注册一份，否则按 Tab 出不来榜单
            // ★ v69：pair / solo 用独立的对战卡面板，不复用 tdm 表格版
            TabManager.getInstance().registerRenderer(new TcpTabRenderer(TeamCompetitionMap.TYPE));
            TabManager.getInstance().registerRenderer(new TcpVersusTabRenderer(TeamCompetitionMap.TYPE_PAIR));
            TabManager.getInstance().registerRenderer(new TcpVersusTabRenderer(TeamCompetitionMap.TYPE_SOLO));
        });
    }
}
