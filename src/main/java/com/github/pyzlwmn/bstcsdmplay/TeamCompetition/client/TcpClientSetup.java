package com.github.pyzlwmn.bstcsdmplay.TeamCompetition.client;

import com.github.pyzlwmn.bstcsdmplay.TeamCompetition.TeamCompetitionMap;
import net.ptcrys.fpsmatch.common.client.FPSMGameHudManager;
import net.ptcrys.fpsmatch.common.client.tab.TabManager;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * 客户端初始化：
 *  1. 把 HUD 注册给 FPSMatch（按 gameType 分派，只在 tdm 且在局内时调用）
 *  2. 把 Tab 面板注册给 FPSMatch 的 TabManager（按 TAB 时替换原版 tab）
 *
 */
@Mod.EventBusSubscriber(modid = "bstcsdmplay", bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
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
